package com.streamchat.twitch;

import com.streamchat.AbstractChatSource;
import com.streamchat.ChatSink;
import com.streamchat.StreamChatMessage;
import com.streamchat.StreamEventType;
import com.streamchat.StreamPlatform;
import java.awt.Color;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Reads Twitch chat over the IRC WebSocket gateway.
 *
 * <p>Read-only access needs no OAuth at all: Twitch accepts an anonymous {@code justinfan} nick and
 * still delivers IRCv3 tags, so display names and name colours come through. That is why this
 * plugin asks for no Twitch credentials -- a token would add nothing to a read-only feed, and not
 * having one means no token to store, refresh or leak.
 */
@Slf4j
public class TwitchChatSource extends AbstractChatSource
{
	private static final String GATEWAY = "wss://irc-ws.chat.twitch.tv:443";

	/** Twitch pings roughly every five minutes; treat a longer silence as a dead socket. */
	private static final long INACTIVITY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(7);
	private static final long WATCHDOG_PERIOD_MS = TimeUnit.MINUTES.toMillis(1);

	/** CTCP delimiter (0x01). "/me" messages arrive as ACTION text wrapped in it. */
	private static final char CTCP = (char) 1;
	private static final String ACTION_PREFIX = CTCP + "ACTION ";

	private final OkHttpClient httpClient;
	private final List<String> channels;

	private volatile WebSocket webSocket;
	private volatile ScheduledFuture<?> watchdog;
	private volatile long lastActivityMs;

