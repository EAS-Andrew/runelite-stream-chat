package com.streamchat;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.BeforeClass;
import org.junit.Test;

public class StreamChatOverlayTest
{
	private static FontMetrics metrics;

	@BeforeClass
	public static void setUp()
	{
		final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = image.createGraphics();
		metrics = g.getFontMetrics();
		g.dispose();
	}

	@Test
	public void shortTextStaysOnOneLine()
	{
		final List<String> lines = StreamChatOverlay.wrap("hi", metrics, 400);

		assertEquals(1, lines.size());
		assertEquals("hi", lines.get(0));
	}

	@Test
	public void wrapsOnWordBoundaries()
	{
		final String text = "the quick brown fox jumps over the lazy dog again and again";
		final int width = 80;

		final List<String> lines = StreamChatOverlay.wrap(text, metrics, width);

		assertTrue("expected multiple lines, got " + lines, lines.size() > 1);
		for (String line : lines)
		{
			assertTrue("line too wide: " + line, metrics.stringWidth(line) <= width);
		}
		// No text is lost or duplicated.
		assertEquals(text, String.join(" ", lines));
	}

	@Test
	public void hardBreaksAWordWiderThanThePanel()
	{
		// A single unbroken token (a pasted link, or keyboard mashing) cannot be wrapped on spaces,
		// so it has to be split mid-word rather than overflowing the panel.
		final String text = repeat("A", 200);

		final List<String> lines = StreamChatOverlay.wrap(text, metrics, 60);

		assertTrue(lines.size() > 1);
		for (String line : lines)
		{
			assertTrue("line too wide: " + line, metrics.stringWidth(line) <= 60);
		}
		assertEquals(text, String.join("", lines));
	}

	@Test
	public void handlesEmptyAndSpacesOnly()
	{
		assertTrue(StreamChatOverlay.wrap("", metrics, 100).isEmpty());
		assertTrue(StreamChatOverlay.wrap("   ", metrics, 100).isEmpty());
	}

	@Test
	public void collapsesRunsOfSpaces()
	{
		final List<String> lines = StreamChatOverlay.wrap("a    b", metrics, 400);

		assertEquals(1, lines.size());
		assertEquals("a b", lines.get(0));
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
