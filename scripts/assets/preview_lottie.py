#!/usr/bin/env python3
"""Renders preview frames of the onboarding Lottie files, without Android.

Why this exists
---------------
The animations are generated JSON that is only ever *seen* on a phone or in a CI
screenshot, which is a slow feedback loop for something as visual as a hand-off
scene. This tool rasterises the subset of Lottie that ``build_lottie.py`` emits
(shape layers, rounded rects, ellipses, polygons, fills, strokes, opacity,
position/scale/rotation keyframes) so a contributor can look at a contact sheet
before pushing.

It is deliberately a *preview*, not a renderer: easing is linearised, trim paths
and gradients are not supported (they are not emitted either), and anti-aliasing
is approximated by 2x supersampling. If a frame looks wrong here it is wrong in
the app; if it looks right here it still deserves one look on a device.

Usage
-----
    python3 scripts/assets/preview_lottie.py                 # all three, /tmp
    python3 scripts/assets/preview_lottie.py --frames 0,15,30,45,59
    python3 scripts/assets/preview_lottie.py app/src/main/res/raw/lend_hand.json

Requires Pillow (``pip install pillow``); it is not a build dependency.
"""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:  # pragma: no cover
    print("Pillow is required: pip install pillow", file=sys.stderr)
    raise SystemExit(2)

ROOT = Path(__file__).resolve().parents[2]
RAW_DIR = ROOT / "app" / "src" / "main" / "res" / "raw"
DEFAULT_FILES = ["lend_hand.json", "app_grid.json", "shield_key.json"]
SUPERSAMPLE = 2

