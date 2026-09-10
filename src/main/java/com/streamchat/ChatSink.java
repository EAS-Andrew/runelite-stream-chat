package com.streamchat;

/**
 * Where a {@link ChatSource} delivers its output.
 *
 * <p>Implementations must be thread-safe: sources call these from OkHttp dispatcher threads and
 * from the plugin's scheduled executor, never from the client thread.
 */
public interface ChatSink
{
	void onMessage(StreamChatMessage message);

	void onStatus(StreamPlatform platform, SourceStatus status, String detail);
}
