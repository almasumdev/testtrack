"""Export the editable Test Exchange SVG to native Android splash drawables.

Run from any directory: python scripts/build_splash.py
Geometry and colours live in the SVG; motion is declared below. No raster conversion.
"""

from pathlib import Path
import math
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
DESIGN = ROOT / "docs/design/logo"
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
AAPT = "http://schemas.android.com/aapt"
SVG = "http://www.w3.org/2000/svg"
ET.register_namespace("android", ANDROID)
ET.register_namespace("aapt", AAPT)

# target, property, delay, duration, [(fraction, value), ...], easing
# Arrival -> reciprocal exchange -> verification. No generic logo bounce or antenna wobble.
# Each segment uses its own easing in both Android and the browser. Total: 900 ms.
EASING = {
    "arrive": (.16, 1, .3, 1),
    "flow": (.4, 0, .2, 1),
    "confirm": (.2, 0, 0, 1),
    "linear": (0, 0, 1, 1),
}


def flow_progress(t):
    """Sample the Android fast-out-slow-in curve for the circular reveal rig."""
    low, high = 0., 1.
    for _ in range(18):
        u = (low + high) / 2
        x = 3 * (1-u)**2 * u * .4 + 3 * (1-u) * u*u * .2 + u**3
        if x < t:
            low = u
        else:
            high = u
    return 3 * (1-u) * u*u + u**3


def sector(start, sweep):
    """Two compatible cubic arcs, so AVD path morphs sweep along the arrows."""
    cx, cy, radius = 627, 907, 700
    def point(angle):
        return cx + radius * math.cos(angle), cy + radius * math.sin(angle)
    def coords(p):
        return f"{p[0]:.3f} {p[1]:.3f}"
    a = math.radians(start)
    step = math.radians(sweep / 2)
    path = f"M{cx} {cy} L{coords(point(a))}"
    for _ in range(2):
        b = a + step
        k = 4 / 3 * math.tan(step / 4)
        p0, p1 = point(a), point(b)
        c0 = p0[0] - radius * k * math.sin(a), p0[1] + radius * k * math.cos(a)
        c1 = p1[0] + radius * k * math.sin(b), p1[1] - radius * k * math.cos(b)
        path += f" C{coords(c0)} {coords(c1)} {coords(p1)}"
        a = b
    return path + f" L{cx} {cy} Z"


def reveal_frames(start):
    return [(i / 24, sector(start, 180 * flow_progress(i / 24))) for i in range(25)]


REVEALS = {
    "exchange_top": ("top_reveal", reveal_frames(180)),
    "exchange_bottom": ("bottom_reveal", reveal_frames(0)),
    "exchange_top_front": ("top_front_reveal", reveal_frames(180)),
    "exchange_bottom_front": ("bottom_front_reveal", reveal_frames(0)),
}
MOTION = [
    ("torso", "fillAlpha", 60, 180, [(0, 0), (1, 1)], "confirm"),
    ("card_left", "translateX", 60, 350, [(0, -62), (1, 0)], "arrive"),
    ("card_left", "translateY", 60, 350, [(0, 92), (1, 0)], "arrive"),
    ("card_left", "rotation", 60, 350, [(0, -14), (1, 0)], "arrive"),
    ("card_left", "scaleX", 60, 350, [(0, .68), (1, 1)], "arrive"),
    ("card_left", "scaleY", 60, 350, [(0, .68), (1, 1)], "arrive"),
    ("card_left", "opacity", 60, 140, [(0, 0), (1, 1)], "confirm"),
    ("card_right", "translateX", 100, 350, [(0, 62), (1, 0)], "arrive"),
    ("card_right", "translateY", 100, 350, [(0, 92), (1, 0)], "arrive"),
    ("card_right", "rotation", 100, 350, [(0, 14), (1, 0)], "arrive"),
    ("card_right", "scaleX", 100, 350, [(0, .68), (1, 1)], "arrive"),
    ("card_right", "scaleY", 100, 350, [(0, .68), (1, 1)], "arrive"),
    ("card_right", "opacity", 100, 140, [(0, 0), (1, 1)], "confirm"),
    ("top_reveal", "pathData", 240, 340, REVEALS['exchange_top'][1], "linear"),
    ("bottom_reveal", "pathData", 310, 340, REVEALS['exchange_bottom'][1], "linear"),
    ("top_front_reveal", "pathData", 240, 340, REVEALS['exchange_top_front'][1], "linear"),
    ("bottom_front_reveal", "pathData", 310, 340, REVEALS['exchange_bottom_front'][1], "linear"),
    ("eyes", "translateX", 140, 640, [(0, 0), (.25, -9), (.65, 9), (1, 0)], "flow"),
    ("eyes", "translateY", 140, 640, [(0, 0), (.25, 10), (.65, 10), (1, 0)], "flow"),
    ("eye_blink", "scaleY", 780, 120, [(0, 1), (.45, .06), (1, 1)], "flow"),
    ("check_left", "trimPathEnd", 550, 180, [(0, 0), (1, 1)], "confirm"),
    ("check_right", "trimPathEnd", 650, 180, [(0, 0), (1, 1)], "confirm"),
    ("check_left", "strokeAlpha", 550, 60, [(0, 0), (1, 1)], "confirm"),
    ("check_right", "strokeAlpha", 650, 60, [(0, 0), (1, 1)], "confirm"),
    ("head_rig", "translateY", 630, 270, [(0, 0), (.4, 10), (1, 0)], "flow"),
]


