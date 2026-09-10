package com.streamchat;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ChatIconManager;
import net.runelite.client.util.ImageUtil;

/**
 * Registers the per-platform chat icons and hands back their {@code <img=n>} indices.
 *
 * <p>Chat icons are appended to the client's shared mod-icon array and there is no way to remove
 * one, so registration happens exactly once per client session even if the plugin is toggled off
 * and on again. This class is a singleton so that flag survives a restart of the plugin.
 */
@Slf4j
@Singleton
public class ChatIcons
{
	private final ChatIconManager chatIconManager;
	private final Map<StreamPlatform, Integer> iconIds = new EnumMap<>(StreamPlatform.class);

	/** The same artwork, kept for the overlay, which draws with Java2D rather than chat markup. */
	private final Map<StreamPlatform, BufferedImage> images = new EnumMap<>(StreamPlatform.class);
	private boolean loaded;

	@Inject
	private ChatIcons(ChatIconManager chatIconManager)
	{
		this.chatIconManager = chatIconManager;
	}

	/** Loads and registers the icons. Must be called on the client thread. */
	public void load()
	{
		if (loaded)
		{
			return;
		}
		loaded = true;

		for (StreamPlatform platform : StreamPlatform.values())
		{
			final BufferedImage image = ImageUtil.loadImageResource(ChatIcons.class, platform.getIconResource());
			if (image == null)
			{
				log.warn("missing icon resource {}", platform.getIconResource());
				continue;
			}

			images.put(platform, image);
			iconIds.put(platform, chatIconManager.registerChatIcon(image));
		}
	}

	/**
	 * Loads just the artwork, with no client involvement.
	 *
	 * <p>The overlay can draw before the client has booted far enough to register chat icons, so it
	 * must not depend on {@link #load()} having run.
	 */
	@Nullable
	public BufferedImage image(StreamPlatform platform)
	{
		BufferedImage image = images.get(platform);
		if (image == null)
		{
			image = ImageUtil.loadImageResource(ChatIcons.class, platform.getIconResource());
			if (image != null)
			{
				images.put(platform, image);
			}
		}
		return image;
	}

	/**
	 * The index to use in an {@code <img=n>} tag, or -1 when the icon is not usable yet.
	 *
	 * <p>Indices are invalidated whenever the client restarts its game state and are reassigned on
	 * the login screen, so this has to be read at render time rather than cached.
	 */
	public int index(StreamPlatform platform)
	{
		final Integer id = iconIds.get(platform);
		return id == null ? -1 : chatIconManager.chatIconIndex(id);
	}
}
