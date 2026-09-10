package com.streamchat;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.client.Notifier;

/**
 * Reacts in-game when a stream event lands.
 *
 * <p><b>Nothing here sends input to the game.</b> That line matters: automating an emote so other
 * players saw it would be input simulation, which breaks Jagex's third-party client rules and is
 * rejected by the Plugin Hub. Every reaction below is client-side presentation only --
 * {@code playSoundEffect} plays locally, {@link Notifier} is a desktop notification, and the emote
 * is written onto the local player's <em>rendered</em> animation, which no packet describes and no
 * other player sees.
 */
@Slf4j
@Singleton
public class EventReactions
{
	/**
	 * Table key for our spotanim. Arbitrary but fixed, and high enough not to collide with the
	 * small ids the game itself uses.
	 */
	private static final int SPOTANIM_KEY = 0x57_43_00;

	private final Client client;
	private final StreamChatConfig config;
	private final Notifier notifier;

	@Inject
	private EventReactions(Client client, StreamChatConfig config, Notifier notifier)
	{
		this.client = client;
		this.config = config;
		this.notifier = notifier;
	}

	/** Called on the client thread as the message is shown. */
	void fire(StreamChatMessage message)
	{
		if (!config.eventTrigger().matches(message.getEventType()))
		{
			return;
		}

		final EventSound sound = config.eventSound();
		if (sound != EventSound.NONE)
		{
			client.playSoundEffect(sound.getSoundId());
		}

		if (config.eventNotify())
		{
			// Cleaned, but there is no markup to escape here -- this goes to the OS, not the chatbox.
			notifier.notify(message.getPlatform().getDisplayName() + ": "
				+ ChatText.clean(message.getMessage(), 120));
		}

		playGraphic(config.eventGraphic());
		playEmote(config.eventAnimation());
	}

	/**
	 * Plays a spotanim on the local player, client-side.
	 *
	 * <p>Unlike the emote this does not replace anything the game is doing, so it is safe to fire
	 * while the player is busy -- the fireworks simply appear over whatever is happening, exactly
	 * as a real level-up does.
	 */
	private void playGraphic(EventGraphic graphic)
	{
		if (graphic == EventGraphic.NONE || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		// A dedicated key, so repeat events replace our own spotanim rather than stacking up, and
		// so we never disturb one the game put there.
		local.removeSpotAnim(SPOTANIM_KEY);
		local.createSpotAnim(SPOTANIM_KEY, graphic.getSpotAnimId(), 0, 0);
	}

	/**
	 * Sets the local player's rendered animation.
	 *
	 * <p>Client-side only, and deliberately not persisted or re-applied: the game overwrites it as
	 * soon as the player does anything, which is the correct behaviour. Fighting that would mean
	 * suppressing real animations, and that starts to look like interfering with gameplay.
	 */
	private void playEmote(EmoteAnimation emote)
	{
		if (emote == EmoteAnimation.NONE || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		final Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		// Only interrupt an idle player. Overwriting a combat or skilling animation would be
		// visually confusing and could hide something the player needs to see.
		if (local.getAnimation() != -1)
		{
			return;
		}

		local.setAnimation(emote.getAnimationId());
		local.setAnimationFrame(0);
	}
}