def native_motion():
    for name, prop, delay, duration, frames, ease in MOTION:
        if prop == "opacity":
            for part in ["shell", "screen", "hand_base", "hand_front"]:
                yield (f"{name}_{part}", "fillAlpha", delay, duration, frames, ease)
        else:
            yield name, prop, delay, duration, frames, ease


def android_attrs(**attributes):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in attributes.items()}


def write_xml(path, root):
    path.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root, space="    ")
    path.write_text(
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<!-- Generated by scripts/build_splash.py. Edit docs/design/logo/test-exchange.svg. -->\n'
        + ET.tostring(root, encoding="unicode") + "\n",
        encoding="utf-8",
    )


def build_vector(svg, animated=False):
    gradients = {g.attrib["id"]: g for g in svg.findall(f".//{{{SVG}}}linearGradient")}
    clips = {c.attrib["id"]: c for c in svg.findall(f".//{{{SVG}}}clipPath")}
    vector = ET.Element("vector", android_attrs(
        width="288dp", height="288dp", viewportWidth=1254, viewportHeight=1254,
    ))
    # Without an icon background Android's 288dp canvas is masked to a 192dp circle.
    # This padding keeps the antenna, ears, cards and motion inside that circle.
    safe = ET.SubElement(vector, "group", android_attrs(
        name="safe_area", pivotX=627, pivotY=627, scaleX=.78, scaleY=.78,
    ))
    index = 0

    def convert(node, parent):
        nonlocal index
        tag = node.tag.split("}")[-1]
        if tag == "g":
            attrs = android_attrs(name=node.attrib["id"])
            for axis in ("x", "y"):
                value = node.get(f"data-pivot-{axis}")
                if value is not None:
                    attrs[f"{{{ANDROID}}}pivot{axis.upper()}"] = value
            if animated:
                for name, prop, _, _, frames, _ in native_motion():
                    if name == node.get("id"):
                        attrs.update(android_attrs(**{prop: frames[0][1]}))
            group = ET.SubElement(parent, "group", attrs)
            if node.get("clip-path"):
                clip = clips[node.get("clip-path")[5:-1]]
                for shape in clip:
                    ET.SubElement(group, "clip-path", android_attrs(pathData=shape.attrib["d"]))
            if animated and node.get("id") in REVEALS:
                name, frames = REVEALS[node.get("id")]
                ET.SubElement(group, "clip-path", android_attrs(name=name, pathData=frames[0][1]))
            for child in node:
                convert(child, group)
        elif tag == "path":
            index += 1
            attrs = android_attrs(name=node.get("id", f"part_{index}"), pathData=node.attrib["d"])
            mapping = {
                "fill": "fillColor", "stroke": "strokeColor", "stroke-width": "strokeWidth",
                "stroke-linecap": "strokeLineCap", "stroke-linejoin": "strokeLineJoin",
            }
            for source, dest in mapping.items():
                value = node.get(source)
                if value is not None and not value.startswith("url("):
                    attrs[f"{{{ANDROID}}}{dest}"] = "#00000000" if value == "none" else value
            if "fill" not in node.attrib:
                attrs.update(android_attrs(fillColor="#00000000"))
            if animated:
                for name, prop, _, _, frames, _ in native_motion():
                    if name == node.get("id"):
                        attrs.update(android_attrs(**{prop: frames[0][1]}))
            path = ET.SubElement(parent, "path", attrs)
            fill = node.get("fill", "")
            if fill.startswith("url("):
                gradient = gradients[fill[5:-1]]
                attr = ET.SubElement(path, f"{{{AAPT}}}attr", {"name": "android:fillColor"})
                native = ET.SubElement(attr, "gradient", android_attrs(
                    type="linear", startX=gradient.get("x1"), startY=gradient.get("y1"),
                    endX=gradient.get("x2"), endY=gradient.get("y2"),
                ))
                for stop in gradient:
                    ET.SubElement(native, "item", android_attrs(
                        offset=stop.get("offset"), color=stop.get("stop-color"),
                    ))
        else:
            raise ValueError(f"Unsupported SVG artwork element: {tag}")

    convert(svg.find(f"{{{SVG}}}g"), safe)
    return vector


