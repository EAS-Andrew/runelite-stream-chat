package com.streamchat.youtube;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class YouTubeTargetTest
{
	@Test
	public void parsesBareVideoId()
	{
		final YouTubeTarget t = YouTubeTarget.parse("dQw4w9WgXcQ");

		assertEquals(YouTubeTarget.Kind.VIDEO, t.getKind());
		assertEquals("dQw4w9WgXcQ", t.getValue());
		assertFalse(t.isExpensiveToResolve());
	}

	@Test
	public void parsesWatchUrl()
	{
		assertEquals("dQw4w9WgXcQ",
			YouTubeTarget.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ").getValue());
		assertEquals("dQw4w9WgXcQ",
			YouTubeTarget.parse("https://www.youtube.com/watch?list=x&v=dQw4w9WgXcQ").getValue());
	}

	@Test
	public void parsesLiveAndShortUrls()
	{
		assertEquals("dQw4w9WgXcQ", YouTubeTarget.parse("https://youtu.be/dQw4w9WgXcQ").getValue());
		assertEquals("dQw4w9WgXcQ",
			YouTubeTarget.parse("https://www.youtube.com/live/dQw4w9WgXcQ").getValue());
	}

	@Test
	public void parsesChannelId()
	{
		final YouTubeTarget t = YouTubeTarget.parse("UC_x5XG1OV2P6uZZ5FSM9Ttw");

		assertEquals(YouTubeTarget.Kind.CHANNEL_ID, t.getKind());
		assertTrue(t.isExpensiveToResolve());
	}

	@Test
	public void parsesHandle()
	{
		final YouTubeTarget t = YouTubeTarget.parse("@SomeCreator");

		assertEquals(YouTubeTarget.Kind.HANDLE, t.getKind());
		assertEquals("SomeCreator", t.getValue());
		assertTrue(t.isExpensiveToResolve());

		assertEquals("SomeCreator", YouTubeTarget.parse("https://youtube.com/@SomeCreator").getValue());
	}

	@Test
	public void channelIdIsNotMistakenForAVideoId()
	{
		// Both are made of the same character class, so length is what separates them.
		final YouTubeTarget t = YouTubeTarget.parse("https://www.youtube.com/channel/UC_x5XG1OV2P6uZZ5FSM9Ttw");

		assertEquals(YouTubeTarget.Kind.CHANNEL_ID, t.getKind());
		assertEquals("UC_x5XG1OV2P6uZZ5FSM9Ttw", t.getValue());
	}

	@Test
	public void rejectsJunk()
	{
		assertNull(YouTubeTarget.parse(null));
		assertNull(YouTubeTarget.parse(""));
		assertNull(YouTubeTarget.parse("   "));
		assertNull(YouTubeTarget.parse("not a valid id"));
		assertNull(YouTubeTarget.parse("https://www.youtube.com/results?search_query=x"));
	}

	@Test
	public void trimsSurroundingWhitespace()
	{
		assertEquals("dQw4w9WgXcQ", YouTubeTarget.parse("  dQw4w9WgXcQ  ").getValue());
	}
}
