package com.streamchat;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A player emote animation that can be played when a stream event lands.
 *
 * <p><b>This is client-side only.</b> The animation is written straight onto the local player for
 * rendering; no input and no packet is sent to the game, so nobody else sees it and nothing happens
 * server-side. That distinction is the whole reason this is acceptable at all -- actually
 * performing an emote would mean simulating input, which breaks Jagex's third-party client rules.
 */
@Getter
@RequiredArgsConstructor
public enum EmoteAnimation
{
	NONE("None", -1),
	DANCE("Dance", 866),
	JIG("Jig", 2106),
	SPIN("Spin", 2107),
	HEADBANG("Head bang", 2108),
	JUMP_FOR_JOY("Jump for joy", 2109),
	CHEER("Cheer", 862),
	CLAP("Clap", 865),
	WAVE("Wave", 863),
	BOW("Bow", 858),
	SALUTE("Salute", 2112),
	YES("Yes", 855),
	LAUGH("Laugh", 861),
	BLOW_KISS("Blow kiss", 1368),
	RASPBERRY("Raspberry", 2110),
	PANIC("Panic", 2105);

	private final String displayName;
	private final int animationId;

	@Override
	public String toString()
	{
		return displayName;
	}
}
