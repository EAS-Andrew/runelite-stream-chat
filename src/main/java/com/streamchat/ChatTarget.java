package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ChatMessageType;

/** Which chatbox tab stream messages are printed to. */
@Getter
@RequiredArgsConstructor
public enum ChatTarget
{
	GAME("Game tab", ChatMessageType.GAMEMESSAGE, false),
	CHANNEL("Channel tab", ChatMessageType.FRIENDSCHAT, true),
	CLAN("Clan tab", ChatMessageType.CLAN_CHAT, true),
	TRADE("Trade tab", ChatMessageType.TRADE, false);

	private final String displayName;
	private final ChatMessageType messageType;

	/**
	 * Whether this tab renders a sender prefix. The friends-chat and clan tabs show the sender in
	 * brackets ahead of the line, which is a natural place to put the platform name; the game tab
	 * ignores it entirely.
	 */
	private final boolean showsSender;

	@Override
	public String toString()
	{
		return displayName;
	}
}
