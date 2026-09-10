package com.streamchat.twitch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TwitchEmoteOnlyTest
{
	@Test
	public void singleEmoteOnly()
	{
		// "Kappa" is chars 0-4, and that is the whole message.
		assertTrue(TwitchChatSource.isEmoteOnly("Kappa", "25:0-4"));
	}

	@Test
	public void severalEmotesAndNothingElse()
	{
		assertTrue(TwitchChatSource.isEmoteOnly("Kappa Kappa", "25:0-4,6-10"));
		assertTrue(TwitchChatSource.isEmoteOnly("Kappa PogChamp", "25:0-4/88:6-13"));
	}

	@Test
	public void emoteWithRealWordsIsNotEmoteOnly()
	{
		assertFalse(TwitchChatSource.isEmoteOnly("lol Kappa", "25:4-8"));
		assertFalse(TwitchChatSource.isEmoteOnly("Kappa nice", "25:0-4"));
	}

	@Test
	public void plainTextIsNotEmoteOnly()
	{
		assertFalse(TwitchChatSource.isEmoteOnly("hello there", ""));
		assertFalse(TwitchChatSource.isEmoteOnly("hello there", null));
	}

	@Test
	public void thirdPartyEmotesAreTreatedAsText()
	{
		// BTTV/7TV emotes never appear in the emotes tag, so this must not be hidden -- erring
		// towards showing a message rather than silently dropping one.
		assertFalse(TwitchChatSource.isEmoteOnly("catJAM", ""));
	}

	@Test
	public void malformedTagDoesNotHide()
	{
		assertFalse(TwitchChatSource.isEmoteOnly("Kappa", "25:notanumber"));
		assertFalse(TwitchChatSource.isEmoteOnly("Kappa", "25"));
	}

	@Test
	public void outOfBoundsRangeIsClamped()
	{
		// Twitch indexes by code point, so a message with surrogate pairs can report a range past
		// the end. That must not throw.
		assertTrue(TwitchChatSource.isEmoteOnly("Kappa", "25:0-999"));
	}

	@Test
	public void emptyMessage()
	{
		assertFalse(TwitchChatSource.isEmoteOnly("", "25:0-4"));
		assertFalse(TwitchChatSource.isEmoteOnly(null, "25:0-4"));
	}
}
