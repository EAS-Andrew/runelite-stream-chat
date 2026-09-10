package com.streamchat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.client.util.Text;
import org.junit.Test;

/**
 * The sanitising path is the plugin's security boundary, so these tests cover the injection cases
 * explicitly rather than only the happy path.
 */
public class ChatTextTest
{
	private static final int LEN = 180;

	@Test
	public void passesOrdinaryText()
	{
		assertEquals("hello world", ChatText.clean("hello world", LEN));
	}

	@Test
	public void collapsesWhitespaceAndNewlines()
	{
		assertEquals("a b c", ChatText.clean("a\n\nb\t\tc", LEN));
		assertEquals("a b", ChatText.clean("  a     b  ", LEN));
	}

	@Test
	public void stripsNonBreakingSpace()
	{
		// Twitch clients append U+00A0 so a user can repeat an identical message.
		assertEquals("gg", ChatText.clean("gg\u00A0", LEN));
	}

	@Test
	public void stripsUnrenderableCharacters()
	{
		// Emoji and control characters have no glyph in the game font.
		assertEquals("hi", ChatText.clean("hi\uD83D\uDE00", LEN));
		assertEquals("ab", ChatText.clean("a" + (char) 1 + "b", LEN));
		assertEquals("", ChatText.clean("\uD83D\uDE00\uD83D\uDE00", LEN));
	}

	@Test
	public void keepsLatin1Characters()
	{
		// 160-255 are renderable, so accented names survive.
		assertEquals("Jos\u00E9", ChatText.clean("Jos\u00E9", LEN));
	}

	@Test
	public void truncatesWithEllipsis()
	{
		final String out = ChatText.clean(repeat("a", 300), 50);

		assertEquals(50, out.length());
		assertTrue(out.endsWith("..."));
	}

	@Test
	public void escapingNeutralisesTagInjection()
	{
		// A chatter typing an <img> tag must not be able to fake a mod crown in the chatbox.
		final String cleaned = ChatText.clean("<img=1> nice crown", LEN);
		final String escaped = Text.escapeJagex(cleaned);

		assertFalse(escaped.contains("<img="));
		assertEquals("<lt>img=1<gt> nice crown", escaped);
	}

	@Test
	public void escapingNeutralisesColourInjection()
	{
		final String escaped = Text.escapeJagex(ChatText.clean("<col=ff0000>red", LEN));

		assertFalse(escaped.contains("<col="));
		assertEquals("<lt>col=ff0000<gt>red", escaped);
	}

	@Test
	public void truncationCannotSplitAnEscapeSequence()
	{
		// Truncating before escaping is what guarantees this: escaping a cut string can never
		// produce a half-written <lt>.
		for (int limit = 20; limit <= 40; limit++)
		{
			final String escaped = Text.escapeJagex(ChatText.clean(repeat("<", 60), limit));

			assertFalse("limit " + limit + " produced " + escaped, escaped.contains("<l>"));
			assertFalse("limit " + limit + " produced " + escaped, escaped.endsWith("<l"));
			assertFalse("limit " + limit + " produced " + escaped, escaped.endsWith("<"));
		}
	}

	@Test
	public void handlesNullAndEmpty()
	{
		assertEquals("", ChatText.clean(null, LEN));
		assertEquals("", ChatText.clean("", LEN));
		assertEquals("", ChatText.clean("     ", LEN));
	}

	private static String repeat(String s, int n)
	{
		final StringBuilder sb = new StringBuilder(s.length() * n);
		for (int i = 0; i < n; i++)
		{
			sb.append(s);
		}
		return sb.toString();
	}
}
