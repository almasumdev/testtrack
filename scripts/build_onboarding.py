"""Turn the approved onboarding pictures (unDraw, recoloured) into Android vector drawables.

Source of truth: docs/design/onboarding/undraw/light/*.svg and undraw/dark-sticker/*.svg. Those are
made from undraw/original by undraw/recolor.py; this script only converts them and never edits a
colour. Day has no sticker border, night has the soft off-white sticker border.

    light        SVG -> app/src/main/res/drawable/onboarding_<page>.xml
    dark-sticker SVG -> app/src/main/res/drawable-night/onboarding_<page>.xml

Run:  python scripts/build_onboarding.py

Standard library only. It handles what these files use: svg, g (translate, scale), path, rect
(with rx), circle, ellipse, polygon, polyline, opacity, fill and stroke. Anything else stops the
build with a message, so a picture is never converted with a part silently dropped.

The viewBox crop is kept: a vector drawable always starts at 0,0, so the whole picture sits in a
group that moves the viewBox corner to the origin, and the viewport is the viewBox size.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs/design/onboarding/undraw"
RES = ROOT / "app/src/main/res"

#: Page order: drawable name used by OnboardingScreen.kt -> unDraw file name (without .svg).
PAGES = {
    "together": "true-friends_1h3v",
    "feedback": "online-review_08y6",
    "progress": "work-chat_kw8x",
    "share": "mobile-application_uc2q",
    "explore": "searching_pqji",
}

#: Source folder under undraw/ -> drawable out folder.
THEMES = {"light": "drawable", "dark-sticker": "drawable-night"}

#: The art is shown in a box of this size (dp). The drawable's own size is the picture scaled to fit.
BOX_W, BOX_H = 328.0, 330.0

SVG_NS = "{http://www.w3.org/2000/svg}"
NUMBER = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
ARGS = {"m": 2, "l": 2, "h": 1, "v": 1, "c": 6, "s": 4, "q": 4, "t": 2, "a": 7, "z": 0}

COMMON = {"transform", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "opacity"}
ALLOWED = {
    "path": COMMON | {"d"},
    "rect": COMMON | {"x", "y", "width", "height", "rx", "ry"},
    "circle": COMMON | {"cx", "cy", "r"},
    "ellipse": COMMON | {"cx", "cy", "rx", "ry"},
    "polygon": COMMON | {"points"},
    "polyline": COMMON | {"points"},
    "g": {"transform", "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin"},
}


def fmt(value: float) -> str:
    text = f"{round(value, 4):.4f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def normalize_path(d: str) -> str:
    """Rewrite path data so every number and arc flag is separated, which every parser accepts."""
    out: list[str] = []
    pos, n = 0, len(d)

    def skip() -> None:
        nonlocal pos
        while pos < n and (d[pos].isspace() or d[pos] == ","):
            pos += 1

    def number() -> float:
        nonlocal pos
        skip()
        m = NUMBER.match(d, pos)
        if not m:
            raise ValueError(f"bad path data near {d[pos:pos + 20]!r}")
        pos = m.end()
        return float(m.group())

    def flag() -> int:
        nonlocal pos
        skip()
        if d[pos] not in "01":
            raise ValueError(f"bad arc flag near {d[pos:pos + 20]!r}")
        pos += 1
        return int(d[pos - 1])

    cmd = ""
    while True:
        skip()
        if pos >= n:
            break
        if d[pos].isalpha():
            cmd = d[pos]
            pos += 1
            if cmd.lower() not in ARGS:
                raise ValueError(f"unsupported path command {cmd!r}")
            if cmd in "Zz":
                out.append(cmd)
                continue
        elif not cmd or cmd in "Zz":
            raise ValueError(f"number without a command near {d[pos:pos + 20]!r}")
        # Extra pairs after a moveto are implicit linetos.
        letter = cmd
        count = ARGS[cmd.lower()]
        values: list[str] = []
        for i in range(count):
            if cmd.lower() == "a" and i in (3, 4):
                values.append(str(flag()))
            else:
                values.append(fmt(number()))
        out.append(letter + " ".join(values))
        if cmd == "M":
            cmd = "L"
        elif cmd == "m":
            cmd = "l"
    return " ".join(out)


def rect_path(x: float, y: float, w: float, h: float, rx: float, ry: float) -> str:
    rx, ry = min(rx, w / 2), min(ry, h / 2)
    if rx <= 0 or ry <= 0:
        return f"M{fmt(x)} {fmt(y)} H{fmt(x + w)} V{fmt(y + h)} H{fmt(x)} Z"
    r, b = x + w, y + h
    arc = f"A{fmt(rx)} {fmt(ry)} 0 0 1 "
    return (
        f"M{fmt(x + rx)} {fmt(y)} H{fmt(r - rx)} {arc}{fmt(r)} {fmt(y + ry)} "
        f"V{fmt(b - ry)} {arc}{fmt(r - rx)} {fmt(b)} "
        f"H{fmt(x + rx)} {arc}{fmt(x)} {fmt(b - ry)} "
        f"V{fmt(y + ry)} {arc}{fmt(x + rx)} {fmt(y)} Z"
    )


def ellipse_path(cx: float, cy: float, rx: float, ry: float) -> str:
    return (
        f"M{fmt(cx - rx)} {fmt(cy)} A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx + rx)} {fmt(cy)} "
        f"A{fmt(rx)} {fmt(ry)} 0 1 0 {fmt(cx - rx)} {fmt(cy)} Z"
    )


def points_path(points: str, close: bool) -> str:
    nums = [float(v) for v in NUMBER.findall(points)]
    if len(nums) < 4 or len(nums) % 2:
        raise ValueError(f"bad points {points!r}")
    pairs = [f"{fmt(nums[i])} {fmt(nums[i + 1])}" for i in range(0, len(nums), 2)]
    return "M" + " L".join(pairs) + (" Z" if close else "")


def color(value: str) -> str | None:
    """#RRGGBB for a hex colour, None for 'none'. Names and rgb() are not used in these files."""
    value = value.strip()
    if value == "none":
        return None
    m = re.fullmatch(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})", value)
    if not m:
        raise ValueError(f"unsupported colour {value!r}")
    hexa = m.group(1)
    if len(hexa) == 3:
        hexa = "".join(c * 2 for c in hexa)
    return "#" + hexa.upper()


