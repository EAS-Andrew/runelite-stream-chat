package com.streamchat;

/**
 * A live chat feed for one platform.
 *
 * <p>{@link #start()} and {@link #stop()} are idempotent and safe to call from any thread. A source
 * owns its own reconnection: once started it keeps trying until stopped or until it hits an error
 * that retrying cannot fix.
 */
public interface ChatSource
{
	StreamPlatform platform();

	void start();

	void stop();

	SourceStatus status();

	/** Human-readable detail for the current status, e.g. an error message. Never null. */
	String statusDetail();
}
