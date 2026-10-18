#!/usr/bin/env python3
"""
Biscuit Orbital - texture generator.
Draws the Biscuit Uplink walkie-talkie (16x16 item texture) and the 128x128 mod icon.

Usage:  python3 tools/make_textures.py        (needs Pillow)
Tip:    edit the PALETTE / ART below to restyle the item, or just paint over the PNG.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/biscuit_orbital"
(ASSETS / "textures/item").mkdir(parents=True, exist_ok=True)

PALETTE = {
    ".": (0, 0, 0, 0),
    "K": (42, 26, 14, 255),     # outline
    "D": (74, 44, 23, 255),     # body dark
    "M": (107, 66, 38, 255),    # body mid
    "L": (146, 98, 56, 255),    # body light edge
    "G": (242, 184, 75, 255),   # biscuit gold
    "g": (190, 126, 28, 255),   # biscuit chips / dark gold
    "C": (251, 231, 176, 255),  # cream highlight
    "R": (255, 59, 48, 255),    # LED red
    "r": (150, 20, 20, 255),    # LED dark
    "S": (126, 224, 129, 255),  # screen bright
    "s": (50, 130, 62, 255),    # screen dark
}

# 16 x 16, row by row (x = 0..15)
ART = [
    "...........RK...",
    "...........MK...",
    "...........MK...",
    "...........gK...",
    "...KKKKKKKKKKK..",
    "...KLMMMMMMMRK..",
    "...KLMKMKMKMDK..",
    "...KLMMMMMMMDK..",
    "...KLMKMKMKMDK..",
    "...KLDDDDDDDDK..",
    "...KLDsSSSSsDK..",
    "...KLDssssssDK..",
    "...KLMMGGGMMDK..",
    "...KLMGCgGgMDK..",
    "...KLMMGGGMMDK..",
    "...KKKKKKKKKKK..",
]


def item_texture():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ART):
        assert len(row) == 16, (y, len(row))
        for x, ch in enumerate(row):
            px[x, y] = PALETTE[ch]
    img.save(ASSETS / "textures/item/biscuit_uplink.png")
    # preview for eyeballing
    img.resize((256, 256), Image.NEAREST).save("/tmp/uplink_preview.png")


def mod_icon():
    S = 4  # supersample
    size = 128 * S
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # rounded dark backdrop
    d.rounded_rectangle([4 * S, 4 * S, size - 4 * S, size - 4 * S], radius=22 * S, fill=(30, 18, 10, 255))
    # starfield
    import random
    r = random.Random(7)
    for _ in range(40):
        x, y = r.randint(10 * S, size - 10 * S), r.randint(10 * S, size - 10 * S)
        c = r.choice([(255, 235, 190, 200), (255, 200, 120, 160)])
        d.ellipse([x, y, x + 2 * S, y + 2 * S], fill=c)
    cx = cy = size // 2
    # orbit ring (back half)
    d.ellipse([cx - 54 * S, cy - 20 * S, cx + 54 * S, cy + 20 * S], outline=(255, 150, 60, 255), width=3 * S)
    # biscuit
    R = 34 * S
    d.ellipse([cx - R - 3 * S, cy - R - 3 * S, cx + R + 3 * S, cy + R + 3 * S], fill=(120, 70, 20, 255))
    d.ellipse([cx - R, cy - R, cx + R, cy + R], fill=(236, 170, 70, 255))
    d.ellipse([cx - R + 5 * S, cy - R + 5 * S, cx + R - 5 * S, cy + R - 5 * S], fill=(246, 190, 90, 255))
    for ang, rad in [(20, 18), (80, 22), (140, 14), (200, 22), (260, 16), (320, 22), (0, 0), (110, 8)]:
        px_ = cx + math.cos(math.radians(ang)) * rad * S
        py_ = cy + math.sin(math.radians(ang)) * rad * S
        d.ellipse([px_ - 4 * S, py_ - 4 * S, px_ + 4 * S, py_ + 4 * S], fill=(110, 60, 18, 255))
    # orbit ring (front half) + red target dot
    d.arc([cx - 54 * S, cy - 20 * S, cx + 54 * S, cy + 20 * S], start=0, end=180, fill=(255, 150, 60, 255), width=3 * S)
    d.ellipse([cx + 40 * S, cy + 8 * S, cx + 52 * S, cy + 20 * S], fill=(255, 59, 48, 255))
    # beam from the top
    d.rectangle([cx - 3 * S, 8 * S, cx + 3 * S, cy - R], fill=(255, 245, 210, 140))
    d.rectangle([cx - 1 * S, 8 * S, cx + 1 * S, cy - R], fill=(255, 255, 255, 230))
    img = img.resize((128, 128), Image.LANCZOS)
    img.save(ASSETS / "icon.png")


if __name__ == "__main__":
    item_texture()
    mod_icon()
    print("textures written")
