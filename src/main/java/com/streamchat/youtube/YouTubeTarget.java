package com.streamchat.youtube;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.Value;

/**
 * What the user typed into the YouTube target config option, resolved into something the Data API
 * can look up.
 *
 * <p>The distinction matters for quota: a video id costs 1 unit to resolve, whereas finding the
 * current live video for a channel costs 100 units because it needs {@code search.list}. Accepting
 * either, and telling the two apart reliably, is what lets the plugin recommend the cheap path.
 */
@Value
public class YouTubeTarget
{
	public enum Kind
	{
		VIDEO,
		CHANNEL_ID,
		HANDLE
	}

	Kind kind;
	String value;

	private static final Pattern VIDEO_ID = Pattern.compile("^[A-Za-z0-9_-]{11}$");
	private static final Pattern CHANNEL_ID = Pattern.compile("^UC[A-Za-z0-9_-]{22}$");

	// youtube.com/watch?v=ID, youtu.be/ID, youtube.com/live/ID, youtube.com/embed/ID
	private static final Pattern URL_VIDEO = Pattern.compile(
		"(?:youtube\\.com/(?:watch\\?(?:.*&)?v=|live/|embed/|shorts/)|youtu\\.be/)([A-Za-z0-9_-]{11})");
	private static final Pattern URL_CHANNEL = Pattern.compile("youtube\\.com/channel/(UC[A-Za-z0-9_-]{22})");
	private static final Pattern URL_HANDLE = Pattern.compile("youtube\\.com/@([A-Za-z0-9._-]+)");

	/**
	 * Parses a video id, channel id, {@code @handle}, or any of the common YouTube URL forms.
	 * Returns null when the input is blank or unrecognised.
	 */
	@Nullable
	public static YouTubeTarget parse(@Nullable String raw)
	{
		if (raw == null)
		{
			return null;
		}

		final String input = raw.trim();
		if (input.isEmpty())
		{
			return null;
		}

		if (input.toLowerCase(Locale.ROOT).contains("youtu"))
		{
			final Matcher video = URL_VIDEO.matcher(input);
			if (video.find())
			{
				return new YouTubeTarget(Kind.VIDEO, video.group(1));
			}

			final Matcher channel = URL_CHANNEL.matcher(input);
			if (channel.find())
			{
				return new YouTubeTarget(Kind.CHANNEL_ID, channel.group(1));
			}

			final Matcher handle = URL_HANDLE.matcher(input);
			if (handle.find())
			{
				return new YouTubeTarget(Kind.HANDLE, handle.group(1));
			}

			return null;
		}

		if (input.startsWith("@"))
		{
			final String handle = input.substring(1);
			return handle.isEmpty() ? null : new YouTubeTarget(Kind.HANDLE, handle);
		}

		// Check channel id first: "UC" + 22 chars is 24 long, so it cannot collide with a video id.
		if (CHANNEL_ID.matcher(input).matches())
		{
			return new YouTubeTarget(Kind.CHANNEL_ID, input);
		}

		if (VIDEO_ID.matcher(input).matches())
		{
			return new YouTubeTarget(Kind.VIDEO, input);
		}

		return null;
	}

	/** True when resolving this target needs {@code search.list}, which costs 100 quota units. */
	public boolean isExpensiveToResolve()
	{
		return kind != Kind.VIDEO;
	}
}
