#!/usr/bin/env python3
"""Remove the square canvas from Android launcher icons without changing the UA mark."""

from pathlib import Path
from math import hypot

from PIL import Image, ImageChops


ROOT = Path(__file__).resolve().parents[1]
CANONICAL = ROOT / "app/src/main/res/drawable-nodpi/ua_player_icon.png"
ADAPTIVE = ROOT / "app/src/main/res/drawable-nodpi/ua_player_launcher_icon.png"
LEGACY = (
    ROOT / "app/src/main/res/mipmap-mdpi/ic_launcher.png",
    ROOT / "app/src/main/res/mipmap-hdpi/ic_launcher.png",
    ROOT / "app/src/main/res/mipmap-xhdpi/ic_launcher.png",
    ROOT / "app/src/main/res/mipmap-xxhdpi/ic_launcher.png",
    ROOT / "app/src/main/res/mipmap-xxxhdpi/ic_launcher.png",
)


def circle_mask(size: tuple[int, int]) -> Image.Image:
    width, height = size
    center_x = (width - 1) / 2
    center_y = (height - 1) / 2
    minimum = min(width, height)
    inner_radius = minimum * 0.455
    outer_radius = minimum * 0.47
    feather = outer_radius - inner_radius
    pixels: list[int] = []

    for y in range(height):
        for x in range(width):
            distance = hypot(x - center_x, y - center_y)
            if distance <= inner_radius:
                pixels.append(255)
            elif distance >= outer_radius:
                pixels.append(0)
            else:
                amount = 1 - (distance - inner_radius) / feather
                smooth = amount * amount * (3 - 2 * amount)
                pixels.append(round(255 * smooth))

    mask = Image.new("L", size)
    mask.putdata(pixels)
    return mask


def circularize(source: Path, target: Path) -> None:
    with Image.open(source) as opened:
        image = opened.convert("RGBA")
    image.putalpha(ImageChops.darker(image.getchannel("A"), circle_mask(image.size)))
    target.parent.mkdir(parents=True, exist_ok=True)
    image.save(target, format="PNG", optimize=True)


def main() -> None:
    circularize(CANONICAL, ADAPTIVE)
    for icon in LEGACY:
        circularize(icon, icon)


if __name__ == "__main__":
    main()
