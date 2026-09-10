package com.streamchat.twitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A parsed IRCv3 line, as sent by the Twitch chat gateway.
 *
 * <p>Wire format is {@code [@tags] [:prefix] COMMAND [params...] [:trailing]}. Only the subset
 * Twitch actually emits is handled, but tag unescaping follows the IRCv3 message-tags spec in full
 * because display names and message bodies travel through tags on some events.
 */
public final class IrcMessage
{
	private final Map<String, String> tags;
	private final String prefix;
	private final String command;
	private final List<String> params;

	private IrcMessage(Map<String, String> tags, String prefix, String command, List<String> params)
	{
		this.tags = tags;
		this.prefix = prefix;
		this.command = command;
		this.params = params;
	}

	/**
	 * Parses one line. Returns null for blank lines or lines with no command, which the gateway
	 * does occasionally send.
	 */
	@Nullable
	public static IrcMessage parse(String line)
	{
		if (line == null)
		{
			return null;
		}

		// Trailing CR from the CRLF framing.
		int end = line.length();
		while (end > 0 && (line.charAt(end - 1) == '\r' || line.charAt(end - 1) == '\n'))
		{
			--end;
		}

		int pos = 0;
		while (pos < end && line.charAt(pos) == ' ')
		{
			++pos;
		}

		if (pos >= end)
		{
			return null;
		}

		Map<String, String> tags = Collections.emptyMap();
		if (line.charAt(pos) == '@')
		{
			final int sp = indexOf(line, ' ', pos, end);
			final int tagsEnd = sp == -1 ? end : sp;
			tags = parseTags(line.substring(pos + 1, tagsEnd));
			pos = sp == -1 ? end : sp + 1;
			pos = skipSpaces(line, pos, end);
		}

		String prefix = null;
		if (pos < end && line.charAt(pos) == ':')
		{
			final int sp = indexOf(line, ' ', pos, end);
			final int prefixEnd = sp == -1 ? end : sp;
			prefix = line.substring(pos + 1, prefixEnd);
			pos = sp == -1 ? end : sp + 1;
			pos = skipSpaces(line, pos, end);
		}

		if (pos >= end)
		{
			return null;
		}

		final int cmdSp = indexOf(line, ' ', pos, end);
		final int cmdEnd = cmdSp == -1 ? end : cmdSp;
		final String command = line.substring(pos, cmdEnd);
		pos = cmdSp == -1 ? end : cmdSp + 1;

		final List<String> params = new ArrayList<>(4);
		while (pos < end)
		{
			pos = skipSpaces(line, pos, end);
			if (pos >= end)
			{
				break;
			}

			if (line.charAt(pos) == ':')
			{
				// Trailing parameter: everything left, spaces included.
				params.add(line.substring(pos + 1, end));
				break;
			}

			final int sp = indexOf(line, ' ', pos, end);
			final int paramEnd = sp == -1 ? end : sp;
			params.add(line.substring(pos, paramEnd));
			pos = sp == -1 ? end : sp + 1;
		}

		return new IrcMessage(tags, prefix, command, params);
	}

	private static Map<String, String> parseTags(String raw)
	{
		if (raw.isEmpty())
		{
			return Collections.emptyMap();
		}

		final Map<String, String> out = new LinkedHashMap<>();
		int pos = 0;
		final int len = raw.length();

		while (pos <= len)
		{
			int semi = raw.indexOf(';', pos);
			if (semi == -1)
			{
				semi = len;
			}

			final String pair = raw.substring(pos, semi);
			if (!pair.isEmpty())
			{
				final int eq = pair.indexOf('=');
				if (eq == -1)
				{
					out.put(pair, "");
				}
				else
				{
					out.put(pair.substring(0, eq), unescapeTagValue(pair.substring(eq + 1)));
				}
			}

			pos = semi + 1;
			if (semi == len)
			{
				break;
			}
		}

		return out;
	}

	/** IRCv3 message-tags escaping: {@code \: ; \s space \\ backslash \r CR \n LF}. */
	static String unescapeTagValue(String value)
	{
		if (value.indexOf('\\') == -1)
		{
			return value;
		}

		final StringBuilder out = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++)
		{
			final char c = value.charAt(i);
			if (c != '\\')
			{
				out.append(c);
				continue;
			}

			if (i + 1 >= value.length())
			{
				// A lone trailing backslash is dropped, per spec.
				break;
			}

			final char next = value.charAt(++i);
			switch (next)
			{
				case ':':
					out.append(';');
					break;
				case 's':
					out.append(' ');
					break;
				case '\\':
					out.append('\\');
					break;
				case 'r':
					out.append('\r');
					break;
				case 'n':
					out.append('\n');
					break;
				default:
					// Unrecognised escape: the escaping backslash is dropped.
					out.append(next);
					break;
			}
		}

		return out.toString();
	}

	private static int skipSpaces(String s, int pos, int end)
	{
		while (pos < end && s.charAt(pos) == ' ')
		{
			++pos;
		}
		return pos;
	}

	private static int indexOf(String s, char c, int from, int end)
	{
		for (int i = from; i < end; i++)
		{
			if (s.charAt(i) == c)
			{
				return i;
			}
		}
		return -1;
	}

	public String getCommand()
	{
		return command;
	}

	public Map<String, String> getTags()
	{
		return tags;
	}

	/** Tag value, or null when absent or empty. Twitch sends empty tags rather than omitting them. */
	@Nullable
	public String tag(String key)
	{
		final String v = tags.get(key);
		return v == null || v.isEmpty() ? null : v;
	}

	@Nullable
	public String getPrefix()
	{
		return prefix;
	}

	/** Nickname from the prefix, i.e. the part before {@code !}. */
	@Nullable
	public String nick()
	{
		if (prefix == null)
		{
			return null;
		}

		final int bang = prefix.indexOf('!');
		return bang == -1 ? prefix : prefix.substring(0, bang);
	}

	public List<String> getParams()
	{
		return params;
	}

	@Nullable
	public String param(int index)
	{
		return index >= 0 && index < params.size() ? params.get(index) : null;
	}

	/** Last parameter, which for PRIVMSG is the message body. */
	@Nullable
	public String trailing()
	{
		return params.isEmpty() ? null : params.get(params.size() - 1);
	}
}
