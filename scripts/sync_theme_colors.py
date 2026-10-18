"""Export native UI colours from Tokens.kt. Called automatically before Android builds.

SVG/vector artwork retains its authored brand colours; these resources control native
surfaces, the splash ground and the notification accent. The floating testing control is
Compose, and reads Tokens.kt itself.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/com/eazyverse/testtrack/ui/theme/Tokens.kt'
ROLES = {
    'paper': 'bg',
    'notification_accent': 'accent',
}


def sync(check=False):
    source = SOURCE.read_text(encoding='utf-8')
    valid = True
    for palette, folder in [('OceanLight', 'values'), ('OceanDark', 'values-night')]:
        block = source.split(f'val {palette} =', 1)[1].split('val ', 1)[0]
        colors = dict(re.findall(r'(\w+) = Color\(0x([0-9A-Fa-f]{8})\)', block))
        entries = ''.join(f'    <color name="{name}">#{colors[role]}</color>\n' for name, role in ROLES.items())
        result = '<?xml version="1.0" encoding="utf-8"?>\n<!-- Generated from ui/theme/Tokens.kt. Do not edit. -->\n<resources>\n' + entries + '</resources>\n'
        path = ROOT / 'app/src/main/res' / folder / 'colors.xml'
        if not path.exists() or path.read_text(encoding='utf-8') != result:
            valid = False
            if not check:
                path.write_text(result, encoding='utf-8', newline='\n')
    return valid if check else True


if __name__ == '__main__':
    sys.exit(0 if sync('--check' in sys.argv) else 1)
