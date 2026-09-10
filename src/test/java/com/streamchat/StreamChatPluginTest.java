package com.streamchat;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev launcher. Starts a real RuneLite client with this plugin loaded as a builtin:
 * <pre>./gradlew runClient</pre>
 */
public class StreamChatPluginTest
{
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(StreamChatPlugin.class);
		RuneLite.main(args);
	}
}
