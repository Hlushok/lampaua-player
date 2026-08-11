#!/usr/bin/env python3
"""Generate the Android TV UA Player launcher banner from the canonical app logo."""

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/res/drawable-nodpi/ua_player_icon.png"
TARGET = ROOT / "app/src/main/res/mipmap-xhdpi/banner.png"
FONT = Path(r"C:\Windows\Fonts\segoeuib.ttf")


def blend(start: int, end: int, amount: float) -> int:
    return round(start + (end - start) * amount)


def main() -> None:
    width, height = 320, 180
    top = (7, 21, 42)
    bottom = (2, 8, 18)
    banner = Image.new("RGB", (width, height))
    pixels = banner.load()
    for y in range(height):
        amount = y / (height - 1)
        color = tuple(blend(top[i], bottom[i], amount) for i in range(3))
        for x in range(width):
            pixels[x, y] = color

    draw = ImageDraw.Draw(banner)
    draw.rounded_rectangle((4, 4, width - 5, height - 5), radius=18,
                           outline=(45, 85, 135), width=2)

    logo_size = 126
    logo_box = (16, 27, 16 + logo_size, 27 + logo_size)
    draw.ellipse((13, 24, 145, 156), fill=(0, 0, 0), outline=(240, 183, 38), width=2)
    logo = Image.open(SOURCE).convert("RGBA").resize(
        (logo_size, logo_size), Image.Resampling.LANCZOS)
    mask = Image.new("L", (logo_size, logo_size), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, logo_size - 1, logo_size - 1), fill=255)
    banner.paste(logo, logo_box[:2], mask)

    draw.rounded_rectangle((153, 35, 156, 145), radius=2, fill=(240, 183, 38))

    title = "UA Player"
    size = 32
    while size >= 22:
        font = ImageFont.truetype(str(FONT), size)
        bounds = draw.textbbox((0, 0), title, font=font)
        if bounds[2] - bounds[0] <= 145:
            break
        size -= 1
    text_width = bounds[2] - bounds[0]
    text_height = bounds[3] - bounds[1]
    text_x = 166 + (145 - text_width) // 2
    text_y = (height - text_height) // 2 - bounds[1] - 8
    draw.text((text_x + 1, text_y + 2), title, font=font, fill=(0, 0, 0))
    draw.text((text_x, text_y), title, font=font, fill=(248, 250, 255))

    line_y = text_y + text_height + 12
    draw.rounded_rectangle((174, line_y, 236, line_y + 4), radius=2, fill=(255, 212, 0))
    draw.rounded_rectangle((238, line_y, 299, line_y + 4), radius=2, fill=(8, 119, 232))

    TARGET.parent.mkdir(parents=True, exist_ok=True)
    banner.save(TARGET, format="PNG", optimize=True)


if __name__ == "__main__":
    main()
