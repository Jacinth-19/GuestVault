#!/usr/bin/env python3
"""Generates the Google Play listing graphics from the app's own brand assets.

Everything here is deterministic and checked into the repository, so the store
listing can be rebuilt on any machine (and in CI) without design tools:

  docs/play/icon-512.png          512x512, 32-bit PNG with alpha, < 1 MB
  docs/play/feature-graphic.png   1024x500, 24-bit PNG without alpha
  docs/play/screenshots/*.png     1200x2400 (2:1), no alpha, app UI only

Run:  python3 scripts/make_store_assets.py
"""

from __future__ import annotations

import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MEDIA = os.path.join(ROOT, "docs", "media")
OUT = os.path.join(ROOT, "docs", "play")
SHOTS = os.path.join(OUT, "screenshots")

# Brand palette, taken from ui/theme/Color.kt and docs/media/logo.svg.
INDIGO_500 = (79, 70, 229)
INDIGO_900 = (49, 46, 129)
INDIGO_950 = (30, 27, 75)
SURFACE_DARK = (15, 23, 42)

FONT_DIR = "/usr/share/fonts/truetype/dejavu"


def _font(size: int, bold: bool = True) -> ImageFont.FreeTypeFont:
    name = "DejaVuSans-Bold.ttf" if bold else "DejaVuSans.ttf"
    path = os.path.join(FONT_DIR, name)
    if os.path.exists(path):
        return ImageFont.truetype(path, size)
    return ImageFont.load_default(size)  # Pillow >= 10.1


