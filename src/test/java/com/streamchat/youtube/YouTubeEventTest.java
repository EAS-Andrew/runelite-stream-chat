package com.streamchat.youtube;

import com.streamchat.StreamEventType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class YouTubeEventTest
{
	@Test
	public void membershipsAndSuperChatsAreEvents()
	{
		assertEquals(StreamEventType.SUBSCRIPTION, YouTubeChatSource.classify("newSponsorEvent"));
		assertEquals(StreamEventType.SUBSCRIPTION, YouTubeChatSource.classify("memberMilestoneChatEvent"));
		assertEquals(StreamEventType.GIFT, YouTubeChatSource.classify("membershipGiftingEvent"));
		assertEquals(StreamEventType.DONATION, YouTubeChatSource.classify("superChatEvent"));
		assertEquals(StreamEventType.DONATION, YouTubeChatSource.classify("superStickerEvent"));
	}

	@Test
	public void detectsCustomEmojiOnlyMessages()
	{
		assertTrue(YouTubeChatSource.isEmoteOnly(":yt_laugh:"));
		assertTrue(YouTubeChatSource.isEmoteOnly(":a: :b:"));
	}

	@Test
	public void messagesWithWordsAreNotEmoteOnly()
	{
		assertFalse(YouTubeChatSource.isEmoteOnly("nice :yt_laugh:"));
		assertFalse(YouTubeChatSource.isEmoteOnly("hello"));
		assertFalse(YouTubeChatSource.isEmoteOnly(""));
		assertFalse(YouTubeChatSource.isEmoteOnly(null));
		// A bare colon-word is not a shortcode.
		assertFalse(YouTubeChatSource.isEmoteOnly("time: 5"));
	}

	@Test
	public void ordinaryChatIsNotAnEvent()
	{
		assertNull(YouTubeChatSource.classify("textMessageEvent"));
		assertNull(YouTubeChatSource.classify(null));
		assertNull(YouTubeChatSource.classify("somethingUnknown"));
	}
}