def parse_transform(text: str | None) -> tuple[float, float, float]:
    """translate(x y) with an optional scale(s), in that order. Returns (tx, ty, scale)."""
    tx = ty = 0.0
    scale = 1.0
    if not text:
        return tx, ty, scale
    rest = text.strip()
    parts = re.findall(r"(\w+)\(([^)]*)\)", rest)
    if re.sub(r"\s+", "", "".join(f"{n}({a})" for n, a in parts)) != re.sub(r"\s+", "", rest):
        raise ValueError(f"unsupported transform {text!r}")
    for i, (name, args) in enumerate(parts):
        nums = [float(v) for v in NUMBER.findall(args)]
        if name == "translate" and i == 0 and len(nums) in (1, 2):
            tx, ty = nums[0], nums[1] if len(nums) == 2 else 0.0
        elif name == "scale" and i == len(parts) - 1 and len(nums) == 1:
            scale = nums[0]
        else:
            raise ValueError(f"unsupported transform {text!r}")
    return tx, ty, scale


def group_open(tx: float, ty: float, scale: float, indent: str) -> str | None:
    if tx == 0 and ty == 0 and scale == 1:
        return None
    attrs = []
    if scale != 1:
        attrs += [f'android:scaleX="{fmt(scale)}"', f'android:scaleY="{fmt(scale)}"']
    if tx:
        attrs.append(f'android:translateX="{fmt(tx)}"')
    if ty:
        attrs.append(f'android:translateY="{fmt(ty)}"')
    return f"{indent}<group {' '.join(attrs)}>"


