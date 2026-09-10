package com.streamchat;

import java.awt.Color;
import javax.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

/**
 * A single chat message, normalised across every platform.
 *
 * <p>All fields are raw, untrusted, remote text. Escaping and truncation happen in
 * {@link MessageRouter} immediately before the message reaches the chatbox -- never in the
 * sources -- so there is exactly one place that has to be right.
 */
@Value
@Builder
public class StreamChatMessage
{
	StreamPlatform platform;

	/** The channel/room this arrived from, for display when more than one is configured. */
	String channel;

	String author;

	String message;

	/** Author name colour supplied by the platform, or null to use the platform default. */
	@Nullable
	Color authorColor;

	/**
	 * Platform-assigned message id, used for de-duplication. Falls back to a synthetic id when the
	 * platform does not supply one.
	 */
	String id;
}
