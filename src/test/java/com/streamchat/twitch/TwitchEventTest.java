package com.streamchat.twitch;

import com.streamchat.StreamEventType;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TwitchEventTest
{
	@Test
	public void subVariantsAreSubscriptions()
	{
		assertEquals(StreamEventType.SUBSCRIPTION, TwitchChatSource.classifyUserNotice("sub"));
		assertEquals(StreamEventType.SUBSCRIPTION, TwitchChatSource.classifyUserNotice("resub"));
		assertEquals(StreamEventType.SUBSCRIPTION, TwitchChatSource.classifyUserNotice("primepaidupgrade"));
	}

	@Test
	public void giftVariantsAreGifts()
	{
		assertEquals(StreamEventType.GIFT, TwitchChatSource.classifyUserNotice("subgift"));
		assertEquals(StreamEventType.GIFT, TwitchChatSource.classifyUserNotice("anonsubgift"));
		assertEquals(StreamEventType.GIFT, TwitchChatSource.classifyUserNotice("submysterygift"));
	}

	@Test
	public void raidIsARaid()
	{
		assertEquals(StreamEventType.RAID, TwitchChatSource.classifyUserNotice("raid"));
	}

	@Test
	public void unknownNoticesStillCountAsEvents()
	{
		// Twitch adds msg-ids over time; an unrecognised one should still be shown, not dropped.
		assertEquals(StreamEventType.OTHER, TwitchChatSource.classifyUserNotice("somethingnew"));
		assertEquals(StreamEventType.OTHER, TwitchChatSource.classifyUserNotice(null));
	}
}
