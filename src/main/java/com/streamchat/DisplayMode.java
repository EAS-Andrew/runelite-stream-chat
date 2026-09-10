package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where stream messages are shown. */
@Getter
@RequiredArgsConstructor
public enum DisplayMode
{
	CHATBOX("Chatbox", true, false),
	OVERLAY("On-screen panel", false, true),
	BOTH("Both", true, true);

	private final String displayName;
	private final boolean chatbox;
	private final boolean overlay;

	@Override
	public String toString()
	{
		return displayName;
	}
}
