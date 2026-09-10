package com.streamchat.kick;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.streamchat.AbstractChatSource;
import com.streamchat.ChatSink;
import com.streamchat.StreamChatMessage;
import com.streamchat.StreamPlatform;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * Reads Kick chat over Kick's public Pusher chatroom socket.
 *
 * <p><b>Why not the official Kick API?</b> Kick's official developer API authenticates with
 * OAuth 2.1 + PKCE, but it delivers chat as <em>webhooks</em> -- it POSTs events to a public HTTPS
 * endpoint you host. A desktop game client has no such endpoint, and standing up a relay server
 * just to forward chat would mean routing a user's messages through third-party infrastructure.
 * Kick's own web player reads chat from the Pusher socket used here, unauthenticated, so that is
 * what this source uses.
 *
 * <p>The trade-off is that this is an undocumented endpoint and Kick may change it. Failures are
 * reported plainly in the status readout rather than being retried silently forever.
 */
@Slf4j
public class KickChatSource extends AbstractChatSource
{
	/**
	 * Kick's public Pusher application key, as used by the kick.com web player. This is a public
	 * client identifier, not a secret -- it is served to every visitor in the site's JavaScript.
	 */
	private static final String PUSHER_APP_KEY = "32cbd69e4b950bf97679";
	private static final String PUSHER_URL = "wss://ws-us2.pusher.com/app/" + PUSHER_APP_KEY
		+ "?protocol=7&client=runelite-stream-chat&version=8.4.0&flash=false";

	private static final String CHANNEL_API = "https://kick.com/api/v2/channels/";
	private static final String CHAT_MESSAGE_EVENT = "App\\Events\\ChatMessageEvent";

	/**
	 * Kick inlines emotes in the message body as {@code [emote:<id>:<name>]}. The game cannot show
	 * the image, so the markup is reduced to the emote's name -- which is what Twitch sends in the
	 * first place, so both platforms end up reading the same.
	 */
	private static final Pattern EMOTE_MARKUP = Pattern.compile("\\[emote:\\d+:([^\\]]*)\\]");

