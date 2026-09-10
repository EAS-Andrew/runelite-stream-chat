package com.streamchat;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.streamchat.kick.KickChatSource;
import com.streamchat.twitch.TwitchChatSource;
import com.streamchat.youtube.YouTubeChatSource;
import com.streamchat.youtube.YouTubeTarget;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Stream Chat",
	description = "Shows live Twitch, YouTube and Kick chat in the game chatbox with source icons",
	tags = {"stream", "twitch", "youtube", "kick", "chat", "streaming", "live"}
)
@Slf4j
public class StreamChatPlugin extends Plugin
{
	private static final String STATUS_COMMAND = "streamchat";

	/** Config edits arrive as the user types; wait for them to settle before reconnecting. */
	private static final long CONFIG_DEBOUNCE_MS = 1_000L;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private StreamChatConfig config;

	@Inject
	private MessageRouter router;

	@Inject
	private ChatIcons icons;

	@Inject
	private StreamChatOverlay overlay;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	private final Map<StreamPlatform, ChatSource> sources = new EnumMap<>(StreamPlatform.class);

	/**
	 * The plugin owns its executor rather than borrowing RuneLite's shared one: the YouTube source
	 * makes blocking HTTP calls on it, and a slow response should never hold up another plugin's
	 * scheduled work.
	 */
	private ScheduledExecutorService executor;
	private ScheduledFuture<?> pendingRebuild;

