"""Prepare and recolour the unDraw onboarding art for TestTrack, light and dark.

    python docs/design/onboarding/undraw/recolor.py

Reads original/<name>.svg (as downloaded from cdn.undraw.co, untouched), applies the
edits in PAGES (drop clutter and floors, mark hair and light clothing, restyle a few
shapes, move people, add the few app details a page needs), frames the result so every
standing figure comes out the same height, then writes light/<name>.svg and
dark/<name>.svg through one colour map per theme. A colour the maps do not know is
reported, so a new file never slips through half done.

Shapes are addressed by their order in the file: the n-th path, circle, ellipse, rect,
polygon, polyline or line. The originals never change, so the numbers hold. The script
checks each file's shape count before it edits anything.
"""

import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Colours of our own, put into the art by PAGES and coloured per theme below.
HAIR = "#2b2220"  # hair, marked shape by shape, so night keeps it dark instead of grey
MINT = "#3fb39b"  # the one mint touch: tonight's night on page 3
CLOTH = "#c8ced3"  # light clothing: pale by day, a soft mid grey at night so it never sinks into the page

# Skin and lips: the same in both themes.
KEEP = {"#ed9da0", "#9f616a", "#a0616a", "#ffb6b6", "#ffb8b8", "#ff6884"}

LIGHT = {
    "#6c63ff": "#32729B",  # unDraw's accent becomes robot blue
    # unDraw's purple-tinted inks move to the app's blue-tinted ink, so nothing reads violet
    "#090814": "#0F1C26",
    "#2f2e41": "#22303C",
    "#2f2e43": "#22303C",
    "#3f3d56": "#354452",
    "#b6b3c5": "#AFB9C2",
    "#d6d6e3": "#D5DDE4",
    HAIR: "#1A1F26",
    MINT: "#38A68C",
    CLOTH: "#E6E6E6",
    # whites and greys stay, they already sit well on #F7FCFF
    "#ffffff": "#ffffff",
    "#f2f2f2": "#f2f2f2",
    "#f1f1f1": "#f1f1f1",
    "#f0f0f0": "#f0f0f0",
    "#e6e6e6": "#e6e6e6",
    "#e4e4e4": "#e4e4e4",
    "#e0e0e0": "#e0e0e0",
    "#cccccc": "#cccccc",
    "#cbcbcb": "#cbcbcb",
    "#cacaca": "#cacaca",
    "#6f6f6f": "#6f6f6f",
}

DARK = {
    "#6c63ff": "#5B93B8",
    # near-black clothes and device frames: lifted to a mid-dark grey so they show on #0F1011
    "#090814": "#4A5158",
    "#2f2e41": "#555C63",
    "#2f2e43": "#555C63",
    # the mid ink used for text lines and dark clothes becomes a soft grey
    "#3f3d56": "#646D75",
    "#6f6f6f": "#7A838B",
    "#b6b3c5": "#545B62",
    # hair stays hair: a deep brown that still separates from the page, never grey
    HAIR: "#3D2F2B",
    MINT: "#68B8A3",
    CLOTH: "#6B737B",
    # white and the lightest greys become surfaces one step above the page
    "#ffffff": "#22262A",
    "#f2f2f2": "#2A2E33",
    "#f1f1f1": "#2A2E33",
    "#f0f0f0": "#2A2E33",
    # light greys are edges: a little lighter than the surfaces they frame
    "#e6e6e6": "#33383E",
    "#e4e4e4": "#33383E",
    "#e0e0e0": "#33383E",
    "#d6d6e3": "#3A3F45",
    "#cccccc": "#444A50",
    "#cbcbcb": "#444A50",
    "#cacaca": "#444A50",
}

LINK = (  # Lucide's link glyph, 24 units
    '<path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/>'
    '<path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/>'
)
CHECK = 'fill="none" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"'


