package com.streamchat.youtube;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.streamchat.AbstractChatSource;
import com.streamchat.ChatSink;
import com.streamchat.StreamChatMessage;
import com.streamchat.StreamPlatform;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads YouTube live chat through the YouTube Data API v3.
 *
 * <p><b>Auth.</b> Reading a public live chat is public read-only data, which Google authenticates
 * with an API key rather than OAuth. The plugin therefore asks for a key the user creates in their
 * own Google Cloud project: the quota consumed is theirs, the key stays in their RuneLite config,
 * and no shared credential ships with the plugin. OAuth would only be required to read a private or
 * unlisted broadcast, or to post messages, and this plugin does neither.
 *
 * <p><b>Quota.</b> This is the one real constraint. A Google project gets 10,000 units/day.
 * {@code liveChatMessages.list} costs 5 units per poll, so polling every 5 seconds spends about
 * 3,600 units per hour -- roughly 2.5 hours of viewing per day. Resolving a target costs 1 unit for
 * a video id but 100 units for a channel id or handle, because that needs {@code search.list}.
 * Because of that gap, a channel target is re-checked on a deliberately slow timer while it is
 * offline, and the config text steers users towards pasting the live video URL.
 */
@Slf4j
public class YouTubeChatSource extends AbstractChatSource
{
	private static final HttpUrl API_BASE = HttpUrl.get("https://www.googleapis.com/youtube/v3/");

	/** Floor on the API's suggested poll interval, so a busy chat cannot drain the daily quota. */
	private static final long MIN_POLL_INTERVAL_MS = 2_000L;
	private static final long DEFAULT_POLL_INTERVAL_MS = 5_000L;

	private final OkHttpClient httpClient;
	private final Gson gson;
	private final String apiKey;
	private final YouTubeTarget target;
	private final long minPollIntervalMs;
	private final long offlineRecheckMs;

	private volatile ScheduledFuture<?> pollTask;
	private volatile String liveChatId;
	private volatile String nextPageToken;

	/**
	 * The API replays recent history on the first page. Showing it would dump a wall of stale
	 * messages into the chatbox on every connect, so the first page is consumed for its page token
	 * only.
	 */
	private volatile boolean primed;

	public YouTubeChatSource(ScheduledExecutorService executor, ChatSink sink, OkHttpClient httpClient,
		Gson gson, String apiKey, YouTubeTarget target, int minPollSeconds, int offlineRecheckMinutes)
	{
		super(executor, sink);
		this.httpClient = httpClient;
		this.gson = gson;
		this.apiKey = apiKey;
		this.target = target;
		this.minPollIntervalMs = Math.max(MIN_POLL_INTERVAL_MS, TimeUnit.SECONDS.toMillis(minPollSeconds));
		this.offlineRecheckMs = TimeUnit.MINUTES.toMillis(Math.max(1, offlineRecheckMinutes));
	}

	@Override
	public StreamPlatform platform()
	{
		return StreamPlatform.YOUTUBE;
	}

	@Override
	protected void doConnect() throws IOException
	{
		if (apiKey.isEmpty())
		{
			onFatal("no API key configured");
			return;
		}

		liveChatId = null;
		nextPageToken = null;
		primed = false;

		final String videoId = resolveVideoId();
		if (videoId == null)
		{
			scheduleOfflineRecheck();
			return;
		}

		liveChatId = resolveLiveChatId(videoId);
		if (liveChatId == null)
		{
			scheduleOfflineRecheck();
			return;
		}

		onConnected();
		schedulePoll(0L);
	}

	@Override
	protected void doStop()
	{
		final ScheduledFuture<?> p = pollTask;
		if (p != null)
		{
			p.cancel(false);
			pollTask = null;
		}
	}

