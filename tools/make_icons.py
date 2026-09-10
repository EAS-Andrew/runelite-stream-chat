#!/usr/bin/env python3
"""Regenerates the chat source icons.

RuneLite converts these to indexed sprites with ImageUtil.getImageIndexedSprite, which draws a
pixel ONLY if it is fully opaque -- any partial alpha silently becomes transparent. So an icon
cannot rely on antialiasing: every pixel is either fully on or fully off.

At 11x11 there are only 121 pixels, and downsampling vector art puts edges in the wrong places
(a 1px logo detail either vanishes or doubles in width depending on where it lands). So each mark
is hand-placed instead, laid out from the real logo's proportions:

  * Twitch  -- the glitch mark: chamfered top-left, two eyes, tail bottom-left, chamfered
               bottom-right. Derived from the official 2400x2800 path, letterboxed to 9x11 so it
               keeps its taller-than-wide proportions.
  * YouTube -- the rounded badge with a white play triangle.
  * Kick    -- the bold stepped K.

Usage:  python3 tools/make_icons.py
"""

import os

from PIL import Image, ImageDraw

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "com", "streamchat")

TRANSPARENT = (0, 0, 0, 0)

TWITCH_PURPLE = (169, 112, 255, 255)
YOUTUBE_RED = (255, 40, 40, 255)
KICK_GREEN = (83, 252, 24, 255)
WHITE = (255, 255, 255, 255)

# '.' transparent, '#' brand colour, 'w' white
KEYED = {"w": WHITE}

# Twitch glitch. Rows 0-8 are the body, rows 9-10 the tail. The eyes sit at 48% and 77% across,
# matching the official mark.
TWITCH = [
    "..########.",
    ".#########.",
    ".#########.",
    ".####.##.#.",
    ".####.##.#.",
    ".####.##.#.",
    ".#########.",
    ".########..",
    ".#######...",
    "...##......",
    "...#.......",
]

# YouTube badge: rounded tile, wider than tall, with a play triangle knocked out in white.
YOUTUBE = [
    "...........",
    ".#########.",
    "###########",
    "####w######",
    "####ww#####",
    "####www####",
    "####ww#####",
    "####w######",
    "###########",
    ".#########.",
    "...........",
]

# Kick: 3px stem with 2px stepped arms.
KICK = [
    "...........",
    ".###....##.",
    ".###...##..",
    ".###..##...",
    ".###.##....",
    ".######....",
    ".###.##....",
    ".###..##...",
    ".###...##..",
    ".###....##.",
    "...........",
]

ICONS = {
    "twitch.png": (TWITCH, TWITCH_PURPLE),
    "youtube.png": (YOUTUBE, YOUTUBE_RED),
    "kick.png": (KICK, KICK_GREEN),
}


def build(grid, brand):
    height = len(grid)
    width = len(grid[0])
    img = Image.new("RGBA", (width, height), TRANSPARENT)
    px = img.load()

    for y, row in enumerate(grid):
        assert len(row) == width, "row %d is %d wide, expected %d" % (y, len(row), width)
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            px[x, y] = brand if ch == "#" else KEYED[ch]

    return img


# ------------------------------------------------------------------------------------------------
# Non-sprite images: the Plugin Hub listing icon and the README preview. These are ordinary
# artwork rather than game sprites, so antialiasing is fine here.
# ------------------------------------------------------------------------------------------------

BRAND = [TWITCH_PURPLE, YOUTUBE_RED, KICK_GREEN]


def build_plugin_icon():
    """Plugin Hub listing icon. The Hub caps this at 48x72."""
    scale = 8
    w = h = 48
    img = Image.new("RGBA", (w * scale, h * scale), TRANSPARENT)
    d = ImageDraw.Draw(img)

    d.rounded_rectangle((2 * scale, 6 * scale, 46 * scale, 38 * scale),
                        radius=8 * scale, fill=(232, 232, 232, 255))
    d.polygon([(12 * scale, 36 * scale), (12 * scale, 46 * scale), (24 * scale, 36 * scale)],
              fill=(232, 232, 232, 255))

    r = 4 * scale
    for i, colour in enumerate(BRAND):
        cx, cy = (12 + i * 12) * scale, 22 * scale
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=colour)

    return img.resize((w, h), Image.LANCZOS)


def build_docs_preview(tiles):
    """Enlarged side-by-side of the three chat sprites, for the README."""
    scale, pad = 12, 10
    tw = 11 * scale
    sheet = Image.new("RGBA", (tw * len(tiles) + pad * (len(tiles) + 1), tw + pad * 2), (40, 35, 28, 255))

    for i, tile in enumerate(tiles):
        big = tile.resize((tw, tw), Image.NEAREST)
        sheet.paste(big, (pad + i * (tw + pad), pad), big)

    return sheet


def main():
    out = os.path.abspath(OUT_DIR)
    os.makedirs(out, exist_ok=True)

    tiles = []
    for name, (grid, brand) in ICONS.items():
        img = build(grid, brand)
        tiles.append(img)
        img.save(os.path.join(out, name), "PNG", optimize=True)

        data = list(img.getdata())
        colours = {p for p in data if p[3] == 255}
        assert len(colours) <= 255, "%s has too many colours for an indexed sprite" % name
        assert all(p[3] in (0, 255) for p in data), "%s has partial alpha" % name
        print("%-12s %dx%d  %d opaque colours" % (name, img.width, img.height, len(colours)))

    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

    icon = build_plugin_icon()
    icon.save(os.path.join(root, "icon.png"), "PNG", optimize=True)

    docs = os.path.join(root, "docs")
    os.makedirs(docs, exist_ok=True)
    build_docs_preview(tiles).save(os.path.join(docs, "icons.png"), "PNG", optimize=True)
    print("wrote icon.png and docs/icons.png")


if __name__ == "__main__":
    main()