def calendar(cx: float, bottom: float) -> str:
    """Page 3: a light calendar card of 14 nights. Eight done, tonight in mint, five to come."""
    size, gap, pad = 36, 10, 22
    w = 7 * size + 6 * gap + 2 * pad
    h = 2 * size + gap + 2 * pad + 14
    x0, y0 = cx - w / 2, bottom - h
    out = [
        f'<rect x="{x0}" y="{y0}" width="{w}" height="{h}" rx="16" fill="#fff" stroke="#e6e6e6" stroke-width="3"/>',
        f'<rect x="{x0 + w * .3 - 4}" y="{y0 - 12}" width="8" height="24" rx="4" fill="#3f3d56"/>',
        f'<rect x="{x0 + w * .7 - 4}" y="{y0 - 12}" width="8" height="24" rx="4" fill="#3f3d56"/>',
    ]
    for n in range(14):
        x = x0 + pad + (n % 7) * (size + gap)
        y = y0 + pad + 14 + (n // 7) * (size + gap)
        tick = f'<path d="M{x + 10} {y + 18.5}l6 6 11-12" {CHECK} stroke="{{}}"/>'
        if n < 8:
            out.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="9" fill="#6c63ff" opacity=".2"/>')
            out.append(tick.format("#6c63ff"))
        elif n == 8:
            out.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="9" fill="{MINT}"/>')
            out.append(tick.format("#fff"))
        else:
            out.append(f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="9" fill="#f2f2f2"/>')
    return "".join(out)


def phone(x: float, y: float, w: float = 38, h: float = 68) -> str:
    """A small phone in a hand: dark frame, blue screen."""
    return (
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="7" fill="#090814"/>'
        f'<rect x="{x + 4}" y="{y + 8}" width="{w - 8}" height="{h - 16}" rx="3" fill="#6c63ff"/>'
    )


# Page 1: the swap mark between the two heads, the app icon's idea.
SWAP = (
    '<g fill="none" stroke="#6c63ff" stroke-width="9" stroke-linecap="round" stroke-linejoin="round">'
    '<path d="M352 48H428M414 34l14 14-14 14"/><path d="M428 86H352M366 72l-14 14 14 14"/></g>'
)


def play_link_screen() -> str:
    """Page 4: the phone shows the Play link pasted, the app found, and the two next steps."""
    return "".join(
        [
            '<rect x="248" y="96" width="170" height="18" rx="9" fill="#3f3d56"/>',
            '<rect x="248" y="126" width="230" height="12" rx="6" fill="#e6e6e6"/>',
            # the link field, focused
            '<rect x="248" y="168" width="306" height="64" rx="32" fill="#fff" stroke="#6c63ff" stroke-width="4"/>',
            f'<g transform="translate(266 182) scale(1.5)" fill="none" stroke="#6c63ff" stroke-width="2.4" '
            f'stroke-linecap="round" stroke-linejoin="round">{LINK}</g>',
            '<rect x="312" y="194" width="186" height="12" rx="6" fill="#cccccc"/>',
            '<rect x="510" y="184" width="4" height="32" rx="2" fill="#3f3d56"/>',
            # the app it found
            '<rect x="248" y="266" width="96" height="96" rx="24" fill="#3f3d56"/>',
            '<circle cx="296" cy="314" r="27" fill="#fff"/>',
            '<path d="M296 314v-27a27 27 0 0 1 27 27Z" fill="#6c63ff"/>',
            '<rect x="364" y="290" width="150" height="16" rx="8" fill="#3f3d56"/>',
            '<rect x="364" y="320" width="110" height="12" rx="6" fill="#e6e6e6"/>',
            # take a crew seat, ask for feedback
            '<rect x="248" y="404" width="306" height="60" rx="30" fill="#6c63ff"/>',
            '<rect x="331" y="428" width="140" height="12" rx="6" fill="#fff"/>',
            '<rect x="248" y="484" width="306" height="60" rx="30" fill="#fff" stroke="#6c63ff" stroke-width="3"/>',
            '<rect x="341" y="508" width="120" height="12" rx="6" fill="#6c63ff" opacity=".55"/>',
        ]
    )


# Every standing figure is drawn this tall (dp) inside the 328 by 330 art box, unless the
# scene is too wide to allow it; then it is as large as the box lets it be.
FIGURE_DP, BOX_W, BOX_H = 195, 328, 330

# For each page's file: its shape count; shapes to drop, mark as hair or light clothing,
# restyle, shift sideways, or bring to the front; what to add on top (root coordinates);
# and the height of a standing figure, in the file's units.
PAGES = {
    # 1. two developers, each with a phone, swapping: the backpacks and the apple go
    "true-friends_1h3v": dict(
        count=32,
        drop=[0, 9, 10, 13, 14, 15, 16, 17, 27],
        hair=[8, 29],
        cloth=[3, 5, 19, 22],
        restyle={26: 'fill="#3f3d56"', 28: 'fill="#3f3d56"', 31: 'fill="#3f3d56"', 20: 'fill="#2f2e41"', 23: 'fill="#2f2e41"'},
        add=phone(300, 116) + phone(370, 206) + SWAP,
        front=[11, 30],
        figure=684,
    ),
    # 2. a user with a phone and the review he left: the browser frame and the floor go
    "online-review_08y6": dict(
        count=27,
        drop=[0, 1, 14, 15, 16, 17, 18, 19, 20],
        hair=[11],
        cloth=[8, 9, 10, 13],
        restyle={
            21: 'fill="#ffffff" stroke="#6c63ff" stroke-width="5"',
            **{i: 'fill="#6c63ff"' for i in range(22, 27)},
        },
        add=phone(24, 268, 40, 70),
        front=[12],
        figure=429,
    ),
    # 3. the crew of three, closer together, chatting under 14 nights: floors and the plant go
    "work-chat_kw8x": dict(
        count=60,
        drop=[2, 5, 6, 7, 8, 17, 36],
        hair=[26, 50, 59],
        cloth=[25, 48],
        shift={**{i: 150 for i in [3, 4, *range(37, 51)]}, **{i: -140 for i in range(18, 36)}},
        add=calendar(498, 26),
        figure=369,
    ),
    # 4. the Play link going into the phone: its old screen content goes, ours comes in (the app's icon in ink, to keep the blue in check)
    "mobile-application_uc2q": dict(
        count=27, drop=list(range(14, 27)), hair=[11], cloth=[8, 10], add=play_link_screen(), figure=548
    ),
    # 5. looking through an app, nothing to sign: the plant and the floor go, one of the two blue tiles turns grey
    "searching_pqji": dict(count=25, drop=[5, 6], hair=[10, 11], restyle={8: 'fill="#d6d6e3"'}, add="", figure=600),
}

# The content box of each edited file (x0, y0, x1, y1), measured in the browser with
# measure.html. Rerun that page after changing PAGES and paste its output here.
BOXES = {
    "true-friends_1h3v": (172.7, -0.0, 661.2, 683.7),
    "online-review_08y6": (24.0, 54.1, 375.3, 647.6),
    "work-chat_kw8x": (190.2, -126.0, 807.2, 519.9),
    "mobile-application_uc2q": (0.0, 0.0, 600.5, 800.0),
    "searching_pqji": (107.7, -0.1, 511.2, 800.0),
}

SHAPE = re.compile(r"<(path|circle|ellipse|rect|polygon|polyline|line)\b[^>]*/>")
HEX = re.compile(r"#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})\b")
FILL = re.compile(r'\sfill="[^"]*"')


def in_place(before: str, tag: str) -> str:
    """Wrap a shape in the transforms of the groups it sat in, so it can move to the end of the file."""
    stack = []
    for m in re.finditer(r"<g\b([^>]*)>|</g>", before):
        if m.group(0) == "</g>":
            stack.pop()
        else:
            t = re.search(r'transform="([^"]*)"', m.group(1))
            stack.append(t.group(1) if t else "")
    for t in reversed(stack):
        if t:
            tag = f'<g transform="{t}">{tag}</g>'
    return tag


def edit(name: str, svg: str) -> str:
    page = PAGES[name]
    shapes = list(SHAPE.finditer(svg))
    if len(shapes) != page["count"]:
        raise SystemExit(f"{name}: expected {page['count']} shapes, found {len(shapes)}; the original changed")
    out, front, last = [], [], 0
    for i, m in enumerate(shapes):
        out.append(svg[last : m.start()])
        tag = m.group(0)
        style = page.get("restyle", {}).get(i)
        if i in page.get("hair", []):
            style = f'fill="{HAIR}"'
        if i in page.get("cloth", []):
            style = f'fill="{CLOTH}"'
        if style:
            tag = FILL.sub("", tag)[:-2] + f" {style}/>"
        dx = page.get("shift", {}).get(i)
        if dx:
            tag = f'<g transform="translate({dx} 0)">{tag}</g>'
        if i in page.get("drop", []):
            tag = ""
        elif i in page.get("front", []):
            front.append(in_place(svg[: m.start()], tag))
            tag = ""
        out.append(tag)
        last = m.end()
    out.append(svg[last:])
    svg = "".join(out)
    head, rest = svg.split(">", 1)
    head = re.sub(r'\s(width|height)="[^"]*"', "", head)
    return head + ">" + rest.replace("</svg>", page["add"] + "".join(front) + "</svg>")


def frame(name: str, svg: str) -> str:
    """Set the viewBox so a standing figure is FIGURE_DP tall and the content sits centred in the box."""
    page, box = PAGES[name], BOXES.get(name)
    if not box:
        return svg
    x0, y0, x1, y1 = box
    scale = min(FIGURE_DP / page["figure"], (BOX_W - 8) / (x1 - x0), (BOX_H - 8) / (y1 - y0))
    w, h = BOX_W / scale, BOX_H / scale
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    vb = f"{cx - w / 2:.1f} {cy - h / 2:.1f} {w:.1f} {h:.1f}"
    return re.sub(r'viewBox="[^"]*"', f'viewBox="{vb}"', svg, count=1)


def norm(h: str) -> str:
    h = h.lower()
    if len(h) == 4:
        h = "#" + "".join(c * 2 for c in h[1:])
    return h


def recolour(svg: str, table: dict, unknown: set) -> str:
    def swap(m):
        c = norm(m.group(0))
        if c in table:
            return table[c]
        if c not in KEEP:
            unknown.add(c)
        return m.group(0)

    return HEX.sub(swap, svg)


def main() -> int:
    missing = False
    for theme in ("light", "dark"):
        (HERE / theme).mkdir(exist_ok=True)
        for old in (HERE / theme).glob("*.svg"):
            if old.stem not in PAGES:
                old.unlink()
    for name in PAGES:
        svg = frame(name, edit(name, (HERE / "original" / f"{name}.svg").read_text(encoding="utf-8")))
        for theme, table in (("light", LIGHT), ("dark", DARK)):
            unknown: set = set()
            text = recolour(svg, table, unknown)
            if unknown:
                missing = True
                print(f"{theme} {name}: not in the map: {', '.join(sorted(unknown))}")
            (HERE / theme / f"{name}.svg").write_text(text, encoding="utf-8")
    print(f"{len(PAGES)} files, light and dark" + ("" if BOXES else " (not framed: BOXES is empty)"))
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
