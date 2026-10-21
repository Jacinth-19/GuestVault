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
  actually needs (keyframe order, colour ranges, allowed shape types),
* ``scripts/assets/preview_lottie.py`` can rasterise a contact sheet, so the
  scenes are reviewed with eyes instead of imagination.

The three scenes
----------------
``lend_hand.json``   A phone passes from one hand to another; a shield with a
                     keyhole locks over it as it lands.
``app_grid.json``    Nine app tiles appear; the unapproved six dim and grow a
                     padlock, the approved row stays lit and gets a ring.
``shield_key.json``  A shield closes with a keyhole; a key spins in and docks.

Everything is authored from primitives (rounded rects, ellipses, one polygon
shield) so nothing depends on an external asset or a licence we do not control.
See ``docs/ASSET_LICENCES.md``.

Usage
-----
    python3 scripts/assets/build_lottie.py            # write the JSON files
    python3 scripts/assets/build_lottie.py --check    # fail if out of date
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
FRAMES = 60  # two seconds

# Palette, normalised for Lottie (0..1). Mirrors ui/theme/Color.kt.
INDIGO = [0.192, 0.180, 0.506, 1.0]        # #312E81
INDIGO_LIGHT = [0.647, 0.706, 0.988, 1.0]  # #A5B4FC
TEAL = [0.051, 0.580, 0.533, 1.0]          # #0D9488
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


def _prop(value, default):
    """Accepts a raw value, a static block or an animated block."""
    if isinstance(value, dict):
        return value
    return static(default if value is None else value)


def transform(pos=(0, 0), scale=(100, 100), rotate=0, opacity=100, anchor=(0, 0)):
    return {
        "o": _prop(opacity, 100),
        "r": _prop(rotate, 0),
        "p": _prop(list(pos) if isinstance(pos, tuple) else pos, [0, 0]),
        "a": static(list(anchor)),
        "s": _prop(list(scale) if isinstance(scale, tuple) else scale, [100, 100]),
    }


def group(items, name="group", pos=(0, 0), scale=(100, 100), rotate=0, opacity=100):
    """A shape group.

    ORDER MATTERS, and the opposite of CSS: Lottie paints a group's children
    from the *end* of the list backwards (``ContentGroup.draw`` walks
    ``contents`` in reverse), so the **first** item is painted last and appears
    on top. Every composite below is therefore authored top-most first. Getting
    this wrong is silent — the shield simply swallows its own keyhole.
    """
    items = list(items) + [
        {
            "ty": "tr",
            "p": _prop(list(pos) if isinstance(pos, tuple) else pos, [0, 0]),
            "a": static([0, 0]),
            "s": _prop(list(scale) if isinstance(scale, tuple) else scale, [100, 100]),
            "r": _prop(rotate, 0),
            "o": _prop(opacity, 100),
            "sk": static(0),
            "sa": static(0),
            "nm": "transform",
        }
    ]
    return {"ty": "gr", "nm": name, "it": items}


def rounded_rect(size, radius, pos=(0, 0), name="rect"):
    """Lottie ``rc``: size, corner radius and position. Rotation belongs on the
    enclosing group's transform, which is the only place Lottie supports it."""
    return {
        "ty": "rc",
        "nm": name,
        "d": 1,
        "s": _prop(list(size) if isinstance(size, tuple) else size, [0, 0]),
        "p": _prop(list(pos) if isinstance(pos, tuple) else pos, [0, 0]),
        "r": _prop(radius, 0),
    }


def ellipse(size, pos=(0, 0), name="ellipse"):
    return {
        "ty": "el",
        "nm": name,
        "s": _prop(list(size) if isinstance(size, tuple) else size, [0, 0]),
        "p": _prop(list(pos) if isinstance(pos, tuple) else pos, [0, 0]),
    }


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
        "o": _prop(opacity, 100),
        "r": 1,
        "bm": 0,
    }


def stroke(color, width, opacity=100, name="stroke"):
    return {
        "ty": "st",
        "nm": name,
        "c": static(color),
        "o": _prop(opacity, 100),
        "w": _prop(width, 1),
        "lc": 2,
        "lj": 2,
        "ml": 4,
        "bm": 0,
    }


def keyhole(color=WHITE, scale=1.0):
    """The shield's keyhole: a round bow above a tapered stem."""
    # Bow above a tapered stem, both white on the shield's indigo.
    return [
        group([ellipse((26 * scale, 26 * scale)), fill(color)], name="keyhole-bow"),
        group(
            [rounded_rect((12 * scale, 26 * scale), 5 * scale, (0, 13 * scale)), fill(color)],
            name="keyhole-stem",
        ),
    ]


