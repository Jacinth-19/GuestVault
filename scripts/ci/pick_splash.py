#!/usr/bin/env python3
"""Picks a splash-screen frame out of the CI cold-start burst.

Why this exists
---------------
`docs/media/00-splash.png` used to be a picture of the onboarding screen. The
capture script grabbed five frames starting at 0.6 s and, when none of them
looked right, published frame *c* anyway. By then the 1.15 s Compose splash had
already navigated away.

So this script is a **gate**, not a guess: it only promotes a frame that is
actually the branded splash, and exits non-zero when nothing qualifies. The
caller then keeps the screenshot that is already committed, which is far better
than publishing a wrong one.

Three things have to be true
----------------------------
1. **Indigo.** #312E81 covers the whole screen, so a light screen (onboarding,
   dashboard) is rejected outright.
2. **Branding.** There is white ink in the middle band.
3. **A wordmark, not just an icon.** This is the subtle one: Android 12+ draws
   its own system splash — *also* indigo, with *also* a white mark — before the
   app's first frame. A frame of that screen is a real splash, but it has no
   "GuestVault" wordmark and no version pill, and publishing it as the app's
   splash is misleading.

   Width alone does not separate them: the system icon is a ~43 %-wide shield,
   the same ballpark as the wordmark. What separates them is that a wordmark is
   **text** — many short ink runs with gaps between letters — while an icon is
   one solid blob. So the gate counts distinct horizontal ink runs in the
   wordmark band and demands at least [MIN_WORDMARK_RUNS]. A letter row clears
   that easily; the measured system splash peaks at 3.

A frame that passes arrives from `am start --ei splash_hold_ms ...`, which asks
the debug build to hold the branded splash long enough to photograph it. The
image is still the real splash screen, just not raced against a 1.4 s timer.
"""

from __future__ import annotations

import glob
import json
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
# See SplashScreen: the whole branding block is one vertically centred group.
TITLE_BAND = (0.26, 0.66)
# Enough near-white pixels to prove the branding has faded in.
MIN_TITLE_FRACTION = 0.010
# The wordmark + version pill band. On the branded splash this row contains
# "GuestVault" (40 sp bold) and the version pill.
WORDMARK_BAND = (0.45, 0.63)
# How many separate ink runs (letters + pill) a row must contain to count as
# text rather than as an icon. The Android 12+ system splash peaks at 3 here.
MIN_WORDMARK_RUNS = 6
# How wide that text row must reach, as a fraction of the screen width.
MIN_WORDMARK_SPAN = 0.35


def is_indigo(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return b > 100 and b > r + 40 and b > g + 40


def is_light(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return r > 200 and g > 200 and b > 200


def is_text(px: tuple[int, int, int]) -> bool:
    r, g, b = px
    return r > 195 and g > 195 and b > 195


def wordmark_profile(img) -> tuple[float, int]:
    """(widest text row as a fraction of width, most ink runs in one row).

    A row counts towards the span only if it looks like a line of text: at
    least [MIN_WORDMARK_RUNS] separate ink runs, i.e. letters with gaps.
    """
    width, height = img.size
    top, bottom = int(height * WORDMARK_BAND[0]), int(height * WORDMARK_BAND[1])
    best_span = 0.0
    best_runs = 0

    for y in range(top, bottom, 3):
        runs = 0
        first = last = None
        previous = False
        for x in range(0, width, 2):
            ink = is_text(img.getpixel((x, y)))
            if ink:
                if not previous:
                    runs += 1
                if first is None:
                    first = x
                last = x
            previous = ink

        best_runs = max(best_runs, runs)
        if runs >= MIN_WORDMARK_RUNS and first is not None and last is not None:
            best_span = max(best_span, (last - first) / width)

    return best_span, best_runs


def measure(path: str) -> tuple[float, float, float, float, int]:
    """(indigo fraction, light fraction, title-band ink, wordmark span)."""
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

    span, runs = wordmark_profile(img)
    return (
        indigo_hits / max(total, 1),
        light_hits / max(total, 1),
        title_hits / max(title_total, 1),
        span,
        runs,
    )


def main() -> int:
    frames = sorted(glob.glob("shots/splash-frame-*.png"))
    if not frames:
        print("No splash frames were captured.")
        return 1

    candidates = []
    rejected = []
    for path in frames:
        try:
            indigo, light, title, span, runs = measure(path)
        except Exception as error:  # empty or corrupt frame
            print(f"skip {os.path.basename(path)}: {error}")
            continue

        reasons = []
        if indigo < MIN_INDIGO_FRACTION:
            reasons.append("not indigo")
        if light > MAX_LIGHT_FRACTION:
            reasons.append("too light")
        if title < MIN_TITLE_FRACTION:
            reasons.append("no branding ink")
        if span < MIN_WORDMARK_SPAN or runs < MIN_WORDMARK_RUNS:
            reasons.append("no wordmark")
        verdict = not reasons

        print(
            f"{os.path.basename(path)}: indigo={indigo:.2f} light={light:.2f} "
            f"title={title:.3f} wordmark={span:.2f}/{runs} runs "
            f"{'ok' if verdict else 'rejected (' + ', '.join(reasons) + ')'}"
        )
        if verdict:
            candidates.append((path, title, span, indigo))
        else:
            rejected.append(
                {
                    "frame": os.path.basename(path),
                    "indigo": round(indigo, 3),
                    "light": round(light, 3),
                    "title": round(title, 4),
                    "wordmark_span": round(span, 3),
                    "wordmark_runs": runs,
                    "reasons": reasons,
                }
            )

    # A short, machine-readable explanation of the capture — attached to the CI
    # run so a rejected burst can be diagnosed without re-running the emulator.
    with open("shots/splash-capture.json", "w", encoding="utf-8") as report:
        json.dump(
            {
                "frames": len(frames),
                "thresholds": {
                    "indigo": MIN_INDIGO_FRACTION,
                    "light": MAX_LIGHT_FRACTION,
                    "title": MIN_TITLE_FRACTION,
                    "wordmark_span": MIN_WORDMARK_SPAN,
                    "wordmark_runs": MIN_WORDMARK_RUNS,
                },
                "accepted": [os.path.basename(c[0]) for c in candidates],
                "rejected": rejected,
            },
            report,
            indent=2,
        )

    if not candidates:
        print(
            "No captured frame showed the branded splash (indigo + wordmark). "
            "Keeping the previously published screenshot."
        )
        for path in frames:
            if os.path.exists(path):
                os.remove(path)
        return 1

    # Best branding wins; ties go to the widest wordmark (the moment the layout
    # has fully settled), then to the indigo-est frame.
    best = max(candidates, key=lambda c: (round(c[1], 3), round(c[2], 3), c[3]))
    os.replace(best[0], "shots/00-splash.png")
    print(f"kept {os.path.basename(best[0])} as 00-splash.png")

    # Candidate frames are deleted by default so a burst can never be committed
    # by accident; CI copies them to an artifact first when debugging is needed.
    keep = os.environ.get("KEEP_SPLASH_FRAMES") == "1"
    for path, _, _, _ in candidates:
        if os.path.exists(path) and not keep:
            os.remove(path)
    if not keep:
        for path in glob.glob("shots/splash-frame-*.png"):
            os.remove(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