def _gradient(size: int) -> Image.Image:
    """Diagonal indigo gradient, matching the launcher icon."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    stops = [(0.0, INDIGO_500), (0.55, INDIGO_900), (1.0, INDIGO_950)]

    for y in range(size):
        for x in range(size):
            t = (x + y) / (2.0 * (size - 1))
            for i in range(len(stops) - 1):
                a, ca = stops[i]
                b, cb = stops[i + 1]
                if a <= t <= b:
                    f = (t - a) / (b - a) if b > a else 0.0
                    px[x, y] = tuple(round(ca[k] + (cb[k] - ca[k]) * f) for k in range(3))
                    break
            else:
                px[x, y] = stops[-1][1]
    return img


def _bezier(p0, p1, p2, p3, steps=80):
    pts = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0]
        y = u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1]
        pts.append((x, y))
    return pts


def render_icon(size: int = 512) -> Image.Image:
    """Rasterises docs/media/logo.svg geometry at `size` pixels (RGBA)."""
    ss = 8                     # supersampling factor
    s = size * ss
    scale = s / 108.0

    def p(x, y):
        return (x * scale, y * scale)

    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # Rounded-square background with the gradient.
    radius = int(24 * scale)
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, s - 1, s - 1], radius=radius, fill=255)
    canvas.paste(_gradient(s), (0, 0), mask)

    # Soft top sheen (white 0.18 -> 0).
    sheen = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(sheen)
    for i in range(int(s * 0.45)):
        alpha = int(46 * (1 - i / (s * 0.45)))
        sdraw.line([(0, i), (s, i)], fill=(255, 255, 255, alpha))
    canvas.alpha_composite(Image.composite(sheen, Image.new("RGBA", (s, s), (0, 0, 0, 0)), mask))

    draw = ImageDraw.Draw(canvas)

    # Shield outline (straight top, curved bottom).
    left = p(27, 27.5)
    top = p(54, 18)
    right = p(81, 27.5)
    right_mid = p(81, 51)
    bottom = p(54, 89)
    left_mid = p(27, 51)

    shield = [left, top, right, right_mid]
    shield += _bezier(right_mid, p(81, 68), p(69.5, 81.5), bottom)
    shield += _bezier(bottom, p(38.5, 81.5), p(27, 68), left_mid)
    draw.polygon(shield, fill=(255, 255, 255, 255))

    # Lock body + shackle + keyhole.
    draw.rounded_rectangle(
        [p(39, 48.5), p(69, 72.5)], radius=5 * scale, fill=INDIGO_900 + (255,)
    )
    draw.line(
        [p(46, 48.5), p(46, 43)],
        fill=INDIGO_900 + (255,),
        width=int(4.6 * scale),
    )
    draw.arc(
        [p(46, 35), p(62, 51)],
        start=180,
        end=360,
        fill=INDIGO_900 + (255,),
        width=int(4.6 * scale),
    )
    draw.line([p(62, 43), p(62, 48.5)], fill=INDIGO_900 + (255,), width=int(4.6 * scale))
    draw.ellipse([p(50.9, 54.4), p(57.1, 60.6)], fill=(255, 255, 255, 255))
    draw.rounded_rectangle(
        [p(52.6, 58.5), p(55.4, 65.5)], radius=int(1.2 * scale), fill=(255, 255, 255, 255)
    )

    return canvas.resize((size, size), Image.LANCZOS)


def build_icon() -> str:
    icon = render_icon(512)
    assert icon.size == (512, 512) and icon.mode == "RGBA"
    path = os.path.join(OUT, "icon-512.png")
    icon.save(path, "PNG", optimize=True)
    return path


def build_feature_graphic() -> str:
    """1024x500 feature graphic, RGB (Play rejects alpha here).

    Play draws a play button over the centre of this image when a promo video is
    attached, so the middle band (roughly x 440-584) is deliberately empty:
    artwork sits on the left, copy starts at x=560.
    """
    w, h = 1024, 500
    bg = _gradient(1024).resize((w, h))

    draw = ImageDraw.Draw(bg, "RGBA")
    draw.ellipse([w - 260, -170, w + 240, 330], fill=(255, 255, 255, 12))
    draw.ellipse([-180, h - 240, 240, h + 180], fill=(255, 255, 255, 10))

    # App icon with a soft shadow.
    icon = render_icon(290)
    shadow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle(
        [66, 111, 66 + 280, 111 + 280], radius=64, fill=(0, 0, 0, 90)
    )
    bg.paste(
        Image.alpha_composite(bg.convert("RGBA"), shadow.filter(ImageFilter.GaussianBlur(18)))
        .convert("RGB")
    )
    bg.paste(icon, (60, 105), icon)

    x = 560
    kicker_font = _font(19)
    title_font = _font(66)
    tagline_font = _font(29, bold=False)
    small_font = _font(21, bold=False)

    draw = ImageDraw.Draw(bg, "RGBA")
    draw.text((x, 96), "GUEST MODE · ANY ANDROID PHONE", font=kicker_font, fill=(199, 210, 254, 255))
    draw.text((x, 126), "GuestVault", font=title_font, fill=(255, 255, 255, 255))
    draw.text((x, 236), "Borrow my phone.", font=tagline_font, fill=(224, 231, 255, 255))
    draw.text((x, 276), "Keep your privacy.", font=tagline_font, fill=(224, 231, 255, 255))

    draw.line([(x, 336), (x + 400, 336)], fill=(255, 255, 255, 60), width=2)
    draw.text((x, 356), "Allow-list · PIN · Timer · Auto-wipe", font=small_font, fill=(165, 180, 252, 255))

    path = os.path.join(OUT, "feature-graphic.png")
    bg.convert("RGB").save(path, "PNG", optimize=True)
    return path


# Screenshots that ship to the listing, in display order.
LISTING_SHOTS = [
    "01-onboarding-welcome.png",
    "06-dashboard-light.png",
    "08-select-apps.png",
    "09-confirm-session.png",
    "11-guest-home.png",
    "12-lock-screen.png",
    # The permissions screen is a differentiator on the listing: it shows the
    # privacy promise as evidence rather than as a claim.
    "17-permissions.png",
    "15-dashboard-dark.png",
]


def build_screenshots() -> list[str]:
    """Trims the system bars so each capture is exactly 2:1, full-bleed.

    The emulator captures are 1080x2400 (2.22:1). Play rejects anything above
    2:1, and letterboxing would read as a device frame, so the same number of
    pixels is removed from the top and the bottom — which is exactly the status
    bar and the gesture bar. The result is pure app UI at 1080x2160.
    """
    os.makedirs(SHOTS, exist_ok=True)
    written = []
    for name in LISTING_SHOTS:
        src = os.path.join(MEDIA, name)
        if not os.path.exists(src):
            print(f"  ! missing {name}, skipped")
            continue

        shot = Image.open(src).convert("RGB")
        width, height = shot.size
        ratio = max(width, height) / min(width, height)

        if ratio > 2.0:
            if height > width:
                trim = height - (width * 2)
                top = trim // 2
                shot = shot.crop((0, top, width, height - (trim - top)))
            else:
                trim = width - (height * 2)
                left = trim // 2
                shot = shot.crop((left, 0, width - (trim - left), height))

        out = os.path.join(SHOTS, name)
        shot.save(out, "PNG", optimize=True)
        written.append(out)
    return written


def validate(paths: list[str]) -> None:
    print("\nValidation")
    for path in paths:
        img = Image.open(path)
        size = os.path.getsize(path)
        has_alpha = img.mode in ("RGBA", "LA") or "transparency" in img.info
        ratio = max(img.size) / min(img.size)
        print(
            f"  {os.path.relpath(path, ROOT):48s} {img.size[0]}x{img.size[1]}  "
            f"{img.mode:4s} alpha={has_alpha!s:5s} {size / 1024:.0f} KB  ratio={ratio:.2f}"
        )


def main() -> int:
    if not os.path.isdir(MEDIA):
        print("docs/media is missing; run the media workflow first.", file=sys.stderr)
        return 1
    os.makedirs(OUT, exist_ok=True)

    icon = build_icon()
    feature = build_feature_graphic()
    shots = build_screenshots()

    print("Play listing assets written to docs/play/")
    validate([icon, feature] + shots)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
