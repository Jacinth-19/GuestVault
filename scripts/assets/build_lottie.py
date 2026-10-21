#!/usr/bin/env python3
"""Generates the onboarding Lottie animations in ``app/src/main/res/raw``.

Why generated instead of hand-edited
------------------------------------
Lottie JSON is a large, unforgiving tree of keyframes. Hand-editing it in a PR
is unreviewable, and a single typo shows up as a blank screen on a user's phone
(``res/raw`` is not validated by the compiler). Keeping the geometry in Python
means:

* the animation is diffable and parameterised,
* ``--check`` proves the committed JSON matches the source of truth,
* ``scripts/ci/check_lottie.py`` can assert the structural invariants Lottie
  actually needs (keyframe order, colour ranges, allowed shape types).

Usage
-----
    python3 scripts/assets/build_lottie.py            # write the JSON files
    python3 scripts/assets/build_lottie.py --check    # fail if out of date

The animations are authored from primitives (rounded rects, ellipses, one
polygon shield) so nothing depends on an external asset or a licence we do not
control. See ``docs/ASSET_LICENCES.md``.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"

WIDTH = 400
HEIGHT = 400
FPS = 30
FRAMES = 60  # two seconds, seamless loop

# Palette, normalised for Lottie (0..1). Mirrors ui/theme/Color.kt.
INDIGO = [0.192, 0.180, 0.506, 1.0]   # #312E81
INDIGO_LIGHT = [0.647, 0.706, 0.988, 1.0]  # #A5B4FC
TEAL = [0.051, 0.580, 0.533, 1.0]     # #0D9488
WHITE = [1.0, 1.0, 1.0, 1.0]

# Easing pairs: (in, out). 0.33/1 and 0.68/0 are Material's standard curve.
EASE_OUT = ((0.33, 1.0), (0.68, 0.0))
EASE_IN_OUT = ((0.65, 0.0), (0.35, 1.0))
LINEAR = ((0.0, 0.0), (1.0, 1.0))


def _ease(easing):
    (ix, iy), (ox, oy) = easing
    return {"i": {"x": ix, "y": iy}, "o": {"x": ox, "y": oy}}


def static(value):
    """A non-animated property. Lists are position/scale vectors."""
    return {"a": 0, "k": value}


def animated(keyframes):
    """``keyframes`` is [(frame, value, easing_into_next_frame)]."""
    out = []
    for index, (frame, value, easing) in enumerate(keyframes):
        item = {"t": frame, "s": value}
        if index < len(keyframes) - 1:
            item.update(_ease(easing))
        out.append(item)
    return {"a": 1, "k": out}


def transform(pos=(0, 0), scale=(100, 100), rotate=0, opacity=100, anchor=(0, 0)):
    return {
        "o": opacity if isinstance(opacity, dict) else static(opacity),
        "r": rotate if isinstance(rotate, dict) else static(rotate),
        "p": pos if isinstance(pos, dict) else static(list(pos)),
        "a": static(list(anchor)),
        "s": scale if isinstance(scale, dict) else static(list(scale)),
    }


def group(items, name="group", pos=(0, 0), scale=(100, 100), rotate=0, opacity=100):
    items = list(items) + [
        {
            "ty": "tr",
            "p": static(list(pos)),
            "a": static([0, 0]),
            "s": static(list(scale)),
            "r": static(rotate),
            "o": static(opacity),
            "sk": static(0),
            "sa": static(0),
            "nm": "transform",
        }
    ]
    return {"ty": "gr", "nm": name, "it": items}


def rounded_rect(size, radius, pos=(0, 0), name="rect"):
    return {
        "ty": "rc",
        "nm": name,
        "d": 1,
        "s": static(list(size)),
        "p": static(list(pos)),
        "r": static(radius),
    }


def ellipse(size, pos=(0, 0), name="ellipse"):
    return {"ty": "el", "nm": name, "s": static(list(size)), "p": static(list(pos))}


def shield_path(scale=1.0, name="shield"):
    """A closed six-point shield, centred on the origin.

    Tangents are zero so the polygon is exact and cannot be mis-parsed. The
    result is a clean, modern shield rather than a rounded cartoon one.
    """
    points = [(-60, -52), (0, -76), (60, -52), (60, 8), (0, 76), (-60, 8)]
    vertices = [[round(x * scale, 2), round(y * scale, 2)] for x, y in points]
    zeros = [[0, 0] for _ in points]
    return {
        "ty": "sh",
        "nm": name,
        "ks": {
            "a": 0,
            "k": {"i": zeros, "o": zeros, "v": vertices, "c": True},
        },
    }


def fill(color, opacity=100, name="fill"):
    return {
        "ty": "fl",
        "nm": name,
        "c": static(color),
        "o": static(opacity),
        "r": 1,
        "bm": 0,
    }


def stroke(color, width, opacity=100, name="stroke"):
    return {
        "ty": "st",
        "nm": name,
        "c": static(color),
        "o": static(opacity),
        "w": static(width),
        "lc": 2,
        "lj": 2,
        "ml": 4,
        "bm": 0,
    }


def shape_layer(index, name, shapes, ks, in_frame=0, out_frame=FRAMES):
    return {
        "ddd": 0,
        "ind": index,
        "ty": 4,
        "nm": name,
        "sr": 1,
        "ks": ks,
        "ao": 0,
        "shapes": shapes,
        "ip": in_frame,
        "op": out_frame,
        "st": 0,
        "bm": 0,
    }


def composition(name, layers):
    return {
        "v": "5.7.4",
        "fr": FPS,
        "ip": 0,
        "op": FRAMES,
        "w": WIDTH,
        "h": HEIGHT,
        "nm": name,
        "ddd": 0,
        "assets": [],
        "layers": layers,
    }


# ---------------------------------------------------------------------------
# 1. "Lend your phone safely" — a phone is handed over, then a shield badge
#    springs onto it.
# ---------------------------------------------------------------------------
def build_lend():
    layers = []

    # Shield badge (top layer) — springs in after the phone settles.
    shield_badge = shape_layer(
        index=1,
        name="shield-badge",
        shapes=[
            group(
                [
                    shield_path(scale=0.52),
                    fill(INDIGO),
                ],
                name="shield",
            ),
            group(
                [ellipse((19, 19), (0, -10)), fill(WHITE)],
                name="keyhole-top",
            ),
            group(
                [rounded_rect((9, 22), 4.5, (0, 12)), fill(WHITE)],
                name="keyhole-stem",
            ),
        ],
        ks=transform(
            pos=static([256, 306]),
            scale=animated(
                [
                    (18, [0, 0, 100], EASE_OUT),
                    (36, [116, 116, 100], EASE_IN_OUT),
                    (46, [100, 100, 100], LINEAR),
                ]
            ),
            opacity=animated([(18, 0, LINEAR), (26, 100, LINEAR)]),
        ),
    )
    layers.append(shield_badge)

    # Phone — slides in from the left with a slight tilt.
    phone = shape_layer(
        index=2,
        name="phone",
        shapes=[
            group(
                [
                    rounded_rect((122, 214), 26),
                    fill(WHITE),
                    stroke(INDIGO, 8),
                ],
                name="body",
            ),
            group(
                [rounded_rect((58, 8), 4, (0, -86)), fill(INDIGO_LIGHT)],
                name="speaker",
            ),
            group(
                [rounded_rect((88, 58), 12, (0, 24)), fill(INDIGO, opacity=12)],
                name="screen",
            ),
        ],
        ks=transform(
            pos=animated(
                [
                    (0, [126, 178, 0], EASE_OUT),
                    (24, [200, 178, 0], LINEAR),
                ]
            ),
            rotate=animated([(0, -15, EASE_OUT), (24, 0, LINEAR)]),
            opacity=animated([(0, 0, LINEAR), (8, 100, LINEAR)]),
        ),
    )
    layers.append(phone)

    # Three soft pulses behind everything, so the loop is seamless.
    pulse_scale = []
    pulse_opacity = []
    for cycle in range(3):
        base = cycle * 20
        pulse_scale += [
            (base, [100, 100, 100], EASE_OUT),
            (base + 12, [116, 116, 100], LINEAR),
        ]
        pulse_opacity += [(base, 34, EASE_OUT), (base + 12, 0, LINEAR)]
    pulse_scale.append((FRAMES, [100, 100, 100], LINEAR))
    pulse_opacity.append((FRAMES, 34, LINEAR))

    pulse = shape_layer(
        index=3,
        name="pulse",
        shapes=[
            group(
                [ellipse((286, 286)), stroke(INDIGO_LIGHT, 5)],
                name="ring",
            )
        ],
        ks=transform(
            pos=static([200, 196]),
            scale=animated(pulse_scale),
            opacity=animated(pulse_opacity),
        ),
    )
    layers.append(pulse)

    # Static wash so the animation sits on any background.
    glow = shape_layer(
        index=4,
        name="glow",
        shapes=[group([ellipse((300, 300)), fill(INDIGO_LIGHT, opacity=10)], name="glow")],
        ks=transform(pos=static([200, 196])),
    )
    layers.append(glow)

    return composition("onboarding-lend", layers)


# ---------------------------------------------------------------------------
# 2. "Only the apps you allow" — a grid appears, the unapproved tiles dim and
#    the approved row is ringed.
# ---------------------------------------------------------------------------
def build_apps():
    layers = []

    # Approval ring, drawn last in time but highest in z.
    ring = shape_layer(
        index=1,
        name="approval-ring",
        shapes=[
            group(
                [rounded_rect((300, 112), 30), stroke(TEAL, 6)],
                name="ring",
            )
        ],
        ks=transform(
            pos=static([200, 200]),
            scale=animated(
                [
                    (40, [92, 92, 100], EASE_OUT),
                    (52, [100, 100, 100], LINEAR),
                ]
            ),
            opacity=animated([(40, 0, EASE_OUT), (52, 100, LINEAR)]),
        ),
    )
    layers.append(ring)

    colours = [
        [INDIGO, INDIGO, INDIGO],
        [TEAL, TEAL, TEAL],
        [INDIGO, INDIGO, INDIGO],
    ]
    approved_row = 1
    index = 2
    # Top-to-bottom, left-to-right so the stagger reads naturally.
    for row in range(3):
        for col in range(3):
            x = 108 + col * 92
            y = 108 + row * 92
            appear = (row * 3 + col) * 2
            approved = row == approved_row

            if approved:
                scale_keys = [
                    (appear, [58, 58, 100], EASE_OUT),
                    (appear + 12, [100, 100, 100], LINEAR),
                    (44, [100, 100, 100], EASE_IN_OUT),
                    (50, [108, 108, 100], EASE_IN_OUT),
                    (58, [100, 100, 100], LINEAR),
                ]
                opacity_keys = [(appear, 0, EASE_OUT), (appear + 10, 100, LINEAR)]
            else:
                scale_keys = [
                    (appear, [58, 58, 100], EASE_OUT),
                    (appear + 12, [100, 100, 100], LINEAR),
                ]
                opacity_keys = [
                    (appear, 0, EASE_OUT),
                    (appear + 10, 100, EASE_IN_OUT),
                    (38, 100, EASE_IN_OUT),
                    (48, 12, LINEAR),
                ]

            layers.append(
                shape_layer(
                    index=index,
                    name=f"tile-{row}-{col}",
                    shapes=[
                        group(
                            [
                                rounded_rect((78, 78), 22),
                                fill(colours[row][col]),
                            ],
                            name="tile",
                        )
                    ],
                    ks=transform(
                        pos=static([x, y]),
                        scale=animated(scale_keys),
                        opacity=animated(opacity_keys),
                    ),
                )
            )
            index += 1

    return composition("onboarding-apps", layers)


# ---------------------------------------------------------------------------
# 3. "Your data stays private" — a shield closes and three data points are
#    absorbed into it.
# ---------------------------------------------------------------------------
def build_privacy():
    layers = []

    # Shield with a keyhole.
    shield = shape_layer(
        index=1,
        name="shield",
        shapes=[
            group([shield_path(scale=1.0), fill(INDIGO)], name="body"),
            group([ellipse((30, 30), (0, -14)), fill(WHITE)], name="keyhole-top"),
            group([rounded_rect((13, 30), 6.5, (0, 14)), fill(WHITE)], name="keyhole-stem"),
        ],
        ks=transform(
            pos=static([200, 196]),
            scale=animated(
                [
                    (0, [0, 0, 100], EASE_OUT),
                    (20, [112, 112, 100], EASE_IN_OUT),
                    (30, [100, 100, 100], LINEAR),
                ]
            ),
            opacity=animated([(0, 0, LINEAR), (10, 100, LINEAR)]),
        ),
    )
    layers.append(shield)

    # Three "data points" absorbed by the shield.
    dots = [((66, 74), 0), ((48, 196), 6), ((72, 318), 12)]
    index = 2
    for (start_x, start_y), delay in dots:
        end_x = 200 + (start_x - 200) * 0.42
        end_y = 196 + (start_y - 196) * 0.42
        layers.append(
            shape_layer(
                index=index,
                name=f"dot-{index}",
                shapes=[group([ellipse((20, 20)), fill(TEAL)], name="dot")],
                ks=transform(
                    pos=animated(
                        [
                            (18 + delay, [start_x, start_y, 0], EASE_IN_OUT),
                            (44 + delay, [end_x, end_y, 0], LINEAR),
                        ]
                    ),
                    scale=animated(
                        [
                            (18 + delay, [40, 40, 100], EASE_OUT),
                            (30 + delay, [100, 100, 100], EASE_IN_OUT),
                            (44 + delay, [55, 55, 100], LINEAR),
                        ]
                    ),
                    opacity=animated(
                        [
                            (18 + delay, 0, EASE_OUT),
                            (26 + delay, 100, EASE_IN_OUT),
                            (44 + delay, 0, LINEAR),
                        ]
                    ),
                ),
            )
        )
        index += 1

    # Teal halo pulse.
    pulse_scale = []
    pulse_opacity = []
    for cycle in range(3):
        base = cycle * 20
        pulse_scale += [
            (base, [100, 100, 100], EASE_OUT),
            (base + 14, [122, 122, 100], LINEAR),
        ]
        pulse_opacity += [(base, 30, EASE_OUT), (base + 14, 0, LINEAR)]
    pulse_scale.append((FRAMES, [100, 100, 100], LINEAR))
    pulse_opacity.append((FRAMES, 30, LINEAR))

    layers.append(
        shape_layer(
            index=index,
            name="halo",
            shapes=[group([ellipse((300, 300)), stroke(TEAL, 5)], name="ring")],
            ks=transform(
                pos=static([200, 196]),
                scale=animated(pulse_scale),
                opacity=animated(pulse_opacity),
            ),
        )
    )

    return composition("onboarding-privacy", layers)


ANIMATIONS = {
    "onboarding_lend.json": build_lend,
    "onboarding_apps.json": build_apps,
    "onboarding_privacy.json": build_privacy,
}


def render(builder) -> str:
    return json.dumps(builder(), indent=1, ensure_ascii=False) + "\n"


def main(argv: list[str]) -> int:
    check_only = "--check" in argv
    RAW_DIR.mkdir(parents=True, exist_ok=True)

    stale = []
    for filename, builder in ANIMATIONS.items():
        path = RAW_DIR / filename
        expected = render(builder)
        if check_only:
            actual = path.read_text(encoding="utf-8") if path.exists() else ""
            if actual != expected:
                stale.append(filename)
            continue
        path.write_text(expected, encoding="utf-8")
        print(f"wrote {path.relative_to(ROOT)} ({len(expected) // 1024 or 1} KB)")

    if check_only:
        if stale:
            print("::error::Generated Lottie files are out of date:")
            for name in stale:
                print(f"  app/src/main/res/raw/{name}")
            print("Run: python3 scripts/assets/build_lottie.py")
            return 1
        print(f"All {len(ANIMATIONS)} Lottie files are up to date.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