# --------------------------------------------------------------------------
# Tiny affine algebra: (a, b, c, d, e, f) maps (x, y) -> (ax + cy + e, bx + dy + f)
# --------------------------------------------------------------------------
IDENTITY = (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def compose(m, n):
    """m applied after n."""
    a1, b1, c1, d1, e1, f1 = m
    a2, b2, c2, d2, e2, f2 = n
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


def apply(m, point):
    a, b, c, d, e, f = m
    x, y = point
    return (a * x + c * y + e, b * x + d * y + f)


def from_parts(pos, scale, rotate, anchor):
    px, py = pos
    sx, sy = scale
    ax, ay = anchor
    rad = math.radians(rotate)
    cos, sin = math.cos(rad), math.sin(rad)
    # T(pos) . R(rot) . S(scale) . T(-anchor)
    a = cos * sx
    b = sin * sx
    c = -sin * sy
    d = cos * sy
    e = px - (a * ax + c * ay)
    f = py - (b * ax + d * ay)
    return (a, b, c, d, e, f)


# --------------------------------------------------------------------------
# Property evaluation
# --------------------------------------------------------------------------
def value_at(prop, frame):
    """Static value, or a linearly interpolated keyframe value."""
    if prop is None:
        return 0
    if prop.get("a", 0) == 0:
        return prop.get("k")

    keyframes = prop["k"]
    if frame <= keyframes[0]["t"]:
        return keyframes[0]["s"]
    for previous, current in zip(keyframes, keyframes[1:]):
        if frame <= current["t"]:
            span = current["t"] - previous["t"]
            if span <= 0:
                return current["s"]
            t = (frame - previous["t"]) / span
            start, end = previous["s"], current["s"]
            if isinstance(start, list):
                return [s + (e - s) * t for s, e in zip(start, end)]
            return start + (end - start) * t
    return keyframes[-1]["s"]


def transform_of(ks, frame):
    pos = value_at(ks.get("p"), frame)
    scale = value_at(ks.get("s"), frame)
    anchor = value_at(ks.get("a"), frame)
    rotate = value_at(ks.get("r"), frame)
    opacity = value_at(ks.get("o"), frame)
    if not isinstance(pos, list):
        pos = [pos, pos]
    if not isinstance(scale, list):
        scale = [scale, scale]
    if not isinstance(anchor, list):
        anchor = [anchor, anchor]
    return from_parts(pos[:2], (scale[0] / 100.0, scale[1] / 100.0), rotate, anchor[:2]), opacity


# --------------------------------------------------------------------------
# Geometry
# --------------------------------------------------------------------------
def rounded_rect_points(size, radius, centre, steps=8):
    w, h = size
    cx, cy = centre
    rx = min(radius, w / 2.0, h / 2.0)
    left, right = cx - w / 2.0 + rx, cx + w / 2.0 - rx
    top, bottom = cy - h / 2.0 + rx, cy + h / 2.0 - rx
    corners = [
        (right, bottom, 0),
        (left, bottom, 90),
        (left, top, 180),
        (right, top, 270),
    ]
    points = []
    for px, py, start in corners:
        for step in range(steps + 1):
            angle = math.radians(start + 90 * step / steps)
            points.append((px + rx * math.cos(angle), py + rx * math.sin(angle)))
    return points


def ellipse_points(size, centre, steps=48):
    w, h = size
    cx, cy = centre
    return [
        (cx + (w / 2.0) * math.cos(2 * math.pi * i / steps),
         cy + (h / 2.0) * math.sin(2 * math.pi * i / steps))
        for i in range(steps)
    ]


def geometry_points(shape, frame):
    kind = shape["ty"]
    offset = value_at(shape.get("p"), frame) or [0, 0]
    if not isinstance(offset, list):
        offset = [offset, offset]
    if kind == "rc":
        return rounded_rect_points(value_at(shape["s"], frame), value_at(shape.get("r"), frame) or 0, offset)
    if kind == "el":
        return ellipse_points(value_at(shape["s"], frame), offset)
    if kind == "sh":
        return [tuple(v) for v in shape["ks"]["k"]["v"]]
    return []


# --------------------------------------------------------------------------
# Painting
# --------------------------------------------------------------------------
def _with_alpha(image, alpha):
    if alpha >= 0.999:
        return image
    r, g, b, a = image.split()
    a = a.point(lambda v: int(v * max(0.0, alpha)))
    return Image.merge("RGBA", (r, g, b, a))


def paint_group(items, matrix, alpha, canvas, scale, frame):
    geometries = [i for i in items if i.get("ty") in ("rc", "el", "sh")]
    paints = [i for i in items if i.get("ty") in ("fl", "st")]

    if geometries and paints:
        for paint in paints:
            colour = value_at(paint["c"], frame) or [0, 0, 0, 1]
            rgb = tuple(int(round(c * 255)) for c in colour[:3])
            paint_alpha = (value_at(paint.get("o"), frame) or 100) / 100.0
            if paint.get("ty") == "fl":
                paint_alpha *= colour[3] if len(colour) > 3 else 1.0
            total = alpha * paint_alpha
            if total <= 0.004:
                continue

            layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
            draw = ImageDraw.Draw(layer)
            width = None
            if paint["ty"] == "st":
                width = max(1, int(round((value_at(paint.get("w"), frame) or 1) * scale)))

            for geometry in geometries:
                points = [apply(matrix, p) for p in geometry_points(geometry, frame)]
                points = [(x * scale, y * scale) for x, y in points]
                if len(points) < 2:
                    continue
                if paint["ty"] == "fl":
                    draw.polygon(points, fill=rgb + (255,))
                else:
                    draw.line(points + [points[0]], fill=rgb + (255,), width=width, joint="curve")

            canvas.alpha_composite(_with_alpha(layer, total))

    for item in items:
        if item.get("ty") != "gr":
            continue
        tr = next((child for child in item["it"] if child.get("ty") == "tr"), None)
        if tr is None:
            continue
        transform, opacity = transform_of(tr, frame)
        paint_group(item["it"], compose(matrix, transform), alpha * opacity / 100.0, canvas, scale, frame)


def paint_layer(layer, frame, canvas, scale):
    transform, opacity = transform_of(layer["ks"], frame)
    paint_group(layer.get("shapes", []), transform, opacity / 100.0, canvas, scale, frame)


def render_frame(composition, frame):
    width, height = composition["w"], composition["h"]
    scale = SUPERSAMPLE
    canvas = Image.new("RGBA", (width * scale, height * scale), (0, 0, 0, 0))

    for layer in reversed(composition["layers"]):  # first layer in the file is on top
        start = layer.get("ip", 0)
        end = layer.get("op", composition["op"])
        if not (start <= frame < end):
            continue
        layer_canvas = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        paint_layer(layer, frame, layer_canvas, scale)
        canvas.alpha_composite(layer_canvas)

    return canvas.resize((width, height), Image.LANCZOS)


def contact_sheet(path, frames, out_path, background=(248, 250, 252, 255)):
    composition = json.loads(Path(path).read_text())
    tiles = [render_frame(composition, frame) for frame in frames]

    padding = 8
    width, height = composition["w"], composition["h"]
    sheet = Image.new(
        "RGBA",
        (len(tiles) * (width + padding) + padding, height + padding * 2 + 18),
        background,
    )
    draw = ImageDraw.Draw(sheet)
    for index, (frame, tile) in enumerate(zip(frames, tiles)):
        x = padding + index * (width + padding)
        tile_background = Image.new("RGBA", tile.size, background)
        tile_background.alpha_composite(tile)
        sheet.paste(tile_background, (x, padding))

    try:
        font = ImageFont.load_default()
    except Exception:  # pragma: no cover
        font = None
    for index, frame in enumerate(frames):
        x = padding + index * (width + padding)
        draw.text((x + 4, padding + height + 2), f"t={frame}", fill=(100, 116, 139), font=font)

    sheet.convert("RGB").save(out_path)
    return out_path


def main(argv):
    frames = [0, 12, 24, 36, 48, 58]
    for index, argument in enumerate(list(argv)):
        if argument == "--frames":
            frames = [int(v) for v in argv[index + 1].split(",")]
            del argv[index:index + 2]
            break

    paths = [Path(a) for a in argv if not a.startswith("-")] or [RAW_DIR / name for name in DEFAULT_FILES]

    out_dir = Path("/tmp/lottie-preview")
    out_dir.mkdir(parents=True, exist_ok=True)

    for path in paths:
        out = out_dir / f"{path.stem}.png"
        contact_sheet(path, frames, out)
        print(f"{path.name} -> {out}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
