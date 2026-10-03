"""Build TestTrack's five editable SVG scenes and matching Android vectors.

The approved robot is shared with the splash. Everything is a vector path;
no network assets, bitmap upscaling, or page-specific brand colours.
Run: python scripts/build_onboarding.py
"""

from copy import deepcopy
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from build_splash import ANDROID, SVG, android_attrs, build_vector

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/design/onboarding"
RES = ROOT / "app/src/main/res/drawable"
BLUE, DEEP, PALE = "#32729B", "#1D5C82", "#D3E4EF"
INK, PAPER, MINT, CORAL = "#18242E", "#FFFCF1", "#64CBB6", "#F58B73"
ROBOT = ET.parse(ROOT / "docs/design/logo/test-exchange.svg").getroot()


def path(d, fill="none", stroke=None, width=3, opacity=1):
    attrs = {"d": d, "fill": fill}
    if stroke:
        attrs.update({"stroke": stroke, "stroke-width": str(width),
                      "stroke-linecap": "round", "stroke-linejoin": "round"})
    if opacity != 1:
        attrs["opacity"] = str(opacity)
    return ET.Element(f"{{{SVG}}}path", attrs)


def rect(x, y, w, h, r, fill):
    return path(f"M{x+r} {y} H{x+w-r} Q{x+w} {y} {x+w} {y+r} V{y+h-r} "
                f"Q{x+w} {y+h} {x+w-r} {y+h} H{x+r} Q{x} {y+h} {x} {y+h-r} "
                f"V{y+r} Q{x} {y} {x+r} {y} Z", fill)


def ellipse(x, y, rx, ry, fill, opacity=1):
    return path(f"M{x-rx} {y} A{rx} {ry} 0 1 0 {x+rx} {y} "
                f"A{rx} {ry} 0 1 0 {x-rx} {y} Z", fill, opacity=opacity)


def check(x, y, size=16, color=DEEP, width=5):
    return path(f"M{x-size*.45} {y} L{x-size*.1} {y+size*.33} "
                f"L{x+size*.5} {y-size*.4}", stroke=color, width=width)


def group(parent, name, transform=None):
    attrs = {"id": name}
    if transform:
        attrs["transform"] = transform
    return ET.SubElement(parent, f"{{{SVG}}}g", attrs)


def scene(name):
    svg = ET.Element(f"{{{SVG}}}svg", {"viewBox": "0 0 400 400", "fill": "none"})
    svg.append(deepcopy(ROBOT.find(f"{{{SVG}}}defs")))
    root = group(svg, name)
    root.append(ellipse(200, 203, 158, 152, BLUE, .08))
    root.append(ellipse(200, 356, 133, 12, DEEP, .10))
    # A restrained, shared decoration system on all three pages.
    for x, y, s in [(50, 95, 5), (349, 242, 4), (305, 53, 3)]:
        root.append(path(f"M{x-s} {y} H{x+s} M{x} {y-s} V{y+s}", stroke=BLUE, width=2))
    for x, y, r in [(340, 104, 4), (54, 287, 3), (102, 48, 3)]:
        root.append(ellipse(x, y, r, r, CORAL))
    return svg, root


def bot(parent, x, y, scale, exchange=False):
    holder = group(parent, "mascot_pose", f"translate({x} {y}) scale({scale})")
    robot = deepcopy(ROBOT.find(f"{{{SVG}}}g"))
    if not exchange:
        for child in list(robot):
            if child.get("id") not in ("torso", "head_rig"):
                robot.remove(child)
        robot.extend([
            ellipse(627, 876, 114, 110, MINT), check(627, 878, 126, PAPER, 34),
            ellipse(335, 835, 81, 92, BLUE), ellipse(919, 835, 81, 92, BLUE),
            ellipse(316, 819, 58, 67, "#6CA9CF"), ellipse(938, 819, 58, 67, "#6CA9CF"),
        ])
    holder.append(robot)