	public TwitchChatSource(ScheduledExecutorService executor, ChatSink sink, OkHttpClient httpClient,
		List<String> channels)
	{
		super(executor, sink);
		// A WebSocket must not inherit the shared client's read timeout, or the connection is torn
		// down during any quiet period. newBuilder() keeps the connection pool and RuneLite's
		// thread-guard interceptor.
		this.httpClient = httpClient.newBuilder()
			.readTimeout(0, TimeUnit.MILLISECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
		this.channels = channels;
	}

	@Override
	public StreamPlatform platform()
	{
		return StreamPlatform.TWITCH;
	}

	@Override
	protected void doConnect()
	{
		if (channels.isEmpty())
		{
			onFatal("no channels configured");
			return;
		}

		final Request request = new Request.Builder()
			.url(GATEWAY)
			.build();

		lastActivityMs = System.currentTimeMillis();
		webSocket = httpClient.newWebSocket(request, new Listener());

		watchdog = executor.scheduleWithFixedDelay(this::checkAlive,
			WATCHDOG_PERIOD_MS, WATCHDOG_PERIOD_MS, TimeUnit.MILLISECONDS);
	}

	@Override
	protected void doStop()
	{
		final ScheduledFuture<?> w = watchdog;
		if (w != null)
		{
			w.cancel(false);
			watchdog = null;
		}

		final WebSocket ws = webSocket;
		if (ws != null)
		{
			webSocket = null;
			// 1000 = normal closure. cancel() too, so a half-open socket cannot linger.
			ws.close(1000, "plugin stopped");
			ws.cancel();
		}
	}

	private void checkAlive()
	{
		if (!isRunning())
		{
			return;
		}

		if (System.currentTimeMillis() - lastActivityMs > INACTIVITY_TIMEOUT_MS)
		{
			log.debug("[Twitch] no traffic for {}ms, reconnecting", INACTIVITY_TIMEOUT_MS);
			onDisconnected("connection went quiet");
		}
	}

	private void send(WebSocket ws, String line)
	{
		ws.send(line + "\r\n");
	}

	private void handleLine(WebSocket ws, String line)
	{
		final IrcMessage msg = IrcMessage.parse(line);
		if (msg == null)
		{
			return;
		}

		switch (msg.getCommand())
		{
			case "PING":
				// Must echo the token back or Twitch drops the connection.
				send(ws, "PONG :" + (msg.trailing() == null ? "tmi.twitch.tv" : msg.trailing()));
				break;

			case "001":
				// Registered. Join here, so a reconnect re-joins with no extra bookkeeping.
				for (String channel : channels)
				{
					send(ws, "JOIN #" + channel.toLowerCase(Locale.ROOT));
				}
				onConnected();
				break;

			case "PRIVMSG":
				handlePrivmsg(msg);
				break;

			case "USERNOTICE":
				handleUserNotice(msg);
				break;

			case "NOTICE":
				handleNotice(msg);
				break;

			case "RECONNECT":
				// Twitch asks clients to reconnect before it restarts a gateway node.
				onDisconnected("server requested reconnect");
				break;

			default:
				break;
		}
	}

	private void handlePrivmsg(IrcMessage msg)
	{
		final String channel = stripHash(msg.param(0));
		String body = msg.trailing();
		if (body == null || body.isEmpty())
		{
			return;
		}

		if (body.startsWith(ACTION_PREFIX))
		{
			final int bodyEnd = body.charAt(body.length() - 1) == CTCP ? body.length() - 1 : body.length();
			body = "* " + body.substring(Math.min(ACTION_PREFIX.length(), bodyEnd), bodyEnd);
		}

		String author = msg.tag("display-name");
		if (author == null)
		{
			author = msg.nick();
		}
		if (author == null)
		{
			return;
		}

		final String id = msg.tag("id");
		// A cheer is an ordinary message that also carries bits.
		final StreamEventType eventType = msg.tag("bits") != null ? StreamEventType.CHEER : null;

		sink.onMessage(StreamChatMessage.builder()
			.platform(StreamPlatform.TWITCH)
			.channel(channel)
			.author(author)
			.message(body)
			.authorColor(parseColor(msg.tag("color")))
			.id(id != null ? id : syntheticId(author, body))
			.eventType(eventType)
			.emoteOnly(isEmoteOnly(msg.trailing(), msg.tag("emotes")))
			.build());
	}

	private void handleUserNotice(IrcMessage msg)
	{
		// system-msg carries the pre-rendered "X subscribed for n months" text.
		final String system = msg.tag("system-msg");
		if (system == null)
		{
			return;
		}

		final String id = msg.tag("id");

		sink.onMessage(StreamChatMessage.builder()
			.platform(StreamPlatform.TWITCH)
			.channel(stripHash(msg.param(0)))
			.author("")
			.message(system)
			.authorColor(null)
			.id(id != null ? id : syntheticId("", system))
			.eventType(classifyUserNotice(msg.tag("msg-id")))
			.build());
	}

	/**
	 * Works out whether a message is nothing but emotes, from the IRCv3 {@code emotes} tag.
	 *
	 * <p>The tag gives character ranges, e.g. {@code 25:0-4,12-16/1902:6-10}. If every non-space
	 * character falls inside one of those ranges, the user typed no words of their own.
	 *
	 * <p>Only native Twitch emotes appear in this tag. BTTV/FFZ/7TV emotes are plain words as far
	 * as the protocol is concerned, and identifying them would mean downloading those services'
	 * emote lists -- which is exactly what the Plugin Hub rejects emote plugins for. So third-party
	 * emotes are treated as ordinary text, which errs towards showing a message rather than hiding
	 * one.
	 */
	static boolean isEmoteOnly(@Nullable String message, @Nullable String emotesTag)
	{
		if (message == null || message.isEmpty() || emotesTag == null || emotesTag.isEmpty())
		{
			return false;
		}

		final boolean[] covered = new boolean[message.length()];

		for (String emote : emotesTag.split("/"))
		{
			final int colon = emote.indexOf(':');
			if (colon == -1)
			{
				continue;
			}

			for (String range : emote.substring(colon + 1).split(","))
			{
				final int dash = range.indexOf('-');
				if (dash == -1)
				{
					continue;
				}

				try
				{
					final int start = Integer.parseInt(range.substring(0, dash).trim());
					final int end = Integer.parseInt(range.substring(dash + 1).trim());
					for (int i = Math.max(0, start); i <= Math.min(covered.length - 1, end); i++)
					{
						covered[i] = true;
					}
				}
				catch (NumberFormatException ex)
				{
					return false;
				}
			}
		}

		boolean sawEmote = false;
		for (int i = 0; i < message.length(); i++)
		{
			if (covered[i])
			{
				sawEmote = true;
			}
			else if (!Character.isWhitespace(message.charAt(i)))
			{
				return false;
			}
		}

		return sawEmote;
	}

	/** Maps Twitch's USERNOTICE msg-id to an event kind. */
	static StreamEventType classifyUserNotice(@Nullable String msgId)
	{
		if (msgId == null)
		{
			return StreamEventType.OTHER;
		}

		switch (msgId)
		{
			case "sub":
			case "resub":
			case "extendsub":
			case "primepaidupgrade":
			case "giftpaidupgrade":
			case "anongiftpaidupgrade":
				return StreamEventType.SUBSCRIPTION;

			case "subgift":
			case "anonsubgift":
			case "submysterygift":
			case "standardpayforward":
			case "communitypayforward":
				return StreamEventType.GIFT;

			case "raid":
			case "unraid":
				return StreamEventType.RAID;

			case "bitsbadgetier":
				return StreamEventType.CHEER;

			default:
				return StreamEventType.OTHER;
		}
	}

	private void handleNotice(IrcMessage msg)
	{
		final String msgId = msg.tag("msg-id");
		if (msgId == null)
		{
			return;
		}

		// These mean the channel will never deliver messages, so retrying is pointless.
		switch (msgId)
		{
			case "msg_channel_suspended":
			case "msg_banned":
			case "no_permission":
			case "tos_ban":
				onFatal(msg.trailing() == null ? msgId : msg.trailing());
				break;
			default:
				log.debug("[Twitch] notice {}: {}", msgId, msg.trailing());
				break;
		}
	}

	private static String stripHash(@Nullable String channel)
	{
		if (channel == null)
		{
			return "";
		}
		return channel.startsWith("#") ? channel.substring(1) : channel;
	}

	/**
	 * Twitch name colours are {@code #RRGGBB}. Returns null for the empty tag Twitch sends for
	 * users who never picked one.
	 */
	@Nullable
	static Color parseColor(@Nullable String raw)
	{
		if (raw == null || raw.length() != 7 || raw.charAt(0) != '#')
		{
			return null;
		}

		try
		{
			return new Color(Integer.parseInt(raw.substring(1), 16));
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
	}

	private static String syntheticId(String author, String body)
	{
		return author + ' ' + body;
	}

	private class Listener extends WebSocketListener
	{
		@Override
		public void onOpen(WebSocket ws, Response response)
		{
			lastActivityMs = System.currentTimeMillis();

			// tags gives us display names and colours; commands gives us RECONNECT and NOTICE.
			send(ws, "CAP REQ :twitch.tv/tags twitch.tv/commands");
			// Anonymous read-only login. Any justinfan<digits> nick is accepted with no password.
			send(ws, "NICK justinfan" + (ThreadLocalRandom.current().nextInt(90_000) + 10_000));
		}

		@Override
		public void onMessage(WebSocket ws, String text)
		{
			lastActivityMs = System.currentTimeMillis();

			// A single frame can carry several CRLF-separated lines.
			int start = 0;
			while (start < text.length())
			{
				int nl = text.indexOf('\n', start);
				if (nl == -1)
				{
					nl = text.length();
				}

				final String line = text.substring(start, nl);
				if (!line.trim().isEmpty())
				{
					try
					{
						handleLine(ws, line);
					}
					catch (Exception ex)
					{
						log.debug("[Twitch] failed to handle line: {}", line, ex);
					}
				}

				start = nl + 1;
			}
		}

		@Override
		public void onClosed(WebSocket ws, int code, String reason)
		{
			if (webSocket == ws)
			{
				onDisconnected("closed by server (" + code + ")");
			}
		}

		@Override
		public void onFailure(WebSocket ws, Throwable t, @Nullable Response response)
		{
			if (webSocket != ws)
			{
				// A socket we already replaced or cancelled.
				return;
			}

			log.debug("[Twitch] websocket failure", t);
			onDisconnected(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}
}