	private static final long INACTIVITY_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);
	private static final long WATCHDOG_PERIOD_MS = TimeUnit.MINUTES.toMillis(1);

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final List<String> channels;
	private final Map<Long, String> chatroomOverrides;

	/** chatroom id to channel slug, resolved once per connection. */
	private final Map<Long, String> chatrooms = new LinkedHashMap<>();

	private volatile WebSocket webSocket;
	private volatile ScheduledFuture<?> watchdog;
	private volatile long lastActivityMs;

	public KickChatSource(ScheduledExecutorService executor, ChatSink sink, OkHttpClient httpClient,
		Gson gson, List<String> channels, Map<Long, String> chatroomOverrides)
	{
		super(executor, sink);
		this.httpClient = httpClient.newBuilder()
			.readTimeout(0, TimeUnit.MILLISECONDS)
			.pingInterval(30, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.channels = channels;
		this.chatroomOverrides = chatroomOverrides;
	}

	@Override
	public StreamPlatform platform()
	{
		return StreamPlatform.KICK;
	}

	@Override
	protected void doConnect() throws IOException
	{
		if (channels.isEmpty() && chatroomOverrides.isEmpty())
		{
			onFatal("no channels configured");
			return;
		}

		chatrooms.clear();
		chatrooms.putAll(chatroomOverrides);

		final List<String> unresolved = new ArrayList<>();
		for (String slug : channels)
		{
			if (chatrooms.containsValue(slug))
			{
				// Already supplied explicitly as a chatroom id override.
				continue;
			}

			final Long chatroomId = resolveChatroomId(slug);
			if (chatroomId == null)
			{
				unresolved.add(slug);
			}
			else
			{
				chatrooms.put(chatroomId, slug);
			}
		}

		if (chatrooms.isEmpty())
		{
			// Kick fronts its site API with Cloudflare, which intermittently refuses non-browser
			// clients. The chatroom id never changes for a channel, so the config offers a manual
			// override; point the user at it rather than retrying a block forever.
			onFatal("could not resolve chatroom id for " + String.join(", ", unresolved)
				+ " (set it manually in the Kick chatroom IDs config option)");
			return;
		}

		if (!unresolved.isEmpty())
		{
			log.warn("[Kick] could not resolve {}, continuing with {}", unresolved, chatrooms.values());
		}

		lastActivityMs = System.currentTimeMillis();
		webSocket = httpClient.newWebSocket(new Request.Builder().url(PUSHER_URL).build(), new Listener());

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
			onDisconnected("connection went quiet");
		}
	}

	/**
	 * Looks up a channel's numeric chatroom id from its slug. Returns null when the lookup is
	 * blocked or the channel does not exist.
	 */
	@Nullable
	private Long resolveChatroomId(String slug) throws IOException
	{
		final HttpUrl url = HttpUrl.get(CHANNEL_API + slug);
		final Request request = new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			if (!response.isSuccessful())
			{
				log.debug("[Kick] channel lookup for {} returned {}", slug, response.code());
				return null;
			}

			final ResponseBody body = response.body();
			if (body == null)
			{
				return null;
			}

			final JsonObject json = gson.fromJson(body.charStream(), JsonObject.class);
			if (json == null || !json.has("chatroom") || json.get("chatroom").isJsonNull())
			{
				return null;
			}

			final JsonObject chatroom = json.getAsJsonObject("chatroom");
			if (!chatroom.has("id") || chatroom.get("id").isJsonNull())
			{
				return null;
			}

			return chatroom.get("id").getAsLong();
		}
		catch (JsonSyntaxException | IllegalStateException | UnsupportedOperationException ex)
		{
			// A Cloudflare interstitial is HTML, not the JSON we asked for.
			log.debug("[Kick] channel lookup for {} did not return usable JSON", slug, ex);
			return null;
		}
	}

	private void handleFrame(WebSocket ws, String text)
	{
		final JsonObject frame;
		try
		{
			frame = gson.fromJson(text, JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			log.debug("[Kick] unparseable frame", ex);
			return;
		}

		if (frame == null || !frame.has("event") || frame.get("event").isJsonNull())
		{
			return;
		}

		final String event = frame.get("event").getAsString();
		switch (event)
		{
			case "pusher:connection_established":
				for (Long chatroomId : chatrooms.keySet())
				{
					subscribe(ws, chatroomId);
				}
				onConnected();
				break;

			case "pusher:ping":
				ws.send("{\"event\":\"pusher:pong\",\"data\":{}}");
				break;

			case "pusher:error":
				log.warn("[Kick] pusher error: {}", frame);
				onDisconnected("pusher error");
				break;

			case CHAT_MESSAGE_EVENT:
				handleChatMessage(frame);
				break;

			default:
				// pusher_internal:subscription_succeeded and Kick's other channel events.
				break;
		}
	}

	private void subscribe(WebSocket ws, long chatroomId)
	{
		// Public chatrooms need no auth token, so "auth" is deliberately empty.
		final JsonObject data = new JsonObject();
		data.addProperty("auth", "");
		data.addProperty("channel", "chatrooms." + chatroomId + ".v2");

		final JsonObject subscribe = new JsonObject();
		subscribe.addProperty("event", "pusher:subscribe");
		subscribe.add("data", data);

		ws.send(gson.toJson(subscribe));
	}

	private void handleChatMessage(JsonObject frame)
	{
		// Pusher nests the payload as a JSON *string* inside the frame.
		if (!frame.has("data") || !frame.get("data").isJsonPrimitive())
		{
			return;
		}

		final JsonObject payload;
		try
		{
			payload = gson.fromJson(frame.get("data").getAsString(), JsonObject.class);
		}
		catch (JsonSyntaxException ex)
		{
			log.debug("[Kick] unparseable chat payload", ex);
			return;
		}

		if (payload == null)
		{
			return;
		}

		final String rawContent = optString(payload, "content");
		if (rawContent == null || rawContent.isEmpty())
		{
			return;
		}

		final String content = stripEmoteMarkup(rawContent);
		if (content.isEmpty())
		{
			return;
		}

		final JsonObject sender = payload.has("sender") && payload.get("sender").isJsonObject()
			? payload.getAsJsonObject("sender")
			: null;
		if (sender == null)
		{
			return;
		}

		final String author = optString(sender, "username");
		if (author == null)
		{
			return;
		}

		Color color = null;
		if (sender.has("identity") && sender.get("identity").isJsonObject())
		{
			color = parseColor(optString(sender.getAsJsonObject("identity"), "color"));
		}

		String channel = "";
		if (payload.has("chatroom_id") && payload.get("chatroom_id").isJsonPrimitive())
		{
			channel = chatrooms.getOrDefault(payload.get("chatroom_id").getAsLong(), "");
		}

		final String id = optString(payload, "id");

		sink.onMessage(StreamChatMessage.builder()
			.platform(StreamPlatform.KICK)
			.channel(channel)
			.author(author)
			.message(content)
			.authorColor(color)
			.id(id != null ? id : author + ' ' + content)
			.build());
	}

	/** Rewrites {@code [emote:123:KEKW]} to {@code KEKW}. */
	static String stripEmoteMarkup(String content)
	{
		if (content.indexOf("[emote:") == -1)
		{
			return content;
		}

		final Matcher m = EMOTE_MARKUP.matcher(content);
		final StringBuffer out = new StringBuffer(content.length());
		while (m.find())
		{
			// The name is user-visible text, so quote it out of the replacement syntax.
			m.appendReplacement(out, Matcher.quoteReplacement(m.group(1)));
		}
		m.appendTail(out);

		return out.toString().trim();
	}

	@Nullable
	private static String optString(JsonObject obj, String key)
	{
		if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive())
		{
			return null;
		}
		return obj.get(key).getAsString();
	}

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

	private class Listener extends WebSocketListener
	{
		@Override
		public void onMessage(WebSocket ws, String text)
		{
			lastActivityMs = System.currentTimeMillis();

			try
			{
				handleFrame(ws, text);
			}
			catch (Exception ex)
			{
				log.debug("[Kick] failed to handle frame", ex);
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
				return;
			}

			log.debug("[Kick] websocket failure", t);
			onDisconnected(t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
		}
	}
}
