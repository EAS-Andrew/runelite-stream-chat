package com.streamchat;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(StreamChatConfig.GROUP)
public interface StreamChatConfig extends Config
{
	String GROUP = "streamchat";

	@ConfigSection(
		name = "Twitch",
		description = "Twitch chat. No login required.",
		position = 0
	)
	String twitchSection = "twitchSection";

	@ConfigSection(
		name = "YouTube",
		description = "YouTube live chat. Requires your own Google API key.",
		position = 1
	)
	String youtubeSection = "youtubeSection";

	@ConfigSection(
		name = "Kick",
		description = "Kick chat. No login required.",
		position = 2
	)
	String kickSection = "kickSection";

	@ConfigSection(
		name = "Display",
		description = "How messages appear in the chatbox",
		position = 3
	)
	String displaySection = "displaySection";

	@ConfigSection(
		name = "On-screen panel",
		description = "Appearance of the movable panel, when Display is set to use it",
		position = 4
	)
	String overlaySection = "overlaySection";

	@ConfigSection(
		name = "Events",
		description = "Subs, gifts, raids and donations, and how to react to them",
		position = 5
	)
	String eventSection = "eventSection";

	@ConfigSection(
		name = "Filters",
		description = "What to hide, and how fast messages arrive",
		position = 6
	)
	String filterSection = "filterSection";

	// ------------------------------------------------------------------ Twitch

