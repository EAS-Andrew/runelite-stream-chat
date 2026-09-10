package com.streamchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class EventTriggerTest
{
	@Test
	public void triggerScopes()
	{
		assertTrue(EventTrigger.ALL.matches(StreamEventType.CHEER));
		assertTrue(EventTrigger.ALL.matches(StreamEventType.SUBSCRIPTION));

		assertTrue(EventTrigger.SUBS_AND_GIFTS.matches(StreamEventType.SUBSCRIPTION));
		assertTrue(EventTrigger.SUBS_AND_GIFTS.matches(StreamEventType.GIFT));
		assertTrue(EventTrigger.SUBS_AND_GIFTS.matches(StreamEventType.RAID));
		assertFalse(EventTrigger.SUBS_AND_GIFTS.matches(StreamEventType.CHEER));

		assertTrue(EventTrigger.SUBS_ONLY.matches(StreamEventType.SUBSCRIPTION));
		assertFalse(EventTrigger.SUBS_ONLY.matches(StreamEventType.GIFT));
	}

	@Test
	public void ordinaryMessagesNeverTriggerReactions()
	{
		// A null event type is an ordinary chat line, which must never set off a sound or emote.
		for (EventTrigger trigger : EventTrigger.values())
		{
			assertFalse(trigger.toString(), trigger.matches(null));
		}
	}

	@Test
	public void messageKnowsWhetherItIsAnEvent()
	{
		final StreamChatMessage chat = StreamChatMessage.builder()
			.platform(StreamPlatform.TWITCH).channel("c").author("a").message("m").id("1").build();
		final StreamChatMessage event = StreamChatMessage.builder()
			.platform(StreamPlatform.TWITCH).channel("c").author("").message("subbed").id("2")
			.eventType(StreamEventType.SUBSCRIPTION).build();

		assertFalse(chat.isEvent());
		assertTrue(event.isEvent());
	}
}