def padlock(color=INDIGO, scale=1.0):
    """A small padlock: a shackle ring whose bottom half is hidden by the body."""
    # Body first so it covers the bottom half of the shackle, leaving an arc.
    return [
        group(
            [rounded_rect((34 * scale, 26 * scale), 7 * scale, (0, 6 * scale)), fill(color)],
            name="body",
        ),
        group(
            [ellipse((26 * scale, 26 * scale), (0, -7 * scale)), stroke(color, 5.5 * scale)],
            name="shackle",
        ),
    ]


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


def halo(center=(200, 196), size=(286, 286), color=INDIGO_LIGHT, opacity=14, index=99):
    """Static soft disc that makes the scene sit on white or on dark surfaces."""
    return shape_layer(
        index=index,
        name="halo",
        shapes=[group([ellipse(size), fill(color, opacity=opacity)], name="halo-fill")],
        ks=transform(pos=static(list(center))),
    )


# ---------------------------------------------------------------------------
# 1. lend_hand.json — a phone passes from one hand to another, then a shield
#    with a keyhole locks over it.
# ---------------------------------------------------------------------------
# #C7D2FE (indigo-200): soft on white, but still reads as a hand at 232 dp.
HAND_SKIN = [0.780, 0.824, 0.996, 1.0]
HAND_EDGE = [0.612, 0.678, 0.988, 1.0]


def hand(direction=1):
    """A hand gripping the phone, seen from behind. ``direction`` = +1 is left.

    Deliberately three shapes and no fingers. Four separate finger capsules with
    outlines read as a comb (or a mitten) at 232 dp; a palm that *overlaps* the
    phone plus a thumb laid across its edge reads as "holding" at a glance, which
    is the whole point of the illustration. The wrist capsule runs off-canvas so
    the arm disappears into the edge of the frame.
    """
    skin = fill(HAND_SKIN)
    edge = stroke(HAND_EDGE, 3)

    return [
        # Thumb, across the front of the phone.
        group(
            [group([rounded_rect((26, 46), 13), skin, edge], name="thumb-body")],
            name="thumb",
            pos=(direction * 30, -14),
            rotate=direction * -26,
        ),
        # Palm: overlaps the phone edge, so the grip is unambiguous.
        group([rounded_rect((64, 92), 30), skin, edge], name="palm"),
        # Wrist, tapering off towards the edge of the canvas.
        group([rounded_rect((92, 46), 23), skin], name="wrist", pos=(-direction * 52, 8)),
    ]


