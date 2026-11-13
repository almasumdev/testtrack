"""Checks that must pass before a version change or a release.

Run as the release tool's pre-deploy hook (mkit, the maintainer's own tooling), which makes
them part of the release path rather than a comment somebody has to remember.
Each command keeps its real exit code; a failure stops before the next one.
Device tests are run by hand. This only compiles their Kotlin sources.
"""

from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_URL = "https://testtrack.eazyverse.com/api/v1/version"
SKIP_FLAG = "--skip-server-check"


def leading_number(text: str) -> int:
    """The migration number at the front of a name such as "0032_app_boxes_and_checks"."""
    match = re.match(r"\s*(\d+)", text)
    if match is None:
        raise ValueError(f"No migration number in {text!r}")
    return int(match.group(1))


def schema_problem(live: str, needed: str) -> str | None:
    """None when the live server has the schema the build needs, otherwise the message to show."""
    live_number = leading_number(live)
    needed_number = leading_number(needed)
    if live_number >= needed_number:
        return None
    return (f"Live server is on {live_number:04d}. This build needs {needed_number:04d}. "
            "Promote the backend first.")


def needed_schema() -> str:
    for line in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines():
        key, _, value = line.partition("=")
        if key.strip() == "TESTTRACK_SERVER_SCHEMA":
            return value.strip()
    raise ValueError("TESTTRACK_SERVER_SCHEMA is missing from gradle.properties")


def live_schema() -> str:
    try:
        with urllib.request.urlopen(VERSION_URL, timeout=10) as response:
            return str(json.load(response)["schema"])
    except urllib.error.HTTPError as error:
        reason = (f"{VERSION_URL} answered {error.code}; the endpoint may not be live yet"
                  if error.code == 404 else f"{VERSION_URL} answered {error.code}")
    except (urllib.error.URLError, TimeoutError, OSError) as error:
        reason = f"{error}"
    except (ValueError, KeyError, TypeError):
        reason = f"{VERSION_URL} did not return a schema"
    raise ValueError(f"Couldn't read the live server's version ({reason}). "
                     "Check it and promote the backend first.")


def check_server_schema() -> None:
    problem = schema_problem(live_schema(), needed_schema())
    if problem:
        raise ValueError(problem)
    print("Live server schema is new enough.", flush=True)


def self_test() -> None:
    assert leading_number("0032_app_boxes_and_checks") == 32
    assert leading_number("0032") == 32
    assert schema_problem("0032_app_boxes_and_checks", "0032") is None
    assert schema_problem("0040_later", "0032") is None
    assert schema_problem("0031_older", "0032") == (
        "Live server is on 0031. This build needs 0032. Promote the backend first.")
    try:
        leading_number("latest")
    except ValueError:
        pass
    else:
        raise AssertionError("a name without a number must be rejected")
    print("check_release self-test passed.")


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
    if "--self-test" in sys.argv:
        self_test()
        return 0
    if SKIP_FLAG in sys.argv:
        print("WARNING: " + SKIP_FLAG + " is on. The live server's schema was NOT checked. "
              "Releasing before the backend is promoted can break the app for everyone.",
              file=sys.stderr, flush=True)
    else:
        check_server_schema()

    executable = shutil.which("mkit.exe" if os.name == "nt" else "mkit")
    if executable is None:
        print("mkit is not installed. It is the maintainer's release tool; building the app does not need it.", file=sys.stderr)
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
