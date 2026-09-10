package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A notable stream event, as opposed to an ordinary chat line.
 *
 * <p>Not every platform reports every kind, so the mapping is best-effort: anything recognised as
 * notable but not neatly categorised lands in {@link #OTHER} rather than being dropped.
 */
@Getter
@RequiredArgsConstructor
public enum StreamEventType
{
	SUBSCRIPTION("Subscription"),
	GIFT("Gifted subs"),
	RAID("Raid / host"),
	CHEER("Bits / cheer"),
	DONATION("Super Chat / donation"),
	OTHER("Other event");

	private final String displayName;

	@Override
	public String toString()
	{
		return displayName;
	}
}
