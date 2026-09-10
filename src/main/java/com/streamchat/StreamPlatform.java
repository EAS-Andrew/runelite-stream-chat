package com.streamchat;

import java.awt.Color;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A supported stream chat platform.
 *
 * <p>{@link #iconResource} is resolved with {@code getResourceAsStream}, which is required for
 * plugins loaded from a jar on the Plugin Hub classpath.
 */
@Getter
@RequiredArgsConstructor
public enum StreamPlatform
{
	TWITCH("Twitch", "twitch.png", new Color(0xB98CFF)),
	YOUTUBE("YouTube", "youtube.png", new Color(0xFF6B6B)),
	KICK("Kick", "kick.png", new Color(0x7BFF52));

	private final String displayName;
	private final String iconResource;

	/**
	 * Fallback colour for author names when the platform does not supply one. These are
	 * deliberately lightened versions of each brand colour -- the real brand colours are too dark
	 * to read against the opaque chatbox background.
	 */
	private final Color defaultAuthorColor;

	@Override
	public String toString()
	{
		return displayName;
	}
}