def avatar(parent, name, x, y, shirt, hair):
    g = group(parent, name, f"translate({x} {y})")
    g.extend([
        ellipse(0, 0, 31, 31, PAPER), ellipse(0, 1, 27, 27, PALE),
        path("M-23 19 Q-22 8 -9 8 H9 Q22 8 23 19 Q0 35 -23 19Z", shirt),
        ellipse(0, -5, 13, 16, "#F3C3A5"),
        path("M-13 -5 Q-19 -23 0 -23 Q19 -23 13 -4 L9 -13 Q0 -8 -10 -13 Z", hair),
        ellipse(-4, -4, 1.3, 1.6, INK), ellipse(5, -4, 1.3, 1.6, INK),
        path("M-3 2 Q1 6 5 2", stroke=INK, width=1.8),
    ])


def together():
    svg, g = scene("together")
    g.append(path("M91 128 C71 226 94 304 172 325 M303 132 C342 222 311 297 247 324 "
                  "M118 87 Q200 34 281 88", stroke=BLUE, width=2, opacity=.28))
    avatar(g, "developer_left", 81, 122, CORAL, INK)
    avatar(g, "developer_top", 200, 63, BLUE, "#463D3A")
    avatar(g, "developer_right", 320, 129, MINT, INK)
    bot(g, 18, 52, .29, exchange=True)
    for x, y in [(57, 236), (345, 295)]:
        g.append(ellipse(x, y, 15, 15, PAPER))
        checkmark = check(x, y, 14, BLUE, 3.5)
        g.append(checkmark)
    return svg


def feedback():
    svg, g = scene("feedback")
    # A real test session: app screen, an observation, and a response.
    phone = group(g, "test_phone", "translate(4 0)")
    phone.extend([
        rect(89, 80, 172, 267, 27, "#B7D1E2"), rect(83, 74, 172, 267, 27, DEEP),
        rect(92, 85, 154, 243, 20, PAPER), rect(137, 84, 64, 14, 7, DEEP),
        rect(107, 119, 124, 87, 15, PALE), ellipse(169, 153, 22, 22, BLUE),
        path("M161 142 L182 153 L161 165 Z", PAPER),
        rect(144, 186, 51, 5, 2.5, "#A0B3C0"),
        rect(107, 224, 88, 7, 3.5, DEEP), rect(107, 239, 119, 5, 2.5, "#B7CAD6"),
        rect(107, 251, 100, 5, 2.5, "#B7CAD6"),
        rect(107, 277, 80, 27, 10, BLUE), check(147, 290, 14, PAPER, 3),
        rect(149, 317, 40, 3, 1.5, "#B7CAD6"),
    ])
    bubble = group(g, "observation", "rotate(-7 85 213)")
    bubble.extend([
        path("M37 174 H129 Q143 174 143 188 V226 Q143 239 129 239 H75 L56 253 V239 H37 "
             "Q24 239 24 226 V188 Q24 174 37 174Z", CORAL),
        rect(40, 191, 63, 6, 3, PAPER), rect(40, 204, 86, 5, 2.5, PAPER),
        rect(40, 217, 50, 5, 2.5, PAPER),
    ])
    g.extend([
        path("M234 48 H325 Q340 48 340 64 V102 Q340 116 325 116 H276 L260 131 V116 H234 "
             "Q218 116 218 102 V64 Q218 48 234 48Z", PAPER),
        ellipse(244, 80, 14, 14, MINT), check(244, 80, 13, DEEP, 3),
        rect(268, 68, 52, 6, 3, BLUE), rect(268, 81, 39, 5, 2.5, "#A0B3C0"),
        rect(235, 99, 85, 4, 2, PALE),
    ])
    bot(g, 195, 161, .17)
    return svg


