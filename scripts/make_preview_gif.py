#!/usr/bin/env python3
"""Builds a small looping GIF preview from the emulator screenshots.

The full walkthrough is a 60-120 second MP4, which is too large (and too slow)
to autoplay inside a README. A handful of key screens at ~1.6s each gives a
quick visual tour while the MP4 stays one click away.
"""

from __future__ import annotations

import glob
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - CI installs pillow
    print("Pillow is not installed, skipping GIF preview.", file=sys.stderr)
    sys.exit(0)

MEDIA_DIR = os.path.join("docs", "media")
OUTPUT = os.path.join(MEDIA_DIR, "preview.gif")
WIDTH = 380
FRAME_DURATION_MS = 1600

# Ordered: the most illustrative screens for a quick glance.
PREFERRED = [
    "01-onboarding-welcome.png",
    "06-dashboard-light.png",
    "08-select-apps.png",
    "09-confirm-session.png",
    "11-guest-home.png",
    "12-lock-screen.png",
    "14-dashboard-history.png",
    "15-dashboard-dark.png",
]


def find_frames() -> list[str]:
    frames = [os.path.join(MEDIA_DIR, name) for name in PREFERRED]
    frames = [f for f in frames if os.path.isfile(f)]
    if len(frames) >= 3:
        return frames

    # Fall back to whatever the run produced.
    return sorted(glob.glob(os.path.join(MEDIA_DIR, "*.png")))[:8]


def main() -> int:
    if not os.path.isdir(MEDIA_DIR):
        print("No media directory, nothing to do.")
        return 0

    frames = find_frames()
    if len(frames) < 2:
        print("Not enough screenshots to build a preview.")
        return 0

    images = []
    for path in frames:
        with Image.open(path) as image:
            ratio = WIDTH / image.width
            resized = image.resize((WIDTH, int(image.height * ratio)), Image.LANCZOS)
            images.append(resized.convert("P", palette=Image.ADAPTIVE, colors=128))

    # Hold the last frame a little longer so the loop reads well.
    durations = [FRAME_DURATION_MS] * len(images)
    durations[-1] = 2400

    images[0].save(
        OUTPUT,
        save_all=True,
        append_images=images[1:],
        duration=durations,
        loop=0,
        optimize=True,
        disposal=2,
    )
    print(f"Wrote {OUTPUT} with {len(images)} frames ({os.path.getsize(OUTPUT) // 1024} KB)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
