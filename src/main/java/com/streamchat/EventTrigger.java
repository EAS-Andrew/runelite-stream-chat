package com.streamchat;

import lombok.RequiredArgsConstructor;

/** Which events are worth reacting to, as opposed to merely showing. */
@RequiredArgsConstructor
public enum EventTrigger
{
	ALL("Every event"),
	SUBS_AND_GIFTS("Subs, gifts and raids"),
	SUBS_ONLY("Subs only");

	private final String displayName;

	public boolean matches(StreamEventType type)
	{
		if (type == null)
		{
			return false;
		}

		switch (this)
		{
			case SUBS_ONLY:
				return type == StreamEventType.SUBSCRIPTION;
			case SUBS_AND_GIFTS:
				return type == StreamEventType.SUBSCRIPTION
					|| type == StreamEventType.GIFT
					|| type == StreamEventType.RAID;
			default:
				return true;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
