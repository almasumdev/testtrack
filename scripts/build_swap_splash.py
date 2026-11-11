"""Export the "two phones swapping" launch icon to native Android splash drawables.

Run from any directory: python scripts/build_swap_splash.py
Geometry and colours live in branding/splash-*.svg. The motion ("Rise and turn",
about 930 ms) is declared below and mirrors the CSS block ".play.C" in
docs/design/icon-swap/source/anim.js. No raster conversion.

Output, per theme (light = values/drawable, dark = the -night folders):
  drawable[-night]/testtrack_splash_icon.xml                 static final frame, used below Android 12
  drawable[-night]-v31/testtrack_splash_vector_animated.xml  the artwork with its start pose
  drawable[-night]-v31/testtrack_splash_icon.xml             the animated-vector wrapper
  interpolator/splash_*.xml                                  the easing curves
The night folders are needed because Android ranks night above the API level when it picks a
drawable, so a night phone on Android 12+ would otherwise get the static icon.
"""

from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "branding"
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)

# The artwork is drawn on a 108 unit grid. Android's splash canvas is 288dp and masks it to a
# 192dp circle (2/3), the same ratio as the 72 of 108 units. The artwork reaches about 32 units
# from the centre and the circle is 36, so it fits even before the ICON_SCALE shrink below.
CANVAS_DP = 288

# Scale of the whole artwork about the canvas centre. Matches the 0.9 foreground scale that
# Android Studio Image Asset wrote into ic_launcher_foreground.xml, so the launch screen icon is
# the same size as the launcher icon. Studio writes it as scale 0.9 plus translate 5.4, which is
# the same as scaling about the centre (54, 54).
ICON_SCALE = 0.9
CENTER = 54

# Easing curves, copied from the browser reference (CSS cubic-bezier).
EASING = {
    "rise": (.2, .9, .3, 1),        # phones settle
    "turn": (.3, 1.25, .5, 1),      # arrows half turn with a small overshoot
    "pop": (.3, 1.5, .5, 1),        # bug scale with a bigger overshoot
    # Android clamps alpha to 1, so the bug fade uses the same curve without the overshoot.
    "pop_fade": (.3, 1, .5, 1),
    "ease_in": (.42, 0, 1, 1),      # CSS ease-in: arrows fade in slowly
    "ease_out": (0, 0, .58, 1),     # CSS ease-out: the check draws
}

# Groups that need a pivot of their own (in the group's own coordinates).
PIVOTS = {"arrows": (54, 54), "bug_pop": (0, .1)}
# Start pose of animated groups. The end pose is whatever the SVG says.
START_GROUP = {
    "phone_left": {"translateY": 10},
    "phone_right": {"translateY": 10},
    "arrows": {"rotation": -180},
    "bug_pop": {"scaleX": 0, "scaleY": 0},
}

# Each fading part: (delay ms, duration ms, easing). All fade from 0 to 1, except the arrows,
# which stay opaque and fade their colour from the splash background instead (see ARROW_FADE).
FADES = {
    "phone_left": (0, 420, "rise"),
    "phone_right": (90, 420, "rise"),
    "arrows": (260, 520, "ease_in"),
    "bug": (560, 300, "pop_fade"),
}
# target, property, from, to, delay, duration, easing
GROUP_MOTION = [
    ("phone_left", "translateY", 10, 0, 0, 420, "rise"),
    ("phone_right", "translateY", 10, 0, 90, 420, "rise"),
    ("arrows", "rotation", -180, 0, 260, 600, "turn"),
    ("bug_pop", "scaleX", 0, 1, 560, 300, "pop"),
    ("bug_pop", "scaleY", 0, 1, 560, 300, "pop"),
]
CHECK_DRAW = (690, 240, "ease_out")


# The arrow head path has a fill, a round-join stroke and overlaps the shaft, so animating alpha
# would stack partial alphas and make the head look hollow. The arrows only ever sit on the plain
# splash background, so they fade their colour from that background to the real colour instead.
ARROW_FADE = "arrows"


