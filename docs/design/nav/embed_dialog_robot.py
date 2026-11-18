"""Embed the native dialog robot's existing vector layers for browser comparison."""
import base64
from pathlib import Path
import xml.etree.ElementTree as ET

A = '{http://schemas.android.com/apk/res/android}'
S = 'http://www.w3.org/2000/svg'
RES = Path(__file__).resolve().parents[3] / 'app/src/main/res/drawable'
ET.register_namespace('', S)


def color(value):
    if value and value.startswith('#') and len(value) == 9:
        return '#' + value[3:], str(int(value[1:3], 16) / 255)
    return value or 'none', None


def build(layers):
    out = ET.Element('{' + S + '}svg', {'viewBox': '0 0 880 980'})
    defs = ET.SubElement(out, '{' + S + '}defs')

    def convert(node, parent):
        tag = node.tag.rsplit('}', 1)[-1]
        if tag == 'group':
            tx, ty, px, py, rotation, sx, sy = [float(node.get(A + k, d)) for k, d in [('translateX', '0'), ('translateY', '0'), ('pivotX', '0'), ('pivotY', '0'), ('rotation', '0'), ('scaleX', '1'), ('scaleY', '1')]]
            transform = f'translate({tx + px} {ty + py}) rotate({rotation}) scale({sx} {sy}) translate({-px} {-py})'
            group = ET.SubElement(parent, '{' + S + '}g', {'transform': transform})
            for child in node:
                convert(child, group)
        elif tag == 'path':
            attrs = {'d': node.get(A + 'pathData', ''), 'fill': 'none'}
            for name, target in [('fillColor', 'fill'), ('strokeColor', 'stroke')]:
                if node.get(A + name):
                    value, opacity = color(node.get(A + name))
                    attrs[target] = value
                    if opacity is not None:
                        attrs[target + '-opacity'] = opacity
            for name, target in [('strokeWidth', 'stroke-width'), ('strokeLineCap', 'stroke-linecap'), ('strokeLineJoin', 'stroke-linejoin'), ('fillAlpha', 'fill-opacity'), ('strokeAlpha', 'stroke-opacity')]:
                if node.get(A + name):
                    attrs[target] = node.get(A + name)
            if node.get(A + 'fillType') == 'evenOdd':
                attrs['fill-rule'] = 'evenodd'
            for child in node:
                if child.tag.rsplit('}', 1)[-1] != 'attr':
                    continue
                for grad in child:
                    if grad.tag.rsplit('}', 1)[-1] != 'gradient':
                        continue
                    gid = 'robot-gradient-' + str(len(defs))
                    radial = grad.get(A + 'type') == 'radial'
                    props = {'id': gid, 'gradientUnits': 'userSpaceOnUse'}
                    mapping = [('centerX', 'cx'), ('centerY', 'cy'), ('gradientRadius', 'r')] if radial else [('startX', 'x1'), ('startY', 'y1'), ('endX', 'x2'), ('endY', 'y2')]
                    for name, target in mapping:
                        props[target] = grad.get(A + name, '0')
                    gradient = ET.SubElement(defs, '{' + S + '}' + ('radialGradient' if radial else 'linearGradient'), props)
                    for stop in grad:
                        value, opacity = color(stop.get(A + 'color'))
                        props = {'offset': stop.get(A + 'offset', '0'), 'stop-color': value}
                        if opacity is not None:
                            props['stop-opacity'] = opacity
                        ET.SubElement(gradient, '{' + S + '}stop', props)
                    attrs['stroke' if child.get('name') == 'android:strokeColor' else 'fill'] = 'url(#' + gid + ')'
            ET.SubElement(parent, '{' + S + '}path', attrs)

    for layer in layers:
        for child in ET.parse(RES / (layer + '.xml')).getroot():
            convert(child, out)
    return 'data:image/svg+xml;base64,' + base64.b64encode(ET.tostring(out)).decode()


source = Path(__file__).with_name('no-review-dialog-comparison.html')
markup = source.read_text(encoding='utf-8')
markup = markup.replace('data-robot-head', 'src="' + build(['level_robot_antenna', 'level_robot_head', 'level_robot_eyes']) + '"')
markup = markup.replace('data-robot-hands', 'src="' + build(['level_robot_hands']) + '"')
source.write_text(markup, encoding='utf-8')
print('Embedded original app robot layers; source bytes:', source.stat().st_size)