def build_lend():
    layers = []

    # Shield badge, topmost: springs onto the phone once it lands on the right.
    layers.append(
        shape_layer(
            index=1,
            name="shield-badge",
            shapes=[
                # Keyhole first: first item paints last, i.e. on top of the shield.
                group(keyhole(WHITE, scale=0.72), name="keyhole", pos=(0, -2)),
                group([shield_path(scale=0.58), fill(INDIGO)], name="shield"),
            ],
            ks=transform(
                pos=static([214, 172]),
                scale=animated(
                    [
                        (34, [0, 0, 100], EASE_OUT),
                        (45, [108, 108, 100], EASE_IN_OUT),
                        (52, [100, 100, 100], LINEAR),
                    ]
                ),
                opacity=animated([(34, 0, LINEAR), (39, 100, LINEAR)]),
            ),
        )
    )

    # Flash ring when the shield locks in.
    layers.append(
        shape_layer(
            index=2,
            name="lock-pulse",
            shapes=[group([ellipse((230, 230)), stroke(WHITE, 5)], name="ring")],
            ks=transform(
                pos=static([214, 172]),
                scale=animated([(40, [62, 62, 100], EASE_OUT), (58, [122, 122, 100], LINEAR)]),
                opacity=animated([(40, 0, EASE_OUT), (46, 42, EASE_IN_OUT), (58, 0, LINEAR)]),
            ),
        )
    )

    # Both hands sit *in front* of the phone, gripping its lower edge, which is
    # what makes the scene read as a hand-off rather than a floating rectangle.
    layers.append(
        shape_layer(
            index=3,
            name="hand-left",
            shapes=hand(direction=1),
            ks=transform(
                pos=animated(
                    [
                        (0, [112, 208, 0], EASE_OUT),
                        (12, [116, 206, 0], EASE_IN_OUT),
                        (34, [130, 208, 0], EASE_IN_OUT),
                        (46, [126, 208, 0], LINEAR),
                    ]
                ),
                rotate=animated([(0, -6, EASE_OUT), (34, -2, LINEAR)]),
                opacity=animated([(0, 0, LINEAR), (8, 100, LINEAR)]),
            ),
        )
    )
    layers.append(
        shape_layer(
            index=4,
            name="hand-right",
            shapes=hand(direction=-1),
            ks=transform(
                pos=animated(
                    [
                        (0, [330, 208, 0], EASE_OUT),
                        (12, [322, 206, 0], EASE_IN_OUT),
                        (34, [286, 208, 0], EASE_IN_OUT),
                        (46, [290, 208, 0], LINEAR),
                    ]
                ),
                rotate=animated([(0, 6, EASE_OUT), (34, 2, LINEAR)]),
                opacity=animated([(0, 0, LINEAR), (8, 100, LINEAR)]),
            ),
        )
    )

    # The phone itself: enters with the left hand, travels to the right hand.
    layers.append(
        shape_layer(
            index=5,
            name="phone",
            # Top-most first: outline, speaker, screen tint, then the body.
            shapes=[
                group([rounded_rect((112, 196), 24), stroke(INDIGO, 8)], name="edge"),
                group([rounded_rect((34, 6), 3, (0, -78)), fill(INDIGO_LIGHT)], name="speaker"),
                group([rounded_rect((86, 150), 14, (0, -4)), fill(INDIGO, opacity=10)], name="screen"),
                group([rounded_rect((112, 196), 24), fill(WHITE)], name="body"),
            ],
            ks=transform(
                pos=animated(
                    [
                        (0, [150, 196, 0], EASE_IN_OUT),
                        (10, [158, 194, 0], EASE_IN_OUT),
                        (22, [186, 190, 0], EASE_IN_OUT),
                        (34, [212, 188, 0], EASE_IN_OUT),
                        (44, [214, 188, 0], LINEAR),
                    ]
                ),
                rotate=animated(
                    [
                        (0, -10, EASE_IN_OUT),
                        (22, 4, EASE_IN_OUT),
                        (34, 8, EASE_IN_OUT),
                        (44, 0, LINEAR),
                    ]
                ),
                opacity=animated([(0, 0, LINEAR), (6, 100, LINEAR)]),
            ),
        )
    )

    layers.append(halo())
    return composition("onboarding-lend-hand", layers)


# ---------------------------------------------------------------------------
# 2. app_grid.json — nine tiles; the six unapproved ones dim and lock, the
#    approved row stays lit and is ringed.
# ---------------------------------------------------------------------------
def build_apps():
    layers = []

    # Approval ring around the middle row.
    layers.append(
        shape_layer(
            index=1,
            name="approval-ring",
            shapes=[group([rounded_rect((300, 112), 30), stroke(TEAL, 6)], name="ring")],
            ks=transform(
                pos=static([200, 200]),
                scale=animated([(40, [92, 92, 100], EASE_OUT), (52, [100, 100, 100], LINEAR)]),
                opacity=animated([(40, 0, EASE_OUT), (52, 100, LINEAR)]),
            ),
        )
    )

    approved_row = 1
    index = 2

    # Padlocks sit above their tiles, so they get their own layers and stagger.
    for row in range(3):
        if row == approved_row:
            continue
        for col in range(3):
            delay = (row * 3 + col) * 2
            layers.append(
                shape_layer(
                    index=index,
                    name=f"lock-{row}-{col}",
                    shapes=[group(padlock(INDIGO, scale=0.62), name="padlock")],
                    ks=transform(
                        pos=static([108 + col * 92, 108 + row * 92]),
                        scale=animated(
                            [
                                (32 + delay, [40, 40, 100], EASE_OUT),
                                (44 + delay, [100, 100, 100], LINEAR),
                            ]
                        ),
                        opacity=animated(
                            [
                                (30 + delay, 0, EASE_OUT),
                                (38 + delay, 70, LINEAR),
                            ]
                        ),
                    ),
                )
            )
            index += 1

    # Tiles, bottom-to-top in z so the first row reads as "in front".
    for row in range(2, -1, -1):
        for col in range(2, -1, -1):
            x = 108 + col * 92
            y = 108 + row * 92
            appear = (row * 3 + col) * 2
            approved = row == approved_row
            colour = TEAL if approved else INDIGO

            scale_keys = [
                (appear, [58, 58, 100], EASE_OUT),
                (appear + 12, [100, 100, 100], EASE_IN_OUT),
            ]
            opacity_keys = [(appear, 0, EASE_OUT), (appear + 10, 100, EASE_IN_OUT)]

            if approved:
                scale_keys += [(44, [100, 100, 100], EASE_IN_OUT), (50, [108, 108, 100], EASE_IN_OUT), (58, [100, 100, 100], LINEAR)]
            else:
                opacity_keys += [(30, 100, EASE_IN_OUT), (42, 16, LINEAR)]

            layers.append(
                shape_layer(
                    index=index,
                    name=f"tile-{row}-{col}",
                    shapes=[group([rounded_rect((78, 78), 22), fill(colour)], name="tile")],
                    ks=transform(
                        pos=static([x, y]),
                        scale=animated(scale_keys),
                        opacity=animated(opacity_keys),
                    ),
                )
            )
            index += 1

    layers.append(halo(size=(300, 300), opacity=12))
    return composition("onboarding-app-grid", layers)


