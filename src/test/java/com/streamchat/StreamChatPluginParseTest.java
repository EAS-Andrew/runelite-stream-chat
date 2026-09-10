package com.streamchat;

import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class StreamChatPluginParseTest
{
	@Test
	public void splitsAndNormalisesChannels()
	{
		assertEquals(Arrays.asList("odablock", "settled"),
			StreamChatPlugin.channelList("Odablock, Settled"));
	}

	@Test
	public void toleratesUrlsAndSigils()
	{
		assertEquals(Arrays.asList("odablock", "settled", "b0aty"),
			StreamChatPlugin.channelList("https://twitch.tv/Odablock, #settled, @B0aty"));
	}

	@Test
	public void dropsBlanksAndDuplicates()
	{
		assertEquals(Arrays.asList("a", "b"), StreamChatPlugin.channelList("a, ,b,,A"));
		assertTrue(StreamChatPlugin.channelList("").isEmpty());
		assertTrue(StreamChatPlugin.channelList(null).isEmpty());
	}

	@Test
	public void parsesChatroomOverrides()
	{
		final Map<Long, String> out = StreamChatPlugin.parseChatroomIds("trainwreckstv:123456, Other:789");

		assertEquals(2, out.size());
		assertEquals("trainwreckstv", out.get(123456L));
		assertEquals("other", out.get(789L));
	}

	@Test
	public void ignoresMalformedChatroomOverrides()
	{
		assertTrue(StreamChatPlugin.parseChatroomIds("nocolon").isEmpty());
		assertTrue(StreamChatPlugin.parseChatroomIds("slug:").isEmpty());
		assertTrue(StreamChatPlugin.parseChatroomIds(":123").isEmpty());
		assertTrue(StreamChatPlugin.parseChatroomIds("slug:notanumber").isEmpty());
		assertTrue(StreamChatPlugin.parseChatroomIds(null).isEmpty());
	}
}