def build_animation():
    root = ET.Element("animated-vector", android_attrs(drawable="@drawable/testtrack_splash_vector_animated"))
    targets = {}
    for name, prop, delay, duration, frames, ease in native_motion():
        if name not in targets:
            target = ET.SubElement(root, "target", android_attrs(name=name))
            attr = ET.SubElement(target, f"{{{AAPT}}}attr", {"name": "android:animation"})
            targets[name] = ET.SubElement(attr, "set", android_attrs(ordering="together"))
        if prop == "pathData":
            # Android's AnimatorInflater only accepts numeric <keyframe> values.
            # Export compatible path morphs with valueFrom/valueTo instead, as in Google's AVD.
            for (f0, p0), (f1, p1) in zip(frames, frames[1:]):
                begin, end = round(f0 * duration), round(f1 * duration)
                ET.SubElement(targets[name], "objectAnimator", android_attrs(
                    propertyName=prop, duration=end-begin, startOffset=delay+begin,
                    valueType="pathType", valueFrom=p0, valueTo=p1,
                    interpolator=f"@interpolator/splash_{ease}",
                ))
            continue
        animator = ET.SubElement(targets[name], "objectAnimator", android_attrs(
            propertyName=prop, duration=duration, startOffset=delay,
            valueType="pathType" if prop == "pathData" else "floatType",
            interpolator="@android:interpolator/linear",
        ))
        holder = ET.SubElement(animator, "propertyValuesHolder", android_attrs(
            propertyName=prop, valueType="pathType" if prop == "pathData" else "floatType",
        ))
        for fraction, value in frames:
            ET.SubElement(holder, "keyframe", android_attrs(
                fraction=fraction, value=value, interpolator=f"@interpolator/splash_{ease}",
            ))
    return root