def splash_background(folder_suffix):
    """The #AARRGGBB splash background of a theme, read from res/values[-night]/splash.xml."""
    values = "values-night" if folder_suffix else "values"
    root = ET.parse(RES / values / "splash.xml").getroot()
    color = root.find("color[@name='splash_background']").text.strip()
    if not re.fullmatch(r"#[0-9A-Fa-f]{8}", color):
        raise ValueError(f"splash_background must be #AARRGGBB, got {color}")
    return color


def opaque(color):
    """#RRGGBB from the SVG as #AARRGGBB."""
    if not re.fullmatch(r"#[0-9A-Fa-f]{6}", color):
        raise ValueError(f"Arrow colour must be #RRGGBB, got {color}")
    return "#FF" + color[1:]


def android_attrs(**attributes):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in attributes.items()}


def num(value):
    return f"{value:g}"


def tag_of(node):
    return node.tag.split("}")[-1]


def write_xml(path, root, header):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root, space="    ")
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n' + header + "\n"
        + ET.tostring(root, encoding="unicode") + "\n",
        encoding="utf-8",
        newline="\n",
    )


def rect_path(x, y, w, h, rx):
    if not rx:
        return f"M{num(x)} {num(y)}H{num(x + w)}V{num(y + h)}H{num(x)}Z"
    r = rx
    return (
        f"M{num(x + r)} {num(y)}H{num(x + w - r)}A{num(r)} {num(r)} 0 0 1 {num(x + w)} {num(y + r)}"
        f"V{num(y + h - r)}A{num(r)} {num(r)} 0 0 1 {num(x + w - r)} {num(y + h)}"
        f"H{num(x + r)}A{num(r)} {num(r)} 0 0 1 {num(x)} {num(y + h - r)}"
        f"V{num(y + r)}A{num(r)} {num(r)} 0 0 1 {num(x + r)} {num(y)}Z"
    )


def circle_path(cx, cy, r):
    return (
        f"M{num(cx - r)} {num(cy)}A{num(r)} {num(r)} 0 1 0 {num(cx + r)} {num(cy)}"
        f"A{num(r)} {num(r)} 0 1 0 {num(cx - r)} {num(cy)}Z"
    )


def group_transform(node):
    """Map an SVG transform onto vector group attributes.

    A group applies translate, then rotate and scale around its pivot. That covers a lone rotate
    or "translate(...) scale(...)", which is all the artwork uses.
    """
    value = node.get("transform")
    if not value:
        return {}
    calls = re.findall(r"(rotate|translate|scale)\(([^)]*)\)", value)
    kinds = [kind for kind, _ in calls]
    if kinds not in (["rotate"], ["translate"], ["scale"], ["translate", "scale"]):
        raise ValueError(f"Unsupported transform: {value}")
    attrs = {}
    for kind, raw in calls:
        args = [float(a) for a in re.split(r"[\s,]+", raw.strip())]
        if kind == "rotate":
            attrs.update(rotation=num(args[0]), pivotX=num(args[1]), pivotY=num(args[2]))
        elif kind == "translate":
            attrs.update(translateX=num(args[0]), translateY=num(args[1] if len(args) > 1 else 0))
        else:
            attrs.update(scaleX=num(args[0]), scaleY=num(args[-1]))
    return attrs


def fade_owner(ancestors, node_id):
    """Which animated part a path belongs to, so its alpha can be driven."""
    if node_id == "check":
        return "check"
    for name in ("bug", "arrows", "phone_left", "phone_right"):
        if name in ancestors:
            return name
    return None