def paint(el: ET.Element, inherited: dict[str, str]) -> dict[str, str]:
    """The fill, stroke and opacity of an element as vector drawable attributes."""
    get = lambda key, default=None: el.get(key, inherited.get(key, default))
    out: dict[str, str] = {}
    fill = color(get("fill", "#000000"))
    stroke = color(get("stroke", "none"))
    opacity = float(el.get("opacity", "1"))
    if fill:
        out["android:fillColor"] = fill
        if opacity != 1:
            out["android:fillAlpha"] = fmt(opacity)
    if stroke:
        out["android:strokeColor"] = stroke
        out["android:strokeWidth"] = fmt(float(get("stroke-width", "1")))
        if get("stroke-linecap") in ("round", "square"):
            out["android:strokeLineCap"] = get("stroke-linecap")
        if get("stroke-linejoin") in ("round", "bevel"):
            out["android:strokeLineJoin"] = get("stroke-linejoin")
        if opacity != 1:
            out["android:strokeAlpha"] = fmt(opacity)
    if not fill and not stroke:
        raise ValueError(f"<{el.tag}> paints nothing")
    return out


def element_path(el: ET.Element, tag: str) -> str:
    num = lambda key, default=0.0: float(el.get(key, default))
    if tag == "path":
        return normalize_path(el.get("d", ""))
    if tag == "rect":
        rx = num("rx", el.get("ry", 0) or 0)
        ry = num("ry", rx)
        return rect_path(num("x"), num("y"), num("width"), num("height"), rx, ry)
    if tag == "circle":
        return ellipse_path(num("cx"), num("cy"), num("r"), num("r"))
    if tag == "ellipse":
        return ellipse_path(num("cx"), num("cy"), num("rx"), num("ry"))
    return points_path(el.get("points", ""), close=tag == "polygon")


def emit(el: ET.Element, inherited: dict[str, str], depth: int, lines: list[str]) -> None:
    tag = el.tag.removeprefix(SVG_NS)
    if tag not in ALLOWED:
        raise ValueError(f"unsupported element <{tag}>")
    extra = set(el.attrib) - ALLOWED[tag]
    if extra:
        raise ValueError(f"<{tag}> has unsupported attributes {sorted(extra)}")
    indent = "    " * depth
    opener = group_open(*parse_transform(el.get("transform")), indent)
    if opener:
        lines.append(opener)
        depth += 1
        indent = "    " * depth
    if tag == "g":
        merged = {**inherited, **{k: v for k, v in el.attrib.items() if k != "transform"}}
        for child in el:
            emit(child, merged, depth, lines)
    else:
        attrs = {"android:pathData": element_path(el, tag), **paint(el, inherited)}
        body = " ".join(f'{k}="{v}"' for k, v in attrs.items())
        lines.append(f"{indent}<path {body} />")
    if opener:
        lines.append("    " * (depth - 1) + "</group>")


def convert(svg_file: Path, source: str) -> str:
    root = ET.parse(svg_file).getroot()
    if root.tag != SVG_NS + "svg":
        raise ValueError("not an svg file")
    min_x, min_y, width, height = (float(v) for v in root.get("viewBox", "").replace(",", " ").split())
    scale = min(BOX_W / width, BOX_H / height)
    lines: list[str] = []
    shift = group_open(-min_x, -min_y, 1, "    ")
    depth = 1
    if shift:
        lines.append(shift)
        depth = 2
    for child in root:
        emit(child, {}, depth, lines)
    if shift:
        lines.append("    </group>")
    head = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        f"<!-- Generated by scripts/build_onboarding.py from {source}. Do not edit by hand. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{fmt(width * scale)}dp"\n'
        f'    android:height="{fmt(height * scale)}dp"\n'
        f'    android:viewportWidth="{fmt(width)}"\n'
        f'    android:viewportHeight="{fmt(height)}">\n'
    )
    return head + "\n".join(lines) + "\n</vector>\n"


def main() -> int:
    written = 0
    for theme, folder in THEMES.items():
        out_dir = RES / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        for name, stem in PAGES.items():
            svg = SRC / theme / f"{stem}.svg"
            if not svg.exists():
                print(f"missing {svg}", file=sys.stderr)
                return 1
            try:
                xml = convert(svg, f"{svg.relative_to(ROOT).as_posix()}")
            except ValueError as err:
                print(f"{svg.name}: {err}", file=sys.stderr)
                return 1
            target = out_dir / f"onboarding_{name}.xml"
            target.write_text(xml, encoding="utf-8", newline="\n")
            written += 1
            print(f"{target.relative_to(ROOT).as_posix()}  ({len(xml) // 1024} KB)")
    print(f"{written} drawables written")
    return 0


if __name__ == "__main__":
    sys.exit(main())