def progress():
    svg, g = scene("progress")
    board = group(g, "progress_board", "rotate(5 214 191)")
    board.extend([
        rect(98, 87, 233, 211, 21, "#B7D1E2"), rect(92, 78, 233, 211, 21, PAPER),
        path("M113 78 H304 Q325 78 325 99 V132 H92 V99 Q92 78 113 78Z", BLUE),
        rect(116, 64, 10, 32, 5, DEEP), rect(289, 64, 10, 32, 5, DEEP),
        rect(138, 101, 93, 7, 3.5, PAPER), ellipse(292, 105, 8, 8, MINT),
    ])
    for row in range(3):
        for col in range(5):
            x, y = 110 + col*40, 151 + row*40
            done = row*5+col < 9
            board.append(rect(x, y, 28, 28, 8, PALE if not done else "#C3E9DF"))
            if done:
                board.append(check(x+14, y+13, 14, DEEP, 3))
            elif row == 1 and col == 4:
                board.append(ellipse(x+14, y+14, 5, 5, CORAL))
    # The same mascot supports the visual, rather than being a second logo.
    bot(g, -2, 181, .174)
    g.extend([
        rect(211, 293, 136, 49, 18, PAPER), ellipse(237, 317, 14, 14, MINT),
        check(237, 317, 14, DEEP, 3), rect(261, 306, 66, 6, 3, BLUE),
        rect(261, 319, 51, 5, 2.5, "#A0B3C0"),
    ])
    return svg


def share():
    svg, g = scene("share")
    card = group(g, "app_listing", "rotate(5 225 199)")
    card.extend([
        rect(110, 93, 210, 239, 22, "#B7D1E2"), rect(104, 85, 210, 239, 22, PAPER),
        rect(124, 109, 62, 62, 17, BLUE),
        path("M142 152 L152 125 Q155 118 159 125 L170 152 H163 L159 142 H149 L146 152 Z", PAPER),
        rect(198, 116, 91, 8, 4, DEEP), rect(198, 135, 66, 5, 2.5, "#A0B3C0"),
        rect(198, 150, 46, 5, 2.5, "#B7CAD6"),
        rect(124, 192, 170, 52, 12, PALE),
        rect(139, 205, 115, 6, 3, BLUE), rect(139, 221, 139, 5, 2.5, "#A0B3C0"),
        rect(124, 266, 108, 33, 12, BLUE), check(177, 282, 17, PAPER, 3.5),
    ])
    # The input link is separate from the populated app card.
    link = group(g, "play_link", "rotate(-7 128 82)")
    link.extend([
        rect(40, 53, 200, 52, 18, PAPER),
        path("M72 83 L66 83 C54 83 54 67 66 67 H76 M76 75 H89 "
             "M89 67 H95 C107 67 107 83 95 83 H85", stroke=BLUE, width=4),
        rect(119, 70, 99, 6, 3, DEEP), rect(119, 83, 69, 4, 2, "#B7CAD6"),
    ])
    g.extend([
        path("M323 234 L347 259", stroke=DEEP, width=15),
        ellipse(309, 215, 33, 33, BLUE), ellipse(309, 215, 25, 25, PAPER),
        ellipse(309, 215, 18, 18, PALE), check(309, 215, 23, DEEP, 4),
    ])
    bot(g, -7, 181, .17)
    return svg


def explore():
    svg, g = scene("explore")
    # An open community window: browse app tiles, then choose a way in.
    window = group(g, "community_window", "rotate(-4 184 202)")
    window.extend([
        rect(62, 92, 249, 220, 22, "#B7D1E2"), rect(56, 84, 249, 220, 22, PAPER),
        path("M78 84 H283 Q305 84 305 106 V127 H56 V106 Q56 84 78 84 Z", BLUE),
    ])
    for x in [78, 91, 104]:
        window.append(ellipse(x, 106, 3, 3, PAPER))
    window.extend([
        rect(124, 102, 119, 6, 3, "#80BCDE"),
        rect(76, 148, 90, 108, 14, PALE), rect(181, 148, 104, 108, 14, PALE),
        rect(94, 161, 54, 54, 15, CORAL),
        path("M110 188 Q110 173 121 181 Q132 173 132 188 Q131 196 121 202 Q111 196 110 188Z", PAPER),
        rect(207, 161, 54, 54, 15, MINT),
        path("M219 199 L231 176 L246 199 Z", PAPER),
        rect(92, 229, 58, 5, 2.5, BLUE), rect(202, 229, 65, 5, 2.5, BLUE),
        rect(78, 276, 125, 5, 2.5, "#B7CAD6"),
    ])
    compass = group(g, "choose_direction", "rotate(12 291 82)")
    compass.extend([
        ellipse(291, 82, 42, 42, DEEP), ellipse(291, 82, 34, 34, PAPER),
        path("M291 51 V57 M291 107 V113 M260 82 H266 M316 82 H322", stroke=BLUE, width=2),
        path("M303 64 L298 89 L279 100 L284 75 Z", PALE),
        path("M303 64 L298 89 L284 75 Z", CORAL), ellipse(291, 82, 4, 4, DEEP),
    ])
    g.append(path("M56 317 Q59 343 101 335 Q142 328 168 344", stroke=BLUE, width=3, opacity=.4))
    g.append(path("M161 333 L171 344 L158 350", stroke=BLUE, width=3))
    bot(g, 193, 184, .166)
    return svg


