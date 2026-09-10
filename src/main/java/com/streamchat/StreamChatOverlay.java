package com.streamchat;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws recent stream chat as a movable on-screen panel, for people who would rather not have it
 * in the chatbox at all.
 *
 * <p>Text here is cleaned but <em>not</em> escaped: escaping exists to neutralise the game's chat
 * markup, and this draws with Java2D, where {@code <lt>} would simply appear as those characters.
 * {@link ChatText#clean} still runs, so the font cannot be handed anything it cannot render.
 */
@Singleton
public class StreamChatOverlay extends Overlay
{
	private static final int PADDING = 4;
	private static final int ICON_SIZE = 11;
	private static final int ICON_GAP = 3;
	private static final int LINE_GAP = 2;
	private static final Color BORDER_COLOR = new Color(0, 0, 0, 160);

	private final StreamChatConfig config;
	private final ChatIcons icons;

	/** Newest last. Access is confined to the client thread: appended on flush, read on render. */
	private final Deque<StreamChatMessage> recent = new ArrayDeque<>();

	@Inject
	private StreamChatOverlay(StreamChatConfig config, ChatIcons icons)
	{
		this.config = config;
		this.icons = icons;

		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setMovable(true);
		setResizable(true);
		setSnappable(true);
	}

	void add(StreamChatMessage message)
	{
		recent.addLast(message);
		trim();
	}

	void clear()
	{
		recent.clear();
	}

	private void trim()
	{
		final int max = Math.max(1, config.overlayLines());
		while (recent.size() > max)
		{
			recent.pollFirst();
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (recent.isEmpty())
		{
			return null;
		}

		trim();

		final Font font = config.overlayFont().font();
		if (font != null)
		{
			graphics.setFont(font);
		}

		// On resize RuneLite stores BOTH dimensions here, so honouring only the width would throw
		// away a dragged height on the very next frame.
		final Dimension preferred = getPreferredSize();
		final int width = preferred != null && preferred.width > 0
			? preferred.width
			: Math.max(120, config.overlayWidth());
		final int fixedHeight = preferred != null ? preferred.height : 0;

		final FontMetrics metrics = graphics.getFontMetrics();
		final boolean showIcon = config.showIcon();
		final int lineHeight = metrics.getHeight() + LINE_GAP;

		int textWidth = width - (PADDING * 2);
		if (showIcon)
		{
			textWidth -= ICON_SIZE + ICON_GAP;
		}
		textWidth = Math.max(40, textWidth);

		// Lay out newest-first so that a fixed height can stop as soon as the box is full, then
		// flip back to reading order for drawing.
		final List<Row> rows = new ArrayList<>();
		int contentHeight = 0;
		final int budget = fixedHeight > 0 ? fixedHeight - (PADDING * 2) + LINE_GAP : Integer.MAX_VALUE;

		final List<StreamChatMessage> newestFirst = new ArrayList<>(recent);
		for (int i = newestFirst.size() - 1; i >= 0; i--)
		{
			final StreamChatMessage message = newestFirst.get(i);

			final String author = ChatText.clean(message.getAuthor(), 32);
			final String body = ChatText.clean(message.getMessage(), config.maxMessageLength());
			if (body.isEmpty())
			{
				continue;
			}

			final String text = author.isEmpty() ? body : author + ": " + body;
			final Row row = new Row(message, author, wrap(text, metrics, textWidth));
			final int rowHeight = row.lines.size() * lineHeight;

			if (!rows.isEmpty() && contentHeight + rowHeight > budget)
			{
				// The panel is full; older messages scroll off the top.
				break;
			}

			rows.add(0, row);
			contentHeight += rowHeight;
		}

		if (rows.isEmpty())
		{
			return null;
		}

		final int height = fixedHeight > 0 ? fixedHeight : contentHeight + (PADDING * 2) - LINE_GAP;

		final Color background = config.overlayBackgroundColor();
		if (background.getAlpha() > 0)
		{
			graphics.setColor(background);
			graphics.fillRect(0, 0, width, height);
		}

		if (config.overlayBorder())
		{
			// Fixed colour rather than one derived from the background, which is invisible when
			// the background is transparent -- the default.
			graphics.setColor(BORDER_COLOR);
			graphics.drawRect(0, 0, width - 1, height - 1);
		}

		// Anchor to the bottom so the newest message sits at the bottom edge, the way a chat window
		// behaves, rather than leaving the gap there.
		int y = PADDING + metrics.getAscent();
		if (fixedHeight > 0)
		{
			y = Math.max(y, height - PADDING + LINE_GAP - contentHeight + metrics.getAscent());
		}

		final Color textColor = config.overlayTextColor();
		final boolean shadow = config.overlayShadow();
		final Rectangle clip = graphics.getClipBounds();
		graphics.clipRect(0, 0, width, height);

		for (Row row : rows)
		{
			int x = PADDING;

			if (showIcon)
			{
				final BufferedImage icon = icons.image(row.message.getPlatform());
				if (icon != null)
				{
					// Sit the icon on the text baseline rather than the line box, so it lines up
					// with the glyphs instead of floating.
					graphics.drawImage(icon, x, y - icon.getHeight() + 1, null);
				}
				x += ICON_SIZE + ICON_GAP;
			}

			final Color authorColor = config.colorAuthors()
				? MessageRouter.readable(row.message.getAuthorColor(), row.message.getPlatform())
				: row.message.getPlatform().getDefaultAuthorColor();

			for (int i = 0; i < row.lines.size(); i++)
			{
				final String line = row.lines.get(i);

				if (i == 0 && !row.author.isEmpty())
				{
					final String prefix = row.author + ":";
					draw(graphics, prefix, x, y, authorColor, shadow);

					final int offset = metrics.stringWidth(prefix + " ");
					final int from = Math.min(line.length(), prefix.length() + 1);
					draw(graphics, line.substring(from), x + offset, y, textColor, shadow);
				}
				else
				{
					draw(graphics, line, x, y, textColor, shadow);
				}

				y += lineHeight;
			}
		}

		graphics.setClip(clip);

		return new Dimension(width, height);
	}

	private static void draw(Graphics2D graphics, String text, int x, int y, Color color, boolean shadow)
	{
		if (shadow)
		{
			graphics.setColor(Color.BLACK);
			graphics.drawString(text, x + 1, y + 1);
		}
		graphics.setColor(color);
		graphics.drawString(text, x, y);
	}

	/** Greedy word wrap, falling back to a hard break for a single word wider than the panel. */
	static List<String> wrap(String text, FontMetrics metrics, int maxWidth)
	{
		final List<String> lines = new ArrayList<>();
		final StringBuilder line = new StringBuilder();

		for (String word : text.split(" "))
		{
			if (word.isEmpty())
			{
				continue;
			}

			final String candidate = line.length() == 0 ? word : line + " " + word;
			if (metrics.stringWidth(candidate) <= maxWidth)
			{
				line.setLength(0);
				line.append(candidate);
				continue;
			}

			if (line.length() > 0)
			{
				lines.add(line.toString());
				line.setLength(0);
			}

			// A word too long to fit on its own line has to be split mid-word.
			String rest = word;
			while (metrics.stringWidth(rest) > maxWidth && rest.length() > 1)
			{
				int cut = rest.length();
				while (cut > 1 && metrics.stringWidth(rest.substring(0, cut)) > maxWidth)
				{
					--cut;
				}
				lines.add(rest.substring(0, cut));
				rest = rest.substring(cut);
			}
			line.append(rest);
		}

		if (line.length() > 0)
		{
			lines.add(line.toString());
		}

		return lines;
	}

	private static final class Row
	{
		final StreamChatMessage message;
		final String author;
		final List<String> lines;

		Row(StreamChatMessage message, String author, List<String> lines)
		{
			this.message = message;
			this.author = author;
			this.lines = lines;
		}
	}
}