def build_vector(svg, animated=False, background=None):
    gradients = {g.attrib["id"]: g for g in svg.findall(f".//{{{SVG}}}linearGradient")}
    vector = ET.Element("vector", android_attrs(
        width=f"{CANVAS_DP}dp", height=f"{CANVAS_DP}dp", viewportWidth=108, viewportHeight=108,
    ))
    paths = []  # (name, owner, props) for every path whose alpha (or arrow colour) is animated
    counter = {}

    def convert(node, parent, ancestors):
        tag = tag_of(node)
        if tag == "defs":
            return
        if tag == "g":
            node_id = node.get("id")
            attrs = group_transform(node)
            if node_id:
                attrs["name"] = node_id
                if animated:
                    attrs.update({k: num(v) for k, v in START_GROUP.get(node_id, {}).items()})
                if node_id in PIVOTS:
                    attrs["pivotX"], attrs["pivotY"] = (num(v) for v in PIVOTS[node_id])
            group = ET.SubElement(parent, "group", android_attrs(**attrs))
            for child in node:
                convert(child, group, ancestors + ([node_id] if node_id else []))
            return
        fill = node.get("fill")
        stroke = node.get("stroke")
        if fill in (None, "none") and stroke in (None, "none"):
            return  # draws nothing (the old placeholder check on the left phone)
        if tag == "path":
            data, box = node.attrib["d"], None
        elif tag == "rect":
            x, y, w, h = (float(node.get(k, 0)) for k in ("x", "y", "width", "height"))
            data, box = rect_path(x, y, w, h, float(node.get("rx", 0))), (x, y, w, h)
        elif tag == "circle":
            cx, cy, r = (float(node.get(k)) for k in ("cx", "cy", "r"))
            data, box = circle_path(cx, cy, r), (cx - r, cy - r, 2 * r, 2 * r)
        else:
            raise ValueError(f"Unsupported SVG artwork element: {tag}")
        attrs = {"pathData": data}
        gradient = None
        if fill and fill.startswith("url("):
            gradient = gradients[fill[5:-1]]
        elif fill and fill != "none":
            attrs["fillColor"] = fill
        if stroke and stroke != "none":
            attrs["strokeColor"] = stroke
            for source, dest in (("stroke-width", "strokeWidth"), ("stroke-linecap", "strokeLineCap"),
                                 ("stroke-linejoin", "strokeLineJoin")):
                if node.get(source) is not None:
                    attrs[dest] = node.get(source)
        owner = fade_owner(ancestors, node.get("id"))
        if animated and owner:
            counter[owner] = counter.get(owner, 0) + 1
            attrs["name"] = node.get("id") or f"{owner}_part_{counter[owner]}"
            props = []
            if owner == ARROW_FADE:
                # Start on the background colour, end on the SVG colour, always fully opaque.
                colors = {}
                for prop in ("fillColor", "strokeColor"):
                    if prop in attrs:
                        colors[prop] = opaque(attrs[prop])
                        attrs[prop] = background
                        props.append((prop, background, colors[prop]))
                paths.append((attrs["name"], owner, props))
                path = ET.SubElement(parent, "path", android_attrs(**attrs))
                return
            if gradient is not None or "fillColor" in attrs:
                props.append("fillAlpha")
            if "strokeColor" in attrs:
                props.append("strokeAlpha")
            if owner == "check":
                # Hidden until the draw begins, so an empty trim cannot leave a round dot.
                attrs["trimPathEnd"] = 0
            for prop in props:
                attrs[prop] = 0
            paths.append((attrs["name"], owner, props))
        path = ET.SubElement(parent, "path", android_attrs(**attrs))
        if gradient is not None:
            # objectBoundingBox gradients become coordinates in the path's own space.
            x, y, w, h = box
            gx = lambda v: num(x + float(v) * w)
            gy = lambda v: num(y + float(v) * h)
            attr = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": "android:fillColor"})
            native = ET.SubElement(attr, "gradient", android_attrs(
                type="linear",
                startX=gx(gradient.get("x1")), startY=gy(gradient.get("y1")),
                endX=gx(gradient.get("x2")), endY=gy(gradient.get("y2")),
            ))
            for stop in gradient:
                ET.SubElement(native, "item", android_attrs(
                    offset=stop.get("offset"), color=stop.get("stop-color"),
                ))

    # The bug is drawn rotated with the phone, then scaled and moved onto the screen. Name the
    # innermost plain group so the pop can scale around the bug's own centre.
    bug = svg.find(f".//*[@id='bug']")
    inner = bug.find(f"{{{SVG}}}g/{{{SVG}}}g/{{{SVG}}}g")
    inner.set("id", "bug_pop")
    # Outer static group: scales everything about the centre. Inner groups keep their own pivots
    # (in this group's coordinates), and animation targets are found by name, so motion is unchanged.
    scaled = ET.SubElement(vector, "group", android_attrs(
        name="icon_scale", scaleX=num(ICON_SCALE), scaleY=num(ICON_SCALE),
        pivotX=num(CENTER), pivotY=num(CENTER),
    ))
    for child in svg:
        convert(child, scaled, [])
    return vector, paths