def build_preview(svg):
    # Browser review uses the same geometry and motion keyframes. The phone runs the AVD.
    css = []
    defs = svg.find(f"{{{SVG}}}defs")
    for group, (name, frames) in REVEALS.items():
        clip = ET.SubElement(defs, f"{{{SVG}}}clipPath", {"id": name + "_clip", "clipPathUnits": "userSpaceOnUse"})
        ET.SubElement(clip, f"{{{SVG}}}path", {"id": name, "d": frames[-1][1]})
        svg.find(f".//*[@id='{group}']").set("clip-path", f"url(#{name}_clip)")
    targets = {}
    for name, prop, delay, duration, frames, ease in MOTION:
        targets.setdefault(name, []).append((prop, delay, duration, frames, ease))
    for name, motions in targets.items():
        node = svg.find(f".//*[@id='{name}']")
        animations = []
        transforms = [m for m in motions if m[0] in ["translateX", "translateY", "rotation", "scaleX", "scaleY"]]
        if transforms:
            _, delay, duration, frames, ease = transforms[0]
            keys = []
            for i, (fraction, _) in enumerate(frames):
                values = {p: f[i][1] for p, _, _, f, _ in transforms}
                transform = f"translate({values.get('translateX', 0)}px,{values.get('translateY', 0)}px) rotate({values.get('rotation', 0)}deg) scale({values.get('scaleX', 1)},{values.get('scaleY', 1)})"
                keys.append(f"{fraction * 100:g}% {{transform:{transform}}}")
            css.append(f"@keyframes {name}_transform {{ {' '.join(keys)} }}")
            animations.append(f"{name}_transform {duration}ms {delay}ms cubic-bezier{EASING[ease]} both")
        for prop, delay, duration, frames, ease in motions:
            if prop in [m[0] for m in transforms]:
                continue
            animation = f"{name}_{prop}"
            if prop == "trimPathEnd":
                node.set("pathLength", "1")
                css.append(f"#{name} {{stroke-dasharray:1; stroke-dashoffset:0}}")
            keys = []
            for fraction, value in frames:
                declaration = {
                    "trimPathEnd": f"stroke-dashoffset:{1 - value}" if prop == "trimPathEnd" else "",
                    "strokeAlpha": f"stroke-opacity:{value}",
                    "fillAlpha": f"fill-opacity:{value}",
                    "opacity": f"opacity:{value}",
                    "pathData": f'd:path("{value}")',
                }[prop]
                keys.append(f"{fraction * 100:g}% {{{declaration}}}")
            css.append(f"@keyframes {animation} {{ {' '.join(keys)} }}")
            animations.append(f"{animation} {duration}ms {delay}ms cubic-bezier{EASING[ease]} both")
        origin = f"{node.get('data-pivot-x', 0)}px {node.get('data-pivot-y', 0)}px"
        css.append(f".playing #{name} {{transform-origin:{origin}; animation:{', '.join(animations)}}}")
    # Leave CSS pixels in source viewBox coordinates, including on nested groups.
    template = (DESIGN / "preview.template.html").read_text(encoding="utf-8")
    artwork = ET.tostring(svg, encoding="unicode").replace("ns0:", "").replace(":ns0", "")
    (DESIGN / "preview.html").write_text(template.replace("{{MOTION}}", "\n".join(css)).replace("{{SVG}}", artwork), encoding="utf-8")
    svg.set("class", "playing")
    style = ET.SubElement(svg, f"{{{SVG}}}style")
    style.text = "\n".join(css) + "\n@media (prefers-reduced-motion:reduce) {g,path {animation:none!important}path {stroke-dashoffset:0!important}}"
    animated = ET.tostring(svg, encoding="unicode").replace("ns0:", "").replace(":ns0", "")
    (DESIGN / "test-exchange-animated.svg").write_text(animated + "\n", encoding="utf-8")


def main():
    svg = ET.parse(DESIGN / "test-exchange.svg").getroot()
    write_xml(RES / "drawable/testtrack_splash_icon.xml", build_vector(svg))
    write_xml(RES / "drawable-v31/testtrack_splash_vector_animated.xml", build_vector(svg, animated=True))
    write_xml(RES / "drawable-v31/testtrack_splash_icon.xml", build_animation())
    for name, (x1, y1, x2, y2) in EASING.items():
        write_xml(RES / f"interpolator/splash_{name}.xml", ET.Element("pathInterpolator", android_attrs(
            controlX1=x1, controlY1=y1, controlX2=x2, controlY2=y2,
        )))
    build_preview(svg)
    print("Exported static vector, Android 12+ animated vector (900ms), and browser preview.")


if __name__ == "__main__":
    main()
