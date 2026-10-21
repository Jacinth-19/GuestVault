#!/usr/bin/env python3
"""Structural checks for the Lottie animations in ``app/src/main/res/raw``.

The Android resource compiler does not validate JSON, so a malformed animation
compiles fine and then renders as nothing on a user's phone. These are the
invariants Lottie actually needs, checked on every push:

1. Top-level composition fields exist and have sane values.
2. Every layer is a shape layer with a complete transform block.
3. Every keyframe list is time-ordered and inside the composition window.
4. Colours are four-component and within 0..1.
5. Only shape types this project authors are present (so a hand-edit cannot
   smuggle in an unsupported construct).
6. Files stay small enough to justify shipping them in the APK.
7. Every file is reproducible from ``scripts/assets/build_lottie.py``, and no
   orphan animation is sitting in the APK unused.

Usage: check_lottie.py [--no-regen]
"""

from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"
MAX_BYTES = 120 * 1024

SHAPE_TYPES = {"gr", "rc", "el", "sh", "fl", "st", "tr", "tm"}
REQUIRED_COMPOSITION = ("v", "fr", "ip", "op", "w", "h", "layers")
REQUIRED_TRANSFORM = ("o", "r", "p", "a", "s")

# Animations referenced from Kotlin. Keep in sync with ui/screens/onboarding.
REFERENCED = {
    "onboarding_lend",
    "onboarding_apps",
    "onboarding_privacy",
}


class Invalid(Exception):
    pass


def check_colour(value, where):
    if not isinstance(value, list) or len(value) not in (3, 4):
        raise Invalid(f"{where}: colour is not an RGB(A) array")
    for channel in value:
        if not isinstance(channel, (int, float)) or not 0.0 <= channel <= 1.0:
            raise Invalid(f"{where}: colour channel {channel!r} is outside 0..1")


def check_property(prop, where, ip, op):
    """Validates one Lottie property block (animated or static)."""
    if not isinstance(prop, dict) or "a" not in prop or "k" not in prop:
        raise Invalid(f"{where}: property is missing 'a'/'k'")
    if prop["a"] == 0:
        return
    keyframes = prop["k"]
    if not isinstance(keyframes, list) or len(keyframes) < 2:
        raise Invalid(f"{where}: animated property needs at least two keyframes")
    previous = None
    for frame in keyframes:
        if not isinstance(frame, dict) or "t" not in frame or "s" not in frame:
            raise Invalid(f"{where}: keyframe is missing 't'/'s'")
        time = frame["t"]
        if not isinstance(time, (int, float)):
            raise Invalid(f"{where}: keyframe time is not a number")
        if time < ip or time > op + 1:
            raise Invalid(f"{where}: keyframe at {time} is outside {ip}..{op}")
        if previous is not None and time <= previous:
            raise Invalid(f"{where}: keyframe times are not increasing ({previous} -> {time})")
        previous = time


def walk_shapes(shapes, where, ip, op):
    if not isinstance(shapes, list) or not shapes:
        raise Invalid(f"{where}: layer has no shapes")
    for index, shape in enumerate(shapes):
        kind = shape.get("ty")
        if kind not in SHAPE_TYPES:
            raise Invalid(f"{where}: unexpected shape type {kind!r}")
        if kind == "gr":
            items = shape.get("it")
            if not isinstance(items, list) or not items:
                raise Invalid(f"{where}: group {index} has no items")
            walk_shapes(items, f"{where}.group[{index}]", ip, op)
        elif kind == "fl":
            check_colour(shape.get("c", {}).get("k"), f"{where}.fill[{index}]")
            check_property(shape.get("o"), f"{where}.fill[{index}].opacity", ip, op)
        elif kind == "st":
            check_colour(shape.get("c", {}).get("k"), f"{where}.stroke[{index}]")
            check_property(shape.get("o"), f"{where}.stroke[{index}].opacity", ip, op)
            check_property(shape.get("w"), f"{where}.stroke[{index}].width", ip, op)
        elif kind == "tr":
            for key in ("p", "a", "s", "r", "o"):
                check_property(shape.get(key), f"{where}.transform.{key}", ip, op)
        elif kind == "sh":
            path = shape.get("ks", {})
            if path.get("a") != 0:
                raise Invalid(f"{where}: shield path must be static")
            data = path.get("k", {})
            for key in ("i", "o", "v"):
                if not isinstance(data.get(key), list):
                    raise Invalid(f"{where}: path is missing '{key}'")
            if len(data["i"]) != len(data["v"]) or len(data["o"]) != len(data["v"]):
                raise Invalid(f"{where}: path tangents and vertices differ in length")
            if len(data["v"]) < 3:
                raise Invalid(f"{where}: path needs at least three vertices")
        else:  # rc / el / tm: validate their animatable properties
            for key in ("s", "p", "r", "o"):
                if key in shape:
                    check_property(shape[key], f"{where}.{kind}.{key}", ip, op)


