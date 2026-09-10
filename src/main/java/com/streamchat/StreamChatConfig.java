package com.streamchat;

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
		name = "Filters",
		description = "What to hide, and how fast messages arrive",
		position = 4
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

	@ConfigItem(
		keyName = "twitchEvents",
		name = "Show subs and raids",
		description = "Also show subscription, gift and raid announcements, not just chat messages.",
		position = 2,
		section = twitchSection
	)
	default boolean twitchEvents()
	{
		return false;
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
		description = "A YouTube Data API v3 key from your own Google Cloud project. Stored in your RuneLite"
			+ " config and sent only to googleapis.com. See the plugin README for how to create one.",
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
		keyName = "chatTarget",
		name = "Show in",
		description = "Which chatbox tab stream messages appear in.",
		position = 0,
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
		position = 1,
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
		position = 2,
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
		position = 3,
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
		position = 4,
		section = displaySection
	)
	default int maxMessageLength()
	{
		return 180;
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
		keyName = "blockedUsers",
		name = "Blocked users",
		description = "Comma separated names to hide entirely. Case insensitive. Useful for chat bots.",
		position = 4,
		section = filterSection
	)
	default String blockedUsers()
	{
		return "";
	}

	@ConfigItem(
		keyName = "blockedWords",
		name = "Blocked words",
		description = "Comma separated. Any message containing one of these is hidden. Case insensitive.",
		position = 5,
		section = filterSection
	)
	default String blockedWords()
	{
		return "";
	}
}
