package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.SoundEffectID;

/** Sound played when a stream event lands. */
@Getter
@RequiredArgsConstructor
public enum EventSound
{
	NONE("None", -1),
	DINGALING("Ding", SoundEffectID.GE_ADD_OFFER_DINGALING),
	COIN_TINKLE("Coins", SoundEffectID.GE_COIN_TINKLE),
	PLOP("Plop", SoundEffectID.GE_INCREMENT_PLOP),
	BOOP("Boop", SoundEffectID.UI_BOOP),
	TELEPORT("Teleport", SoundEffectID.TELEPORT_VWOOP),
	COLLECT("Collect", SoundEffectID.GE_COLLECT_BLOOP);

	private final String displayName;
	private final int soundId;

	@Override
	public String toString()
	{
		return displayName;
	}
}