	@ConfigItem(
		keyName = "twitchEnabled",
		name = "Enable Twitch",
		description = "Read Twitch chat. Anonymous and read-only, so no Twitch account or token is needed.",
		position = 0,
		section = twitchSection
	)
	default boolean twitchEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "twitchChannels",
		name = "Channels",
		description = "Twitch channel names, comma separated. Use the name from the URL, e.g. 'odablock'.",
		position = 1,
		section = twitchSection
	)
	default String twitchChannels()
	{
		return "";
	}

	// ----------------------------------------------------------------- YouTube

	@ConfigItem(
		keyName = "youtubeEnabled",
		name = "Enable YouTube",
		description = "Read YouTube live chat. Requires a Google API key, below.",
		position = 0,
		section = youtubeSection
	)
	default boolean youtubeEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "youtubeApiKey",
		name = "API key",
		description = "A YouTube Data API v3 key from your own Google Cloud project. Sent only to"
			+ " googleapis.com. Note it is stored in plain text in your RuneLite profile like any other"
			+ " setting (masked here, not encrypted), so restrict the key to the YouTube Data API.",
		position = 1,
		section = youtubeSection,
		secret = true
	)
	default String youtubeApiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "youtubeTarget",
		name = "Video or channel",
		description = "A live video URL or ID (cheap: 1 quota unit), or a channel ID / @handle (expensive:"
			+ " 100 quota units each time it looks for the current stream). Prefer the video URL.",
		position = 2,
		section = youtubeSection
	)
	default String youtubeTarget()
	{
		return "";
	}

	@Range(min = 2, max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "youtubePollSeconds",
		name = "Minimum poll interval",
		description = "Lower bound on how often to fetch new messages. Each fetch costs 5 of your 10,000"
			+ " daily quota units, so 5s gives roughly 2.5 hours of viewing per day.",
		position = 3,
		section = youtubeSection
	)
	default int youtubePollSeconds()
	{
		return 5;
	}

	@Range(min = 1, max = 120)
	@Units(Units.MINUTES)
	@ConfigItem(
		keyName = "youtubeOfflineRecheckMinutes",
		name = "Offline re-check",
		description = "How often to look for a stream when the target is not live. Keep this high for a"
			+ " channel/handle target, because every check costs 100 quota units.",
		position = 4,
		section = youtubeSection
	)
	default int youtubeOfflineRecheckMinutes()
	{
		return 10;
	}

	// -------------------------------------------------------------------- Kick

	@ConfigItem(
		keyName = "kickEnabled",
		name = "Enable Kick",
		description = "Read Kick chat. Anonymous and read-only, so no Kick account or token is needed.",
		position = 0,
		section = kickSection
	)
	default boolean kickEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "kickChannels",
		name = "Channels",
		description = "Kick channel slugs, comma separated. Use the name from the URL, e.g. 'trainwreckstv'.",
		position = 1,
		section = kickSection
	)
	default String kickChannels()
	{
		return "";
	}

	@ConfigItem(
		keyName = "kickChatroomIds",
		name = "Chatroom IDs",
		description = "Only needed if Kick blocks the automatic lookup. Format: slug:id, comma separated"
			+ " (e.g. 'trainwreckstv:123456'). See the plugin README for how to find the ID.",
		position = 2,
		section = kickSection
	)
	default String kickChatroomIds()
	{
		return "";
	}

	// ----------------------------------------------------------------- Display

	@ConfigItem(
		keyName = "displayMode",
		name = "Display",
		description = "Show messages in the chatbox, in a movable on-screen panel, or both.",
		position = 0,
		section = displaySection
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.CHATBOX;
	}

	@ConfigItem(
		keyName = "chatTarget",
		name = "Chatbox tab",
		description = "Which chatbox tab stream messages appear in. Ignored when displaying only in"
			+ " the on-screen panel.",
		position = 1,
		section = displaySection
	)
	default ChatTarget chatTarget()
	{
		return ChatTarget.GAME;
	}

	@ConfigItem(
		keyName = "showIcon",
		name = "Source icon",
		description = "Prefix each line with the platform's icon.",
		position = 4,
		section = displaySection
	)
	default boolean showIcon()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showChannel",
		name = "Show channel name",
		description = "Include the channel the message came from. Useful when watching several at once.",
		position = 5,
		section = displaySection
	)
	default boolean showChannel()
	{
		return false;
	}

	@ConfigItem(
		keyName = "colorAuthors",
		name = "Colour author names",
		description = "Use the chatter's own name colour where the platform provides one, otherwise the"
			+ " platform's colour.",
		position = 6,
		section = displaySection
	)
	default boolean colorAuthors()
	{
		return true;
	}

	@Range(min = 20, max = 400)
	@ConfigItem(
		keyName = "maxMessageLength",
		name = "Max message length",
		description = "Longer messages are truncated with an ellipsis, so one wall of text cannot fill the"
			+ " chatbox.",
		position = 7,
		section = displaySection
	)
	default int maxMessageLength()
	{
		return 180;
	}

	// ------------------------------------------------------------------ Events

	@ConfigItem(
		keyName = "showEvents",
		name = "Show events",
		description = "Show subscriptions, gifted subs, raids, cheers and Super Chats alongside chat.",
		position = 0,
		section = eventSection
	)
	default boolean showEvents()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightEvents",
		name = "Highlight events",
		description = "Draw event lines in their own colour so they stand out from ordinary chat.",
		position = 1,
		section = eventSection
	)
	default boolean highlightEvents()
	{
		return true;
	}

	@Alpha
	@ConfigItem(
		keyName = "eventColor",
		name = "Event colour",
		description = "Colour used for event lines.",
		position = 2,
		section = eventSection
	)
	default Color eventColor()
	{
		return new Color(0xFFD44D);
	}

	@ConfigItem(
		keyName = "eventTrigger",
		name = "React to",
		description = "Which events set off the sound, notification and animation below.",
		position = 3,
		section = eventSection
	)
	default EventTrigger eventTrigger()
	{
		return EventTrigger.SUBS_AND_GIFTS;
	}

	@ConfigItem(
		keyName = "eventSound",
		name = "Sound",
		description = "Play a game sound when a matching event lands.",
		position = 4,
		section = eventSection
	)
	default EventSound eventSound()
	{
		return EventSound.NONE;
	}

	@ConfigItem(
		keyName = "eventNotify",
		name = "Notification",
		description = "Send a RuneLite notification, which follows your notification settings"
			+ " (tray popup, sound, window flash).",
		position = 5,
		section = eventSection
	)
	default boolean eventNotify()
	{
		return false;
	}

	@ConfigItem(
		keyName = "eventGraphic",
		name = "Play graphic",
		description = "Play a graphic effect on your character when a matching event lands -- the"
			+ " same fireworks you get for a level up. Purely visual and only you can see it: no"
			+ " input is sent to the game and nothing happens server-side.",
		position = 6,
		section = eventSection
	)
	default EventGraphic eventGraphic()
	{
		return EventGraphic.NONE;
	}

	@ConfigItem(
		keyName = "eventAnimation",
		name = "Play emote",
		description = "Play an emote animation on your character when a matching event lands."
			+ " This is purely visual and only you can see it -- no input is sent to the game and"
			+ " nothing happens server-side. It is cancelled the moment you do anything.",
		position = 7,
		section = eventSection
	)
	default EmoteAnimation eventAnimation()
	{
		return EmoteAnimation.NONE;
	}

	// ----------------------------------------------------------------- Filters

	@Range(min = 1, max = 20)
	@ConfigItem(
		keyName = "maxMessagesPerTick",
		name = "Messages per tick",
		description = "Most messages to print per game tick (0.6s). Keeps a busy stream from flooding the"
			+ " chatbox and pushing game messages out of view.",
		position = 0,
		section = filterSection
	)
	default int maxMessagesPerTick()
	{
		return 2;
	}

	@Range(min = 10, max = 500)
	@ConfigItem(
		keyName = "queueSize",
		name = "Queue size",
		description = "How many waiting messages to hold. When full, the oldest are dropped so the chatbox"
			+ " stays close to live rather than falling behind.",
		position = 1,
		section = filterSection
	)
	default int queueSize()
	{
		return 100;
	}

	@ConfigItem(
		keyName = "announceDropped",
		name = "Note dropped messages",
		description = "Print an occasional '(n messages skipped)' line when the queue overflows, so a gap is"
			+ " visible rather than silent.",
		position = 2,
		section = filterSection
	)
	default boolean announceDropped()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideCommands",
		name = "Hide bot commands",
		description = "Hide messages starting with ! or ?, which are usually bot commands.",
		position = 3,
		section = filterSection
	)
	default boolean hideCommands()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideEmoteOnly",
		name = "Hide emote-only messages",
		description = "Hide messages that contain nothing but emotes, which the game cannot draw"
			+ " anyway and which show up as a wall of names like 'KEKW KEKW'. Only counts emotes the"
			+ " platform tells us about, so BTTV/7TV emotes still come through as text.",
		position = 4,
		section = filterSection
	)
	default boolean hideEmoteOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "blockedUsers",
		name = "Blocked users",
		description = "Comma separated names to hide entirely. Case insensitive. Useful for chat bots.",
		position = 5,
		section = filterSection
	)
	default String blockedUsers()
	{
		return "";
	}

	// ----------------------------------------------------------- On-screen panel

	@Range(min = 1, max = 50)
	@ConfigItem(
		keyName = "overlayLines",
		name = "Max messages",
		description = "How many recent messages the panel keeps. Once you drag the panel to a fixed"
			+ " height it shows as many of these as actually fit.",
		position = 0,
		section = overlaySection
	)
	default int overlayLines()
	{
		return 8;
	}

	@Range(min = 120, max = 800)
	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "Starting width in pixels. Dragging the panel's edge in game overrides this.",
		position = 1,
		section = overlaySection
	)
	default int overlayWidth()
	{
		return 250;
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayBackgroundColor",
		name = "Background",
		description = "Panel background. Transparent by default; raise the alpha for a solid panel."
			+ " Keep 'Text shadow' on when this is see-through.",
		position = 2,
		section = overlaySection
	)
	default Color overlayBackgroundColor()
	{
		return new Color(0, 0, 0, 0);
	}

	@Alpha
	@ConfigItem(
		keyName = "overlayTextColor",
		name = "Message text",
		description = "Colour of the message body. Author names use the platform/chatter colour.",
		position = 3,
		section = overlaySection
	)
	default Color overlayTextColor()
	{
		return Color.WHITE;
	}

	@ConfigItem(
		keyName = "overlayFont",
		name = "Font",
		description = "Font used by the panel.",
		position = 4,
		section = overlaySection
	)
	default OverlayFont overlayFont()
	{
		return OverlayFont.DEFAULT;
	}

	@ConfigItem(
		keyName = "overlayShadow",
		name = "Text shadow",
		description = "Draw a drop shadow behind the text, which keeps it readable over a transparent"
			+ " background.",
		position = 5,
		section = overlaySection
	)
	default boolean overlayShadow()
	{
		return true;
	}

	@ConfigItem(
		keyName = "overlayBorder",
		name = "Border",
		description = "Draw a thin border around the panel.",
		position = 6,
		section = overlaySection
	)
	default boolean overlayBorder()
	{
		return false;
	}

	@ConfigItem(
		keyName = "blockedWords",
		name = "Blocked words",
		description = "Comma separated. Any message containing one of these is hidden. Case insensitive.",
		position = 6,
		section = filterSection
	)
	default String blockedWords()
	{
		return "";
	}
}
