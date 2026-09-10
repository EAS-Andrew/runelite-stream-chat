package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.SpotanimID;

/**
 * A spotanim (graphic effect) played on the local player when a stream event lands.
 *
 * <p>Like {@link EmoteAnimation} this is client-side rendering only -- the spotanim is written
 * into the local player's own spotanim table, so no packet is sent and no other player sees it.
 */
@Getter
@RequiredArgsConstructor
public enum EventGraphic
{
	NONE("None", -1),
	LEVEL_UP("Level-up fireworks", SpotanimID.LEVELUP_ANIM),
	LEVEL_99("99 fireworks", SpotanimID.LEVELUP_99_ANIM),
	MAX("Max cape fireworks", SpotanimID.LEVELUP_MAX);

	private final String displayName;
	private final int spotAnimId;

	@Override
	public String toString()
	{
		return displayName;
	}
}