def check_file(path: Path) -> dict:
    if path.stat().st_size > MAX_BYTES:
        raise Invalid(f"{path.name}: {path.stat().st_size} bytes exceeds {MAX_BYTES}")

    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        raise Invalid(f"{path.name}: not valid JSON ({error})") from error

    for key in REQUIRED_COMPOSITION:
        if key not in data:
            raise Invalid(f"{path.name}: missing top-level '{key}'")

    if data["w"] <= 0 or data["h"] <= 0:
        raise Invalid(f"{path.name}: invalid canvas {data['w']}x{data['h']}")
    if data["op"] <= data["ip"]:
        raise Invalid(f"{path.name}: out frame must be after in frame")
    if not 1 <= data["fr"] <= 120:
        raise Invalid(f"{path.name}: implausible frame rate {data['fr']}")

    layers = data["layers"]
    if not isinstance(layers, list) or not layers:
        raise Invalid(f"{path.name}: no layers")

    for index, layer in enumerate(layers):
        where = f"{path.name}.layer[{index}]"
        if layer.get("ty") != 4:
            raise Invalid(f"{where}: only shape layers are supported")
        ks = layer.get("ks")
        if not isinstance(ks, dict):
            raise Invalid(f"{where}: missing transform block")
        for key in REQUIRED_TRANSFORM:
            check_property(ks.get(key), f"{where}.{key}", data["ip"], data["op"])
        for key in REQUIRED_TRANSFORM:
            if ks[key]["a"] == 0 and not isinstance(ks[key]["k"], (int, float, list)):
                raise Invalid(f"{where}.{key}: static value has an unexpected type")
        walk_shapes(layer.get("shapes"), where, data["ip"], data["op"])

    return data


def main(argv: list[str]) -> int:
    if not RAW_DIR.is_dir():
        print(f"::error::No resource directory at {RAW_DIR}")
        return 1

    files = sorted(RAW_DIR.glob("*.json"))
    if not files:
        print("::error::No Lottie animations found.")
        return 1

    failures: list[str] = []
    for path in files:
        try:
            data = check_file(path)
        except Invalid as error:
            failures.append(str(error))
            continue
        print(
            f"ok  {path.name:32} {data['w']}x{data['h']} "
            f"{data['fr']}fps {len(data['layers'])} layers "
            f"{path.stat().st_size // 1024} KB"
        )

    present = {path.stem for path in files}
    for name in sorted(REFERENCED - present):
        failures.append(f"missing animation referenced by the app: {name}.json")
    for name in sorted(present - REFERENCED):
        failures.append(
            f"unreferenced animation ships in the APK: {name}.json "
            f"(add it to REFERENCED if the onboarding screen uses it)"
        )

    if "--no-regen" not in argv:
        generated = subprocess.run(
            [sys.executable, str(ROOT / "scripts" / "assets" / "build_lottie.py"), "--check"],
            capture_output=True,
            text=True,
        )
        if generated.returncode != 0:
            failures.append(
                (generated.stdout + generated.stderr).strip()
                or "generated Lottie files are out of date"
            )

    if failures:
        for failure in failures:
            print(f"::error::{failure}")
        return 1

    print(f"\nAll {len(files)} animations are structurally valid and up to date.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
