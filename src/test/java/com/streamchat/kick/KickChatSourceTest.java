package com.streamchat.kick;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class KickChatSourceTest
{
	@Test
	public void reducesEmoteMarkupToTheEmoteName()
	{
		assertEquals("KEKW", KickChatSource.stripEmoteMarkup("[emote:37226:KEKW]"));
		assertEquals("lol KEKW nice",
			KickChatSource.stripEmoteMarkup("lol [emote:37226:KEKW] nice"));
		assertEquals("KEKW KEKW",
			KickChatSource.stripEmoteMarkup("[emote:37226:KEKW] [emote:37226:KEKW]"));
	}

	@Test
	public void leavesOrdinaryTextAlone()
	{
		assertEquals("hello world", KickChatSource.stripEmoteMarkup("hello world"));
		assertEquals("array[0] is fine", KickChatSource.stripEmoteMarkup("array[0] is fine"));
		assertEquals("", KickChatSource.stripEmoteMarkup(""));
	}

	@Test
	public void leavesMalformedEmoteMarkupAlone()
	{
		// No id, so it is not emote markup and should be shown as typed.
		assertEquals("[emote:KEKW]", KickChatSource.stripEmoteMarkup("[emote:KEKW]"));
		assertEquals("[emote:123", KickChatSource.stripEmoteMarkup("[emote:123"));
	}

	@Test
	public void emoteOnlyMessageBecomesJustTheName()
	{
		// Trimmed, so an emote-only message does not arrive as leading/trailing whitespace.
		assertEquals("KEKW", KickChatSource.stripEmoteMarkup("  [emote:1:KEKW]  "));
	}

	@Test
	public void detectsEmoteOnlyMessages()
	{
		assertTrue(KickChatSource.isEmoteOnly("[emote:37226:KEKW]"));
		assertTrue(KickChatSource.isEmoteOnly("[emote:1:A] [emote:2:B]"));
		assertTrue(KickChatSource.isEmoteOnly("  [emote:1:A]  "));
	}

	@Test
	public void messagesWithWordsAreNotEmoteOnly()
	{
		assertFalse(KickChatSource.isEmoteOnly("lol [emote:37226:KEKW]"));
		assertFalse(KickChatSource.isEmoteOnly("hello"));
		assertFalse(KickChatSource.isEmoteOnly(""));
		assertFalse(KickChatSource.isEmoteOnly(null));
	}

	@Test
	public void parsesIdentityColors()
	{
		assertEquals(new Color(0xFF5733), KickChatSource.parseColor("#FF5733"));
		assertNull(KickChatSource.parseColor(null));
		assertNull(KickChatSource.parseColor("FF5733"));
		assertNull(KickChatSource.parseColor("#ZZZZZZ"));
	}
}
