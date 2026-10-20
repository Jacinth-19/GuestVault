#!/usr/bin/env python3
"""Picks the best splash-screen frame captured during the CI cold start.

The splash is a Compose screen that fades its title in over ~700 ms and then
navigates to the dashboard after ~1.15 s, so a single screenshot is a race.
The CI script grabs a handful of frames; this script scores them:

* background score  – the left edge must be the indigo gradient, not the white
  system window and not the light dashboard;
* title score       – how much near-white text is present in the band where
  "GuestVault" and the tagline are drawn.

The frame with a real gradient *and* visible branding wins.
"""

from __future__ import annotations

import glob
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    print("Pillow missing, falling back to the last frame", file=sys.stderr)
    frames = sorted(glob.glob("shots/splash-frame-*.png"))
    if frames:
        os.replace(frames[-1], "shots/00-splash.png")
    raise SystemExit(0)

BACKGROUND_COLUMN = 0.10   # x position sampled for the gradient
TITLE_BAND = (0.46, 0.64)  # normalized y range of the logo/title/tagline block
REQUIRED_BACKGROUND = 0.75


def is_indigo(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return b > 90 and b > r + 25 and b > g + 20


def is_text(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return r > 195 and g > 195 and b > 195


def score(path: str) -> tuple[float, float]:
    with Image.open(path) as raw:
        img = raw.convert("RGB")

    width, height = img.size

    bg_hits = 0
    bg_total = 0
    for y in range(int(height * 0.10), int(height * 0.90), 24):
        bg_total += 1
        if is_indigo(img.getpixel((int(width * BACKGROUND_COLUMN), y))):
            bg_hits += 1
    background = bg_hits / max(bg_total, 1)

    text_hits = 0
    text_total = 0
    top = int(height * TITLE_BAND[0])
    bottom = int(height * TITLE_BAND[1])
    for y in range(top, bottom, 6):
        for x in range(int(width * 0.22), int(width * 0.78), 10):
            text_total += 1
            if is_text(img.getpixel((x, y))):
                text_hits += 1
    text = text_hits / max(text_total, 1)

    return background, text


def main() -> int:
    frames = sorted(glob.glob("shots/splash-frame-*.png"))
    if not frames:
        print("No splash frames captured.")
        return 1

    candidates = []
    for path in frames:
        try:
            background, text = score(path)
        except Exception as error:  # corrupt frame
            print(f"skipping {path}: {error}")
            continue
        print(f"{os.path.basename(path)}: background={background:.2f} text={text:.2f}")
        candidates.append((path, background, text))

    usable = [c for c in candidates if c[1] >= REQUIRED_BACKGROUND]
    if not usable:
        print("No frame looked like the indigo splash.")
        return 1

    # Prefer visible branding, then the strongest gradient.
    best = max(usable, key=lambda c: (round(c[2], 2), c[1]))
    os.replace(best[0], "shots/00-splash.png")
    print(f"kept {os.path.basename(best[0])} as 00-splash.png")

    for path, _, _ in candidates:
        if os.path.exists(path):
            os.remove(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