def animator(parent, prop, start, end, delay, duration, easing):
    interpolator = easing if easing.startswith("@") else f"@interpolator/splash_{easing}"
    ET.SubElement(parent, "objectAnimator", android_attrs(
        propertyName=prop, duration=duration, startOffset=delay,
        valueFrom=num(start), valueTo=num(end), valueType="floatType",
        interpolator=interpolator,
    ))


def color_animator(parent, prop, start, end, delay, duration, easing):
    ET.SubElement(parent, "objectAnimator", android_attrs(
        propertyName=prop, duration=duration, startOffset=delay,
        valueFrom=start, valueTo=end, valueType="colorType",
        interpolator=f"@interpolator/splash_{easing}",
    ))


def build_animation(paths):
    root = ET.Element("animated-vector", android_attrs(drawable="@drawable/testtrack_splash_vector_animated"))
    sets = {}

    def target_set(name):
        if name not in sets:
            target = ET.SubElement(root, "target", android_attrs(name=name))
            attr = ET.SubElement(target, f"{{{AAPT}}}attr", {"name": "android:animation"})
            sets[name] = ET.SubElement(attr, "set", android_attrs(ordering="together"))
        return sets[name]

    for name, prop, start, end, delay, duration, easing in GROUP_MOTION:
        animator(target_set(name), prop, start, end, delay, duration, easing)
    for name, owner, props in paths:
        if owner == "check":
            delay, duration, easing = CHECK_DRAW
            animator(target_set(name), "trimPathEnd", 0, 1, delay, duration, easing)
            # Fully visible as soon as the stroke starts drawing.
            animator(target_set(name), "strokeAlpha", 0, 1, delay, 1, "@android:interpolator/linear")
            continue
        delay, duration, easing = FADES[owner]
        if owner == ARROW_FADE:
            for prop, start, end in props:
                color_animator(target_set(name), prop, start, end, delay, duration, easing)
            continue
        for prop in props:
            animator(target_set(name), prop, 0, 1, delay, duration, easing)
    return root


def write_theme(svg_name, folder_suffix):
    svg = ET.parse(DESIGN / svg_name).getroot()
    header = (f"<!-- Generated by scripts/build_swap_splash.py. "
              f"Edit branding/{svg_name}. -->")
    static, _ = build_vector(svg)
    write_xml(RES / f"drawable{folder_suffix}/testtrack_splash_icon.xml", static, header)
    # Parse again: build_vector tags the bug group, and the animated copy needs its own pass.
    svg = ET.parse(DESIGN / svg_name).getroot()
    animated, paths = build_vector(svg, animated=True, background=splash_background(folder_suffix))
    write_xml(RES / f"drawable{folder_suffix}-v31/testtrack_splash_vector_animated.xml", animated, header)
    write_xml(RES / f"drawable{folder_suffix}-v31/testtrack_splash_icon.xml", build_animation(paths), header)


def main():
    write_theme("splash-light.svg", "")
    write_theme("splash-dark.svg", "-night")
    for name, (x1, y1, x2, y2) in EASING.items():
        write_xml(RES / f"interpolator/splash_{name}.xml", ET.Element("pathInterpolator", android_attrs(
            controlX1=x1, controlY1=y1, controlX2=x2, controlY2=y2,
        )), "<!-- Generated by scripts/build_swap_splash.py. -->")
    print("Exported static icons, Android 12+ animated vectors (about 930 ms), light and dark.")


if __name__ == "__main__":
    main()
