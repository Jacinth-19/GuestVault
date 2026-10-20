#!/usr/bin/env python3
"""Validates docs/play against Google Play's current asset rules.

Rules enforced (Play Console Help, "Preview assets"):
  * icon            512x512, 32-bit PNG, alpha allowed, <= 1 MB
  * feature graphic exactly 1024x500, JPEG/24-bit PNG, NO alpha
  * phone shots     320..3840 px per side, long side <= 2x short side,
                    2..8 images, no alpha
"""

from __future__ import annotations

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PLAY = os.path.join(ROOT, "docs", "play")
SHOTS = os.path.join(PLAY, "screenshots")

MAX_ICON_BYTES = 1_000_000


def has_alpha(image: Image.Image) -> bool:
    return image.mode in ("RGBA", "LA", "P") and (
        image.mode != "P" or "transparency" in image.info
    )


def check_icon(errors: list[str]) -> None:
    path = os.path.join(PLAY, "icon-512.png")
    if not os.path.exists(path):
        errors.append("icon-512.png is missing")
        return
    image = Image.open(path)
    size = os.path.getsize(path)
    if image.size != (512, 512):
        errors.append(f"icon must be 512x512, found {image.size[0]}x{image.size[1]}")
    if size > MAX_ICON_BYTES:
        errors.append(f"icon is {size / 1024:.0f} KB, Play allows at most 1024 KB")
    print(f"icon: {image.size[0]}x{image.size[1]} {image.mode} {size / 1024:.0f} KB")


def check_feature_graphic(errors: list[str]) -> None:
    path = os.path.join(PLAY, "feature-graphic.png")
    if not os.path.exists(path):
        errors.append("feature-graphic.png is missing")
        return
    image = Image.open(path)
    if image.size != (1024, 500):
        errors.append(
            f"feature graphic must be exactly 1024x500, found {image.size[0]}x{image.size[1]}"
        )
    if has_alpha(image):
        errors.append("feature graphic must not have an alpha channel")
    print(f"feature graphic: {image.size[0]}x{image.size[1]} {image.mode}")


def check_screenshots(errors: list[str]) -> None:
    if not os.path.isdir(SHOTS):
        errors.append("screenshots/ directory is missing")
        return

    files = sorted(f for f in os.listdir(SHOTS) if f.endswith(".png"))
    if not 2 <= len(files) <= 8:
        errors.append(f"Play requires 2-8 phone screenshots, found {len(files)}")

    for name in files:
        path = os.path.join(SHOTS, name)
        image = Image.open(path)
        width, height = image.size
        ratio = max(width, height) / min(width, height)

        if min(width, height) < 320 or max(width, height) > 3840:
            errors.append(f"{name}: {width}x{height} is outside 320..3840 px per side")
        if ratio > 2.0:
            errors.append(f"{name}: aspect ratio {ratio:.2f}:1 exceeds Play's 2:1 limit")
        if has_alpha(image):
            errors.append(f"{name}: screenshots must not have an alpha channel")
        if os.path.getsize(path) > 8 * 1024 * 1024:
            errors.append(f"{name}: larger than Play's 8 MB screenshot limit")

        print(f"screenshot: {name} {width}x{height} {image.mode} ratio={ratio:.2f}")


def main() -> int:
    errors: list[str] = []
    check_icon(errors)
    check_feature_graphic(errors)
    check_screenshots(errors)

    if errors:
        print("\nStore asset validation FAILED")
        for error in errors:
            print(f"  - {error}")
        return 1

    print("\nStore assets satisfy Play's current requirements.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