# ---------------------------------------------------------------------------
# 3. shield_key.json — a shield closes with a keyhole; a key spins in and docks.
# ---------------------------------------------------------------------------
def key(colour=INDIGO):
    """A key whose origin is the centre of its bow, shaft pointing at +x."""
    # The ring sits above the shaft so the join reads as one object.
    return [
        group([ellipse((58, 58)), stroke(colour, 10)], name="bow-ring", pos=(0, 0)),
        group([ellipse((22, 22)), fill(colour)], name="bow-hole", pos=(0, 0)),
        group([rounded_rect((82, 13), 6, (58, 0)), fill(colour)], name="shaft"),
        group([rounded_rect((14, 26), 4, (92, -12)), fill(colour)], name="tooth-outer"),
        group([rounded_rect((12, 20), 4, (72, -9)), fill(colour)], name="tooth-inner"),
    ]


def build_privacy():
    layers = []

    # Dock flash: a ring that expands from the keyhole as the key seats.
    layers.append(
        shape_layer(
            index=1,
            name="dock-flash",
            shapes=[group([ellipse((180, 180)), stroke(TEAL, 6)], name="ring")],
            ks=transform(
                pos=static([200, 196]),
                scale=animated([(42, [30, 30, 100], EASE_OUT), (58, [104, 104, 100], LINEAR)]),
                opacity=animated([(42, 0, EASE_OUT), (47, 55, EASE_IN_OUT), (58, 0, LINEAR)]),
            ),
        )
    )

    # The key: spins while it approaches, then shrinks into the keyhole.
    layers.append(
        shape_layer(
            index=2,
            name="key",
            shapes=[group(key(INDIGO), name="key")],
            ks=transform(
                pos=animated(
                    [
                        (12, [344, 104, 0], EASE_IN_OUT),
                        (30, [268, 164, 0], EASE_IN_OUT),
                        (42, [200, 196, 0], LINEAR),
                    ]
                ),
                rotate=animated(
                    [
                        (12, 0, EASE_IN_OUT),
                        (30, -420, EASE_IN_OUT),
                        (42, -540, LINEAR),
                    ]
                ),
                scale=animated(
                    [
                        (12, [62, 62, 100], EASE_IN_OUT),
                        (30, [58, 58, 100], EASE_IN_OUT),
                        (42, [22, 22, 100], LINEAR),
                    ]
                ),
                opacity=animated(
                    [
                        (12, 0, LINEAR),
                        (18, 100, EASE_IN_OUT),
                        (42, 100, EASE_IN_OUT),
                        (46, 0, LINEAR),
                    ]
                ),
            ),
        )
    )

    # The shield, closing: a slight overshoot then settle.
    layers.append(
        shape_layer(
            index=3,
            name="shield",
            shapes=[
                # Keyhole above the shield body (see group() ordering note).
                group(keyhole(WHITE, scale=1.0), name="keyhole", pos=(0, 2)),
                group([shield_path(scale=1.0), fill(INDIGO)], name="body"),
            ],
            ks=transform(
                pos=static([200, 196]),
                scale=animated(
                    [
                        (0, [72, 72, 100], EASE_OUT),
                        (16, [104, 104, 100], EASE_IN_OUT),
                        (26, [100, 100, 100], LINEAR),
                    ]
                ),
                rotate=animated([(0, -6, EASE_OUT), (26, 0, LINEAR)]),
                opacity=animated([(0, 0, LINEAR), (10, 100, LINEAR)]),
            ),
        )
    )

    layers.append(halo())
    return composition("onboarding-shield-key", layers)


ANIMATIONS = {
    "lend_hand.json": build_lend,
    "app_grid.json": build_apps,
    "shield_key.json": build_privacy,
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
