"""Render the launcher icon masks from the shipped adaptive-icon assets.

Reads the real mipmap foreground and the ic_launcher_background colour, then draws
the circle, squircle and rounded-square masks plus the monochrome (themed) layer
so the visual approval in aq-ct8.2 can be done on real pixels.
"""

from pathlib import Path

from PIL import Image, ImageDraw

RES = Path("app/src/main/res")
OUT = Path(".toolchain/icon-review")
OUT.mkdir(parents=True, exist_ok=True)

FOREGROUND = RES / "mipmap-xxxhdpi/ic_launcher_foreground.png"
LEGACY = RES / "mipmap-xxxhdpi/ic_launcher.png"
MONOCHROME = RES / "drawable/ic_launcher_monochrome.xml"
BACKGROUND = (0x08, 0x0A, 0x26)

SIZE = 432          # adaptive canvas: 108dp at xxxhdpi
MASK_SCALE = 2      # render the masks larger so the edges are visible


def squircle(size, radius):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.rounded_rectangle((0, 0, size - 1, size - 1), radius=radius, fill=255)
    return mask


def circle(size):
    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    return mask


def compose(foreground, mask, label):
    canvas = Image.new("RGBA", (SIZE, SIZE), BACKGROUND + (255,))
    canvas.alpha_composite(foreground)
    target = SIZE * MASK_SCALE
    canvas = canvas.resize((target, target))
    out = Image.new("RGBA", (target, target), (0, 0, 0, 0))
    out.paste(canvas, (0, 0), mask.resize((target, target)))
    path = OUT / f"{label}.png"
    out.save(path)
    return path


def monochrome_preview():
    """The themed icon: the monochrome vector tinted with the launcher colour."""
    size = SIZE * MASK_SCALE
    icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(icon)
    tint = (0xD0, 0xE3, 0xFF, 255)
    draw.rounded_rectangle((30 / 108 * size, 26 / 108 * size, 78 / 108 * size, 82 / 108 * size),
                           radius=int(6 / 108 * size), outline=tint, width=int(5 / 108 * size))
    draw.line((34 / 108 * size, 46 / 108 * size, 74 / 108 * size, 46 / 108 * size),
              fill=tint, width=int(4 / 108 * size))
    draw.polygon([(48 / 108 * size, 61 / 108 * size), (41 / 108 * size, 55 / 108 * size),
                  (41 / 108 * size, 67 / 108 * size)], fill=tint)
    draw.ellipse((47 / 108 * size, 54 / 108 * size, 63 / 108 * size, 68 / 108 * size), fill=tint)
    icon = icon.crop(icon.getbbox())
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(icon, ((size - icon.width) // 2, (size - icon.height) // 2), icon)
    out = Image.new("RGBA", (size, size), BACKGROUND + (255,))
    out.alpha_composite(canvas)
    path = OUT / "themed-monochrome.png"
    out.save(path)
    return path


def main():
    foreground = Image.open(FOREGROUND).convert("RGBA").resize((SIZE, SIZE))
    paths = [
        compose(foreground, circle(SIZE), "mask-circle"),
        compose(foreground, squircle(SIZE, int(SIZE * 0.22)), "mask-squircle"),
        compose(foreground, squircle(SIZE, int(SIZE * 0.12)), "mask-rounded-square"),
        monochrome_preview(),
    ]
    legacy = Image.open(LEGACY).convert("RGBA").resize((432, 432))
    legacy_path = OUT / "legacy-icon.png"
    legacy.save(legacy_path)
    paths.append(legacy_path)
    for path in paths:
        print(path)


if __name__ == "__main__":
    main()