def export(svg, name):
    OUT.mkdir(parents=True, exist_ok=True)
    ET.register_namespace("", SVG)
    ET.indent(svg, space="  ")
    (OUT / f"{name}.svg").write_text(ET.tostring(svg, encoding="unicode") + "\n", encoding="utf-8")
    vector = build_vector(svg)
    vector.attrib.update(android_attrs(width="400dp", height="400dp", viewportWidth=400, viewportHeight=400))
    safe = vector.find("group")
    safe.attrib = android_attrs(name="illustration")
    native_groups = {n.get(f"{{{ANDROID}}}name"): n for n in vector.iter("group")}
    for node in svg.iter(f"{{{SVG}}}g"):
        dest = native_groups[node.get("id")]
        for kind, values in re.findall(r"(translate|scale|rotate)\(([^)]+)\)", node.get("transform", "")):
            values = [float(v) for v in re.split(r"[ ,]+", values)]
            if kind == "translate":
                dest.attrib.update(android_attrs(translateX=values[0], translateY=values[1]))
            elif kind == "scale":
                dest.attrib.update(android_attrs(scaleX=values[0], scaleY=values[-1]))
            else:
                dest.attrib.update(android_attrs(rotation=values[0], pivotX=values[1], pivotY=values[2]))
    source_paths = list(svg.find(f"{{{SVG}}}g").iter(f"{{{SVG}}}path"))
    for src, dst in zip(source_paths, vector.iter("path"), strict=True):
        if "opacity" in src.attrib:
            dst.attrib.update(android_attrs(fillAlpha=src.get("opacity"), strokeAlpha=src.get("opacity")))
    ET.indent(vector, space="    ")
    (RES / f"onboarding_{name}.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/build_onboarding.py. -->\n'
        + ET.tostring(vector, encoding="unicode") + "\n", encoding="utf-8")


if __name__ == "__main__":
    for name, create in [("together", together), ("feedback", feedback), ("progress", progress),
                         ("share", share), ("explore", explore)]:
        export(create(), name)
    # A tightly framed head for the onboarding wordmark; never downsize the full mascot to 32dp.
    head = deepcopy(ROBOT)
    body = head.find(f"{{{SVG}}}g")
    for child in list(body):
        if child.get("id") != "head_rig":
            body.remove(child)
    mark = build_vector(head)
    mark.attrib.update(android_attrs(width="40dp", height="40dp", viewportWidth=900, viewportHeight=900))
    mark.find("group").attrib = android_attrs(name="head_frame", translateX=-177, translateY=-2)
    ET.indent(mark, space="    ")
    (RES / "onboarding_mascot.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/build_onboarding.py. -->\n'
        + ET.tostring(mark, encoding="unicode") + "\n", encoding="utf-8")
    # Full robot for app bars, without the launcher/splash mask padding.
    full = build_vector(deepcopy(ROBOT))
    full.attrib.update(android_attrs(width="44dp", height="44dp", viewportWidth=1040, viewportHeight=1040))
    full.find("group").attrib = android_attrs(name="full_robot_frame", translateX=-107, translateY=-107)
    ET.indent(full, space="    ")
    (RES / "testtrack_robot.xml").write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated by scripts/build_onboarding.py. -->\n'
        + ET.tostring(full, encoding="unicode") + "\n", encoding="utf-8")
    print("Built onboarding scenes, mascot head, and full app-bar robot.")
