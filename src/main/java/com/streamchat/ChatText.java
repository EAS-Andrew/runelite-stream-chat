package com.streamchat;

import net.runelite.client.util.Text;

/**
 * Turns arbitrary remote chat text into something safe to hand to the chatbox.
 *
 * <p>This is the security boundary of the plugin. Everything printed originates from strangers on
 * the internet, and the game's chat renderer treats {@code <...>} as markup: an unescaped message
 * could inject {@code <img=n>} to fake a mod crown, {@code <col=...>} to recolour the rest of the
 * line, or {@code <br>} to break the layout. So text goes through {@link #clean} here, and the
 * result is then escaped with {@link Text#escapeJagex} before it reaches the chatbox -- never one
 * without the other.
 *
 * <p>Order matters: truncation happens <em>before</em> escaping, so a cut can never land inside a
 * generated {@code <lt>} and leave a malformed tag behind.
 */
public final class ChatText
{
	private static final String ELLIPSIS = "...";

	private ChatText()
	{
	}

	/**
	 * Collapses whitespace, drops characters the game font cannot render, and truncates.
	 *
	 * <p>The result is <em>not</em> escaped; callers must still pass it through
	 * {@link Text#escapeJagex} (directly, or via {@code ChatMessageBuilder.append(String)}).
	 *
	 * @param raw    remote text, may be null
	 * @param maxLen maximum length before truncation, in characters
	 */
	public static String clean(String raw, int maxLen)
	{
		if (raw == null || raw.isEmpty())
		{
			return "";
		}

		// Newlines and tabs would either break the line or be turned into <br> by escapeJagex.
		// Collapse all whitespace runs to a single space first.
		final StringBuilder sb = new StringBuilder(raw.length());
		boolean lastWasSpace = false;
		for (int i = 0; i < raw.length(); i++)
		{
			final char c = raw.charAt(i);
			// \u00A0 is a non-breaking space. Twitch clients append one to let a user repeat an
			// identical message, and the printable matcher below would otherwise keep it.
			if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\u00A0')
			{
				if (!lastWasSpace && sb.length() > 0)
				{
					sb.append(' ');
					lastWasSpace = true;
				}
				continue;
			}

			sb.append(c);
			lastWasSpace = false;
		}

		// Drop anything the RuneScape font cannot draw -- emoji, control characters, combining
		// marks. Left in, these render as garbage boxes or shift the line.
		String out = Text.JAGEX_PRINTABLE_CHAR_MATCHER.retainFrom(sb.toString()).trim();

		if (maxLen > 0 && out.length() > maxLen)
		{
			final int cut = Math.max(0, maxLen - ELLIPSIS.length());
			out = out.substring(0, cut).trim() + ELLIPSIS;
		}

		return out;
	}
}
