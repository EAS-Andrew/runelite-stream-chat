package com.streamchat.twitch;

import java.awt.Color;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IrcMessageTest
{
	@Test
	public void parsesTaggedPrivmsg()
	{
		final String line = "@badge-info=;badges=broadcaster/1;color=#1E90FF;display-name=SomeUser;"
			+ "emotes=;id=abc-123;mod=0 :someuser!someuser@someuser.tmi.twitch.tv PRIVMSG #channel :hello world";

		final IrcMessage msg = IrcMessage.parse(line);

		assertEquals("PRIVMSG", msg.getCommand());
		assertEquals("someuser", msg.nick());
		assertEquals("#channel", msg.param(0));
		assertEquals("hello world", msg.trailing());
		assertEquals("SomeUser", msg.tag("display-name"));
		assertEquals("abc-123", msg.tag("id"));
		assertEquals("#1E90FF", msg.tag("color"));
	}

	@Test
	public void emptyTagsReadAsNull()
	{
		// Twitch sends the key with no value rather than omitting it for users with no colour set.
		final IrcMessage msg = IrcMessage.parse("@color=;id=x :a!a@a PRIVMSG #c :hi");

		assertNull(msg.tag("color"));
		assertEquals("x", msg.tag("id"));
	}

	@Test
	public void parsesPingWithoutPrefix()
	{
		final IrcMessage msg = IrcMessage.parse("PING :tmi.twitch.tv");

		assertEquals("PING", msg.getCommand());
		assertNull(msg.getPrefix());
		assertEquals("tmi.twitch.tv", msg.trailing());
	}

	@Test
	public void trailingKeepsColonsAndSpaces()
	{
		final IrcMessage msg = IrcMessage.parse(":a!a@a PRIVMSG #c :check this: http://example.com/a b");

		assertEquals("check this: http://example.com/a b", msg.trailing());
	}

	@Test
	public void unescapesTagValues()
	{
		// \s space, \: semicolon, \\ backslash, per the IRCv3 message-tags spec.
		assertEquals("a b", IrcMessage.unescapeTagValue("a\\sb"));
		assertEquals("a;b", IrcMessage.unescapeTagValue("a\\:b"));
		assertEquals("a\\b", IrcMessage.unescapeTagValue("a\\\\b"));
		assertEquals("SomeUser subscribed for 3 months!",
			IrcMessage.unescapeTagValue("SomeUser\\ssubscribed\\sfor\\s3\\smonths!"));
	}

	@Test
	public void unescapeDropsLoneTrailingBackslash()
	{
		assertEquals("ab", IrcMessage.unescapeTagValue("ab\\"));
	}

	@Test
	public void handlesCrLfAndBlankLines()
	{
		assertNull(IrcMessage.parse(""));
		assertNull(IrcMessage.parse("   "));
		assertNull(IrcMessage.parse(null));

		final IrcMessage msg = IrcMessage.parse("PING :x\r\n");
		assertEquals("PING", msg.getCommand());
		assertEquals("x", msg.trailing());
	}

	@Test
	public void parsesNumericWelcome()
	{
		final IrcMessage msg = IrcMessage.parse(":tmi.twitch.tv 001 justinfan12345 :Welcome, GLHF!");

		assertEquals("001", msg.getCommand());
		assertEquals("justinfan12345", msg.param(0));
	}

	@Test
	public void parsesColors()
	{
		assertEquals(new Color(0x1E90FF), TwitchChatSource.parseColor("#1E90FF"));
		assertNull(TwitchChatSource.parseColor(null));
		assertNull(TwitchChatSource.parseColor(""));
		assertNull(TwitchChatSource.parseColor("1E90FF"));
		assertNull(TwitchChatSource.parseColor("#GGGGGG"));
	}

	@Test
	public void parsesUserNoticeSystemMessage()
	{
		final String line = "@msg-id=resub;system-msg=SomeUser\\ssubscribed\\sfor\\s3\\smonths!;id=n1 "
			+ ":tmi.twitch.tv USERNOTICE #channel :great stream";

		final IrcMessage msg = IrcMessage.parse(line);

		assertEquals("USERNOTICE", msg.getCommand());
		assertEquals("SomeUser subscribed for 3 months!", msg.tag("system-msg"));
		assertTrue(msg.getTags().containsKey("msg-id"));
	}
}
