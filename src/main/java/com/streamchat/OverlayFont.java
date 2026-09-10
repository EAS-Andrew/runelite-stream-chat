package com.streamchat;

import java.awt.Font;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import net.runelite.client.ui.FontManager;

/** Font choice for the on-screen panel. */
@RequiredArgsConstructor
public enum OverlayFont
{
	/** Whatever the overlay renderer is already using. */
	DEFAULT("Client default"),
	SMALL("Small"),
	REGULAR("Regular"),
	BOLD("Bold");

	private final String displayName;

	/** The font to draw with, or null to leave the graphics context alone. */
	@Nullable
	public Font font()
	{
		switch (this)
		{
			case SMALL:
				return FontManager.getRunescapeSmallFont();
			case REGULAR:
				return FontManager.getRunescapeFont();
			case BOLD:
				return FontManager.getRunescapeBoldFont();
			default:
				return null;
		}
	}

	@Override
	public String toString()
	{
		return displayName;
	}
}
