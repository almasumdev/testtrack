"""The app's architecture rules, checked.

The backend has had rules enforced in CI since before its first line and they held. The app
predates that decision, so it has violations today. Failing on all of them would mean a red build
until somebody finds a week, which in practice means the rules never start working at all.

So this is a ratchet. Everything currently wrong is listed in `app-baseline.txt` and passes;
anything new fails. The baseline may only shrink, and it is the queue: it says exactly what is
left, and nothing has to be remembered.

    python scripts/check_app.py            # fail on anything not in the baseline
    python scripts/check_app.py --update   # after fixing some, write the shorter baseline

See docs/app-rules.md for what the rules are and why.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

SRC = Path("app/src/main/java/com/eazyverse/testtrack")
BASELINE = Path("scripts/app-baseline.txt")

MAX_LINES = 500

#: Which package a file belongs to is its top folder under the source root. A file sitting
#: directly in the root (MainActivity and friends) is the shell and is exempt: it is the one place
#: that must see everything in order to wire it together.
LAYERS = {
    "ui": {"forbidden": ("data", "net"), "why": "a screen that can reach the network cannot be tested without one"},
    "domain": {"forbidden": ("data", "net", "ui"), "why": "domain is what runs on a desk in milliseconds"},
    "data": {"forbidden": ("ui", "viewmodel"), "why": "data is called, it does not call back up"},
}

IMPORT = re.compile(r"^import com\.eazyverse\.testtrack\.([a-z]+)\.", re.M)
ANDROID_IMPORT = re.compile(r"^import (android|androidx)\.", re.M)
COLOR_LITERAL = re.compile(
    r"\bColor\s*\(|\bColor\.(hsl|hsv|parseColor|rgb|argb|White|Black|Red|Green|Blue|Yellow|Cyan|Magenta|Gray|LightGray|DarkGray)\b"
)



def layer_of(path: Path) -> str | None:
    parts = path.relative_to(SRC).parts
    return parts[0] if len(parts) > 1 else None


def problems() -> list[str]:
    """Every violation, as one stable line each so the baseline can be diffed."""
    found: list[str] = []
    for path in sorted(SRC.rglob("*.kt")):
        rel = path.as_posix()
        text = path.read_text(encoding="utf-8", errors="replace")
        lines = text.count("\n") + 1

        if lines > MAX_LINES:
            found.append(f"{rel}: {lines} lines, over {MAX_LINES}")

        layer = layer_of(path)
        rule = LAYERS.get(layer or "")
        if rule:
            for imported in sorted(set(IMPORT.findall(text))):
                if imported in rule["forbidden"]:
                    found.append(f"{rel}: {layer} imports {imported}")

        # A pure layer stops being pure the moment it needs a device to run.
        if layer == "domain" and ANDROID_IMPORT.search(text):
            found.append(f"{rel}: domain imports android")

        # Colours live in ui/theme/Tokens.kt and nowhere else, so a palette is one file to change
        # rather than a search through every screen.
        if path != SRC / "ui/theme/Tokens.kt" and COLOR_LITERAL.search(text):
            found.append(f"{rel}: colour literal outside ui/theme/Tokens.kt")

    return found


HEADER = (
    "# Known violations of docs/app-rules.md. This file may only shrink.\n"
    "# Fix one, then: python scripts/check_app.py --update\n"
)


def _write(entries: list[str]) -> None:
    BASELINE.write_text(
        HEADER + "".join(f"{e}\n" for e in entries), encoding="utf-8", newline="\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--update", action="store_true",
                        help="write the current violations as the baseline, if it would shrink")
    parser.add_argument("--seed", action="store_true",
                        help="write the first baseline. Refused once one exists: after that the "
                             "only way the list changes is downwards.")
    parser.add_argument("--colors-only", action="store_true", help="enforce the palette rule on every Android build")
    args = parser.parse_args()

    if not SRC.is_dir():
        print(f"run this from the repository root; {SRC} not found", file=sys.stderr)
        return 2

    now = problems()
    # Colour rules cannot be waived through the legacy architecture baseline.
    colour_errors = [p for p in now if "colour literal" in p]
    if colour_errors:
        print("\n".join(colour_errors), file=sys.stderr)
        return 1
    from sync_theme_colors import sync
    if not sync(check=True):
        print("Native theme colours are stale; run python scripts/sync_theme_colors.py", file=sys.stderr)
        return 1
    if args.colors_only:
        print("All component colours use Tokens.kt; native resources are current")
        return 0
    was = (
        [l.strip() for l in BASELINE.read_text(encoding="utf-8").splitlines()
         if l.strip() and not l.startswith("#")]
        if BASELINE.exists() else []
    )

    if args.seed:
        if was:
            print(f"{BASELINE} already has {len(was)} entries. Use --update, which may only "
                  f"shrink it.", file=sys.stderr)
            return 1
        _write(now)
        print(f"baseline seeded with {len(now)} known violations")
        return 0

    if args.update:
        # Refused rather than allowed with a warning. A baseline that can grow is not a ratchet,
        # it is a list of things somebody once meant to fix.
        added = [p for p in now if p not in was]
        if added:
            print("refusing: this would add to the baseline rather than shrink it", file=sys.stderr)
            for p in added:
                print(f"  + {p}", file=sys.stderr)
            return 1
        _write(now)
        print(f"baseline now {len(now)}, was {len(was)}")
        return 0

    new = [p for p in now if p not in was]
    fixed = [p for p in was if p not in now]

    for p in new:
        print(f"NEW  {p}")
    if fixed:
        print(f"\n{len(fixed)} baseline entries no longer apply. "
              f"Run: python scripts/check_app.py --update")
        for p in fixed:
            print(f"  - {p}")

    if new:
        print(f"\n{len(new)} new violation(s). See docs/app-rules.md.", file=sys.stderr)
        return 1

    print(f"no new violations ({len(now)} known, see {BASELINE})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