	/**
	 * A stream that has not started yet is normal, not an error. Re-check on a slow fixed timer
	 * rather than the usual backoff, because for a channel target each check costs 100 quota units.
	 */
	private void scheduleOfflineRecheck()
	{
		if (!isRunning())
		{
			return;
		}

		final long delay = target.isExpensiveToResolve() ? offlineRecheckMs : Math.min(offlineRecheckMs, 60_000L);
		log.debug("[YouTube] target not live, re-checking in {}ms", delay);

		pollTask = executor.schedule(() ->
		{
			if (!isRunning())
			{
				return;
			}

			try
			{
				doConnect();
			}
			catch (Exception ex)
			{
				onDisconnected(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
			}
		}, delay, TimeUnit.MILLISECONDS);
	}

	@Nullable
	private String resolveVideoId() throws IOException
	{
		switch (target.getKind())
		{
			case VIDEO:
				return target.getValue();

			case CHANNEL_ID:
				return findLiveVideoForChannel(target.getValue());

			case HANDLE:
			{
				final String channelId = resolveHandleToChannelId(target.getValue());
				return channelId == null ? null : findLiveVideoForChannel(channelId);
			}

			default:
				return null;
		}
	}

	/** channels.list with forHandle: 1 quota unit. */
	@Nullable
	private String resolveHandleToChannelId(String handle) throws IOException
	{
		final HttpUrl url = API_BASE.newBuilder()
			.addPathSegment("channels")
			.addQueryParameter("part", "id")
			.addQueryParameter("forHandle", "@" + handle)
			.addQueryParameter("key", apiKey)
			.build();

		final JsonObject json = get(url);
		if (json == null)
		{
			return null;
		}

		final JsonArray items = optArray(json, "items");
		if (items == null || items.size() == 0)
		{
			onFatal("no YouTube channel found for @" + handle);
			return null;
		}

		return optString(items.get(0).getAsJsonObject(), "id");
	}

	/** search.list: 100 quota units. Only used for channel/handle targets. */
	@Nullable
	private String findLiveVideoForChannel(String channelId) throws IOException
	{
		final HttpUrl url = API_BASE.newBuilder()
			.addPathSegment("search")
			.addQueryParameter("part", "id")
			.addQueryParameter("channelId", channelId)
			.addQueryParameter("eventType", "live")
			.addQueryParameter("type", "video")
			.addQueryParameter("maxResults", "1")
			.addQueryParameter("key", apiKey)
			.build();

		final JsonObject json = get(url);
		if (json == null)
		{
			return null;
		}

		final JsonArray items = optArray(json, "items");
		if (items == null || items.size() == 0)
		{
			return null;
		}

		final JsonObject id = optObject(items.get(0).getAsJsonObject(), "id");
		return id == null ? null : optString(id, "videoId");
	}

	/** videos.list: 1 quota unit. */
	@Nullable
	private String resolveLiveChatId(String videoId) throws IOException
	{
		final HttpUrl url = API_BASE.newBuilder()
			.addPathSegment("videos")
			.addQueryParameter("part", "liveStreamingDetails")
			.addQueryParameter("id", videoId)
			.addQueryParameter("key", apiKey)
			.build();

		final JsonObject json = get(url);
		if (json == null)
		{
			return null;
		}

		final JsonArray items = optArray(json, "items");
		if (items == null || items.size() == 0)
		{
			onFatal("no YouTube video found for id " + videoId);
			return null;
		}

		final JsonObject details = optObject(items.get(0).getAsJsonObject(), "liveStreamingDetails");
		if (details == null)
		{
			// Not a livestream at all, as opposed to a stream that has not started.
			onFatal("video " + videoId + " is not a livestream");
			return null;
		}

		// Absent once the broadcast ends, or before it starts.
		return optString(details, "activeLiveChatId");
	}

	private void schedulePoll(long delayMs)
	{
		if (!isRunning())
		{
			return;
		}

		pollTask = executor.schedule(this::poll, delayMs, TimeUnit.MILLISECONDS);
	}

	private void poll()
	{
		if (!isRunning())
		{
			return;
		}

		try
		{
			final long next = pollOnce();
			schedulePoll(next);
		}
		catch (IOException ex)
		{
			log.debug("[YouTube] poll failed", ex);
			onDisconnected(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
		}
		catch (Exception ex)
		{
			log.warn("[YouTube] unexpected poll error", ex);
			onDisconnected("unexpected error");
		}
	}

	/** Returns the delay before the next poll, in milliseconds. */
	private long pollOnce() throws IOException
	{
		final HttpUrl.Builder url = API_BASE.newBuilder()
			.addPathSegment("liveChat")
			.addPathSegment("messages")
			.addQueryParameter("liveChatId", liveChatId)
			.addQueryParameter("part", "snippet,authorDetails")
			.addQueryParameter("maxResults", "200")
			.addQueryParameter("key", apiKey);

		if (nextPageToken != null)
		{
			url.addQueryParameter("pageToken", nextPageToken);
		}

		final JsonObject json = get(url.build());
		if (json == null)
		{
			// get() already reported the reason; back off via the normal path.
			return minPollIntervalMs;
		}

		nextPageToken = optString(json, "nextPageToken");

		final JsonArray items = optArray(json, "items");
		if (items != null && primed)
		{
			for (int i = 0; i < items.size(); i++)
			{
				if (items.get(i).isJsonObject())
				{
					emit(items.get(i).getAsJsonObject());
				}
			}
		}

		primed = true;

		long interval = DEFAULT_POLL_INTERVAL_MS;
		if (json.has("pollingIntervalMillis") && json.get("pollingIntervalMillis").isJsonPrimitive())
		{
			interval = json.get("pollingIntervalMillis").getAsLong();
		}

		return Math.max(minPollIntervalMs, interval);
	}

	private void emit(JsonObject item)
	{
		final JsonObject snippet = optObject(item, "snippet");
		final JsonObject author = optObject(item, "authorDetails");
		if (snippet == null || author == null)
		{
			return;
		}

		// textMessageEvent is a normal chat line. Superchats and membership events carry their text
		// elsewhere; displayMessage covers the common cases in one field.
		String text = optString(snippet, "displayMessage");
		if (text == null)
		{
			final JsonObject details = optObject(snippet, "textMessageDetails");
			text = details == null ? null : optString(details, "messageText");
		}

		if (text == null || text.isEmpty())
		{
			return;
		}

		final String name = optString(author, "displayName");
		if (name == null)
		{
			return;
		}

		final String id = optString(item, "id");

		sink.onMessage(StreamChatMessage.builder()
			.platform(StreamPlatform.YOUTUBE)
			.channel("")
			.author(name)
			.message(text)
			.authorColor(null)
			.id(id != null ? id : name + ' ' + text)
			.build());
	}

	/**
	 * Performs a GET and returns the parsed body, or null after reporting the failure. Google's
	 * error {@code reason} distinguishes "try again later" from "this will never work", which is
	 * what decides between a retry and giving up.
	 */
	@Nullable
	private JsonObject get(HttpUrl url) throws IOException
	{
		final Request request = new Request.Builder()
			.url(url)
			.header("Accept", "application/json")
			.build();

		try (Response response = httpClient.newCall(request).execute())
		{
			final ResponseBody body = response.body();
			final String raw = body == null ? "" : body.string();

			if (response.isSuccessful())
			{
				try
				{
					return gson.fromJson(raw, JsonObject.class);
				}
				catch (JsonSyntaxException ex)
				{
					throw new IOException("malformed response from YouTube", ex);
				}
			}

			handleApiError(response.code(), raw);
			return null;
		}
	}

	private void handleApiError(int code, String raw) throws IOException
	{
		String reason = "";
		String message = "";

		try
		{
			final JsonObject json = gson.fromJson(raw, JsonObject.class);
			final JsonObject error = json == null ? null : optObject(json, "error");
			if (error != null)
			{
				message = optString(error, "message") == null ? "" : optString(error, "message");
				final JsonArray errors = optArray(error, "errors");
				if (errors != null && errors.size() > 0 && errors.get(0).isJsonObject())
				{
					final String r = optString(errors.get(0).getAsJsonObject(), "reason");
					reason = r == null ? "" : r;
				}
			}
		}
		catch (JsonSyntaxException ignored)
		{
			// Fall through to the status-code handling below.
		}

		switch (reason)
		{
			case "quotaExceeded":
			case "dailyLimitExceeded":
				onFatal("YouTube API quota exhausted for today; raise the poll interval or use a video URL");
				return;

			case "keyInvalid":
			case "badRequest":
			case "forbidden":
			case "liveChatDisabled":
				onFatal("YouTube rejected the request: " + (message.isEmpty() ? reason : message));
				return;

			case "liveChatEnded":
			case "liveChatNotFound":
				// The broadcast finished. Go back to looking for the next one.
				liveChatId = null;
				nextPageToken = null;
				primed = false;
				scheduleOfflineRecheck();
				return;

			case "rateLimitExceeded":
			case "backendError":
				throw new IOException("YouTube temporarily unavailable (" + reason + ")");

			default:
				break;
		}

		if (code == 401 || code == 403)
		{
			onFatal("YouTube rejected the API key" + (message.isEmpty() ? "" : ": " + message));
			return;
		}

		throw new IOException("YouTube returned HTTP " + code + (message.isEmpty() ? "" : ": " + message));
	}

	@Nullable
	private static String optString(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonPrimitive())
		{
			return null;
		}
		return obj.get(key).getAsString();
	}

	@Nullable
	private static JsonObject optObject(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonObject())
		{
			return null;
		}
		return obj.getAsJsonObject(key);
	}

	@Nullable
	private static JsonArray optArray(JsonObject obj, String key)
	{
		if (obj == null || !obj.has(key) || !obj.get(key).isJsonArray())
		{
			return null;
		}
		return obj.getAsJsonArray(key);
	}
}
