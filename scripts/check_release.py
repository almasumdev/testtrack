"""Checks mkit must finish before changing a version or publishing a release.

The app no longer has CI. Putting its checks in mkit's pre-deploy hook makes
them part of the release path rather than a comment somebody has to remember.
Each command keeps its real exit code; a failure stops before the next one.
Device tests remain the user's job. This only compiles their Kotlin sources.
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def run(argv: list[str], *, capture: bool = False,
        accepted: tuple[int, ...] = (0,)) -> subprocess.CompletedProcess[str]:
    print("Checking: " + " ".join(argv), flush=True)
    result = subprocess.run(argv, cwd=ROOT, text=True, capture_output=capture, check=False)
    if capture:
        if result.stderr:
            print(result.stderr, file=sys.stderr, end="", flush=True)
        if result.stdout:
            print(result.stdout, end="", flush=True)
    if result.returncode not in accepted:
        result.check_returncode()
    return result


def mkit(executable: str, *command: str, allow_drift: bool = False) -> dict:
    result = run([executable, "--project", str(ROOT), *command, "--json"], capture=True,
                 accepted=(0, 10) if allow_drift else (0,))
    envelope = json.loads(result.stdout)
    if not envelope.get("ok"):
        raise ValueError(f"{' '.join(command)} did not succeed")
    return envelope["result"]


def main() -> int:
    executable = shutil.which("mkit.exe" if os.name == "nt" else "mkit")
    if executable is None:
        print("mkit is not installed. See H:/tools/mkit/README.md.", file=sys.stderr)
        return 1

    run([sys.executable, "scripts/check_app.py"])
    gradle = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    run([str(gradle), "ktlintCheck", "testDebugUnitTest", "assembleDebug",
         "compileDebugAndroidTestKotlin", "lintDebug"])

    notes = mkit(executable, "whatsnew", "check")
    if not notes["notes"] or notes["missing"]:
        print("Every configured Play locale needs a nonempty release note.", file=sys.stderr)
        return 1

    # A different local listing is normal before publishing it; missing files,
    # invalid copy and failed Play access still stop the release.
    mkit(executable, "listing", "plan", "--text-only", allow_drift=True)
    # Regenerate from the declaration before checking, so an edited answer can
    # never release with an older CSV. The snapshot commit includes this file.
    mkit(executable, "datasafety", "build")
    mkit(executable, "datasafety", "check")
    art = mkit(executable, "art", "check")
    if art["no_captions"]:
        print("Every listing language needs artwork captions.", file=sys.stderr)
        return 1
    # Building proves the raw captures, fonts and design are available too.
    # Outputs are local and ignored by git; nothing here uploads them to Play.
    mkit(executable, "art", "build")
    print("Release checks passed. Nothing was published.", flush=True)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except subprocess.CalledProcessError as error:
        sys.exit(error.returncode)
    except (KeyError, ValueError) as error:
        print(f"Release checks failed: {error}", file=sys.stderr)
        sys.exit(1)