	@Provides
	StreamChatConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(StreamChatConfig.class);
	}

	@Override
	protected void startUp()
	{
		final ThreadFactory threadFactory = r ->
		{
			final Thread t = new Thread(r, "stream-chat");
			t.setDaemon(true);
			return t;
		};
		executor = Executors.newSingleThreadScheduledExecutor(threadFactory);

		overlayManager.add(overlay);

		clientThread.invoke(this::loadIconsIfReady);
		rebuildSources();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);

		if (pendingRebuild != null)
		{
			pendingRebuild.cancel(false);
			pendingRebuild = null;
		}

		for (ChatSource source : sources.values())
		{
			source.stop();
		}
		sources.clear();

		if (executor != null)
		{
			executor.shutdownNow();
			executor = null;
		}

		router.reset();
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		router.flush();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		final GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.LOGGED_IN)
		{
			loadIconsIfReady();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!StreamChatConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}

		final String key = event.getKey();
		// Display and filter options take effect on the next message with no reconnect needed.
		if (!key.startsWith("twitch") && !key.startsWith("youtube") && !key.startsWith("kick"))
		{
			return;
		}

		if (executor == null)
		{
			return;
		}

		if (pendingRebuild != null)
		{
			pendingRebuild.cancel(false);
		}

		pendingRebuild = executor.schedule(this::rebuildSources, CONFIG_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (!STATUS_COMMAND.equalsIgnoreCase(event.getCommand()))
		{
			return;
		}

		printStatus();
	}

	private void loadIconsIfReady()
	{
		// createIndexedSprite needs a booted client, so wait for the login screen at the earliest.
		if (client.getGameState().getState() >= GameState.LOGIN_SCREEN.getState())
		{
			icons.load();
		}
	}

	/**
	 * Stops every source and starts the ones that are enabled and configured. Called on the plugin
	 * executor after config settles, so it must not touch client state.
	 */
	private synchronized void rebuildSources()
	{
		for (ChatSource source : sources.values())
		{
			source.stop();
		}
		sources.clear();

		final ScheduledExecutorService exec = executor;
		if (exec == null)
		{
			return;
		}

		if (config.twitchEnabled())
		{
			final List<String> channels = channelList(config.twitchChannels());
			if (!channels.isEmpty())
			{
				sources.put(StreamPlatform.TWITCH, new TwitchChatSource(
					exec, router, okHttpClient, channels));
			}
		}

		if (config.youtubeEnabled())
		{
			final YouTubeTarget target = YouTubeTarget.parse(config.youtubeTarget());
			if (target == null)
			{
				router.onStatus(StreamPlatform.YOUTUBE, SourceStatus.FAILED,
					config.youtubeTarget().trim().isEmpty()
						? "no video or channel configured"
						: "could not understand the video/channel setting");
			}
			else if (config.youtubeApiKey().trim().isEmpty())
			{
				router.onStatus(StreamPlatform.YOUTUBE, SourceStatus.FAILED, "no API key configured");
			}
			else
			{
				sources.put(StreamPlatform.YOUTUBE, new YouTubeChatSource(
					exec, router, okHttpClient, gson,
					config.youtubeApiKey().trim(), target,
					config.youtubePollSeconds(), config.youtubeOfflineRecheckMinutes()));
			}
		}

		if (config.kickEnabled())
		{
			final List<String> channels = channelList(config.kickChannels());
			final Map<Long, String> overrides = parseChatroomIds(config.kickChatroomIds());
			if (!channels.isEmpty() || !overrides.isEmpty())
			{
				sources.put(StreamPlatform.KICK, new KickChatSource(
					exec, router, okHttpClient, gson, channels, overrides));
			}
		}

		for (ChatSource source : sources.values())
		{
			source.start();
		}

		// Clear stale state for platforms that are no longer running at all.
		for (StreamPlatform platform : StreamPlatform.values())
		{
			if (!sources.containsKey(platform) && isConfiguredOff(platform))
			{
				router.onStatus(platform, SourceStatus.DISABLED, "off");
			}
		}
	}

	private boolean isConfiguredOff(StreamPlatform platform)
	{
		switch (platform)
		{
			case TWITCH:
				return !config.twitchEnabled();
			case YOUTUBE:
				return !config.youtubeEnabled();
			case KICK:
				return !config.kickEnabled();
			default:
				return true;
		}
	}

	private void printStatus()
	{
		for (StreamPlatform platform : StreamPlatform.values())
		{
			final SourceStatus status = router.statusOf(platform);
			final String detail = router.statusDetailOf(platform);

			final Color color;
			switch (status)
			{
				case CONNECTED:
					color = Color.GREEN;
					break;
				case CONNECTING:
					color = Color.YELLOW;
					break;
				case FAILED:
					color = Color.RED;
					break;
				default:
					color = Color.GRAY;
					break;
			}

			final ChatMessageBuilder builder = new ChatMessageBuilder()
				.append(platform.getDefaultAuthorColor(), platform.getDisplayName())
				.append(": ")
				.append(color, status.name().toLowerCase(Locale.ROOT));

			if (!detail.isEmpty())
			{
				builder.append(Color.GRAY, " (" + Text.escapeJagex(detail) + ")");
			}

			client.addChatMessage(config.chatTarget().getMessageType(), "", builder.build(), null);
		}
	}

	/** Splits a comma separated channel list, normalising names to lower case. */
	static List<String> channelList(String csv)
	{
		if (csv == null)
		{
			return Collections.emptyList();
		}

		final List<String> out = new ArrayList<>();
		for (String part : Text.fromCSV(csv))
		{
			String name = part.trim().toLowerCase(Locale.ROOT);
			if (name.isEmpty())
			{
				continue;
			}

			// Tolerate a pasted URL or a leading # / @.
			final int slash = name.lastIndexOf('/');
			if (slash != -1)
			{
				name = name.substring(slash + 1);
			}
			while (!name.isEmpty() && (name.charAt(0) == '#' || name.charAt(0) == '@'))
			{
				name = name.substring(1);
			}

			if (!name.isEmpty() && !out.contains(name))
			{
				out.add(name);
			}
		}

		return out;
	}

	/** Parses the {@code slug:id, slug:id} Kick chatroom override list into {id: slug}. */
	static Map<Long, String> parseChatroomIds(String csv)
	{
		if (csv == null)
		{
			return Collections.emptyMap();
		}

		final Map<Long, String> out = new LinkedHashMap<>();
		for (String part : Text.fromCSV(csv))
		{
			final String entry = part.trim();
			final int colon = entry.lastIndexOf(':');
			if (colon <= 0 || colon == entry.length() - 1)
			{
				continue;
			}

			final String slug = entry.substring(0, colon).trim().toLowerCase(Locale.ROOT);
			try
			{
				out.put(Long.parseLong(entry.substring(colon + 1).trim()), slug);
			}
			catch (NumberFormatException ex)
			{
				log.debug("ignoring malformed Kick chatroom override: {}", entry);
			}
		}

		return out;
	}
}
