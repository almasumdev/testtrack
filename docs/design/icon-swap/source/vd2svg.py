"""Android vector drawable -> SVG (groups with translate, fill/stroke colours, aapt linear gradients). Enough for the robot."""
import sys
import xml.etree.ElementTree as ET

A = "{http://schemas.android.com/apk/res/android}"


def col(c):
    c = c.strip()
    if len(c) == 9:  # #AARRGGBB
        a = int(c[1:3], 16) / 255
        return "#" + c[3:], a
    return c, 1.0


src, out = sys.argv[1], sys.argv[2]
root = ET.parse(src).getroot()
vw, vh = root.get(A + "viewportWidth"), root.get(A + "viewportHeight")
defs, body, n = [], [], [0]


def grad(el):
    for attr in el.iter("{http://schemas.android.com/aapt}attr"):
        g = attr.find("gradient")
        if g is None:
            continue
        n[0] += 1
        gid = f"g{n[0]}"
        stops = "".join(
            f'<stop offset="{it.get(A + "offset")}" stop-color="{col(it.get(A + "color"))[0]}" stop-opacity="{col(it.get(A + "color"))[1]:.3f}"/>'
            for it in g.findall("item"))
        defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{g.get(A + "startX")}" y1="{g.get(A + "startY")}" '
                    f'x2="{g.get(A + "endX")}" y2="{g.get(A + "endY")}">{stops}</linearGradient>')
        return f"url(#{gid})"
    return None


def walk(el):
    for ch in el:
        if ch.tag == "group":
            tx, ty = ch.get(A + "translateX", "0"), ch.get(A + "translateY", "0")
            body.append(f'<g transform="translate({tx} {ty})">')
            walk(ch)
            body.append("</g>")
        elif ch.tag == "path":
            d = ch.get(A + "pathData")
            fill = ch.get(A + "fillColor")
            g = grad(ch)
            attrs = []
            if g:
                attrs.append(f'fill="{g}"')
            elif fill:
                c, a = col(fill)
                attrs.append(f'fill="{c}" fill-opacity="{a:.3f}"')
            else:
                attrs.append('fill="none"')
            sc = ch.get(A + "strokeColor")
            if sc:
                c, a = col(sc)
                attrs.append(f'stroke="{c}" stroke-opacity="{a:.3f}" stroke-width="{ch.get(A + "strokeWidth", "1")}" '
                             f'stroke-linecap="{ch.get(A + "strokeLineCap", "butt")}" stroke-linejoin="{ch.get(A + "strokeLineJoin", "miter")}"')
            body.append(f'<path d="{d}" {" ".join(attrs)}/>')


walk(root)
open(out, "w", encoding="utf-8").write(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>')
print("ok", len(body), "elements")
