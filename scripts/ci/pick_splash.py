#!/usr/bin/env python3
"""Picks a splash-screen frame out of the CI cold-start burst.

Why this exists
---------------
`docs/media/00-splash.png` used to be a picture of the onboarding screen. The
capture script grabbed five frames starting at 0.6 s and, when none of them
looked right, published frame *c* anyway. By then the 1.4 s Compose splash had
already navigated away.

So this script is a **gate**, not a guess: it only promotes a frame that is
actually the splash, and exits non-zero when nothing qualifies. The caller then
keeps the screenshot that is already committed, which is far better than
publishing a wrong one.

What "the splash" looks like
----------------------------
* solid indigo `#312E81` across the whole screen (no white system window, no
  light dashboard, no onboarding illustration);
* a white logo/wordmark block in the middle band;
* a white version pill and tagline below it.

The onboarding and dashboard screens are light (near-white), so a frame that
contains a large light region is rejected outright. The Android 12+ system
splash is also indigo, so it lands in the same bucket — it is a legitimate
splash frame, just without the version pill; the scoring prefers the Compose
frame because it has more white pixels on screen.
"""

from __future__ import annotations

import glob
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - CI installs Pillow, but never guess
    print(
        "::error::Pillow is missing, so the splash frame cannot be validated. "
        "Refusing to publish an unchecked screenshot.",
        file=sys.stderr,
    )
    raise SystemExit(1)

# Indigo #312E81: a *deep* blue where blue clearly leads red and green.
MIN_INDIGO_FRACTION = 0.60
# Anything this light is a normal app screen, not the splash.
MAX_LIGHT_FRACTION = 0.25
# Where the mark, wordmark, version pill and tagline sit (fraction of height).
# See SplashScreen: the whole branding block is one group, roughly 30%-60%.
TITLE_BAND = (0.26, 0.64)
# Enough near-white pixels to prove the branding has faded in.
MIN_TITLE_FRACTION = 0.010


def is_indigo(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return b > 100 and b > r + 40 and b > g + 40


def is_light(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return r > 200 and g > 200 and b > 200


def is_text(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return r > 195 and g > 195 and b > 195


def measure(path: str) -> tuple[float, float, float]:
    """Returns (indigo fraction, light fraction, title-band text fraction)."""
    with Image.open(path) as raw:
        img = raw.convert("RGB")

    width, height = img.size

    indigo_hits = light_hits = total = 0
    for y in range(0, height, 12):
        for x in range(0, width, 12):
            px = img.getpixel((x, y))
            total += 1
            if is_indigo(px):
                indigo_hits += 1
            elif is_light(px):
                light_hits += 1

    title_hits = title_total = 0
    top, bottom = int(height * TITLE_BAND[0]), int(height * TITLE_BAND[1])
    for y in range(top, bottom, 6):
        for x in range(int(width * 0.18), int(width * 0.82), 8):
            title_total += 1
            if is_text(img.getpixel((x, y))):
                title_hits += 1

    return (
        indigo_hits / max(total, 1),
        light_hits / max(total, 1),
        title_hits / max(title_total, 1),
    )


def main() -> int:
    frames = sorted(glob.glob("shots/splash-frame-*.png"))
    if not frames:
        print("No splash frames were captured.")
        return 1

    candidates = []
    for path in frames:
        try:
            indigo, light, title = measure(path)
        except Exception as error:  # empty or corrupt frame
            print(f"skip {os.path.basename(path)}: {error}")
            continue
        verdict = (
            indigo >= MIN_INDIGO_FRACTION
            and light <= MAX_LIGHT_FRACTION
            and title >= MIN_TITLE_FRACTION
        )
        print(
            f"{os.path.basename(path)}: indigo={indigo:.2f} light={light:.2f} "
            f"title={title:.3f} {'ok' if verdict else 'rejected'}"
        )
        if verdict:
            candidates.append((path, title, indigo))

    if not candidates:
        print(
            "No captured frame showed the splash (solid indigo + white branding). "
            "Keeping the previously published screenshot."
        )
        for path in frames:
            if os.path.exists(path):
                os.remove(path)
        return 1

    # Best branding wins; ties go to the indigo-est frame, then to the latest
    # frame (the wordmark is fully faded in just before navigation).
    best = max(candidates, key=lambda c: (round(c[1], 3), c[2], c[0]))
    os.replace(best[0], "shots/00-splash.png")
    print(f"kept {os.path.basename(best[0])} as 00-splash.png")

    for path, _, _ in candidates:
        if os.path.exists(path):
            os.remove(path)
    for path in glob.glob("shots/splash-frame-*.png"):
        os.remove(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
