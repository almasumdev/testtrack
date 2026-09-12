#!/usr/bin/env bash
#
# The end-to-end run: seed a world on the server, drive the app through it, take it down again.
#
# The app signs in through Google and a test cannot, so the session is minted on the server by
# scripts/seed_e2e.py in the backend repository and handed here as instrumentation arguments.
# Nothing in the app or the API knows about that path, and nothing is left switched on afterwards.
#
# Tokens live in this script's memory and in the adb command line for the length of one run. They
# are never written to a file and must never be committed: this repository is public.
#
# This repository is public, so where the backend lives is not written down here. Point these at
# your own checkout before running:
#
#   TESTTRACK_VPS         ssh host or alias the backend runs on
#   TESTTRACK_REMOTE_DIR  the directory holding its docker-compose.yml
#
#   scripts/e2e.sh                 seed, run, clean
#   scripts/e2e.sh --keep          leave the seeded world in place to poke at by hand
#
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
VPS="${TESTTRACK_VPS:?set TESTTRACK_VPS to the host the backend runs on}"
# Git Bash ships an OpenSSH that cannot read a modern ed25519 key on this machine, so the
# Windows one is used when it is there. Override SSH to point at whichever works for you.
SSH="${SSH:-$( [ -x /c/Windows/System32/OpenSSH/ssh.exe ] && echo /c/Windows/System32/OpenSSH/ssh.exe || echo ssh )}"
REMOTE_DIR="${TESTTRACK_REMOTE_DIR:?set TESTTRACK_REMOTE_DIR to the backend checkout on that host}"
CLASS="com.eazyverse.testtrack.net.ReviewJourneyTest"
KEEP="${1:-}"

# The physical phone is never a test target. Naming the serial explicitly is the only thing that
# stops connectedAndroidTest fanning out to every attached device.
if ! adb devices | grep -q "^${SERIAL}[[:space:]]*device$"; then
  echo "No emulator at ${SERIAL}. Start it first, or set ANDROID_SERIAL." >&2
  exit 1
fi

seed() {
  "$SSH" "$VPS" "cd $REMOTE_DIR && docker compose exec -T api python -m scripts.seed_e2e"
}

clean() {
  "$SSH" "$VPS" "cd $REMOTE_DIR && docker compose exec -T api python -m scripts.seed_e2e --clean" >/dev/null
}

echo "Seeding..."
WORLD="$(seed | tail -n 1)"

if [[ "$KEEP" != "--keep" ]]; then
  # Runs on any exit, including a failed test run, so a red suite does not leave rows behind.
  trap 'echo "Cleaning up..."; clean || echo "Could not clean up; run seed_e2e.py --clean by hand." >&2' EXIT
fi

field() { printf '%s' "$WORLD" | python -c "import json,sys;d=json.load(sys.stdin);print(d$1)"; }

REVIEWER_ACCESS="$(field "['reviewer']['access']")"
REVIEWER_REFRESH="$(field "['reviewer']['refresh']")"
OWNER_ACCESS="$(field "['owner']['access']")"
OWNER_REFRESH="$(field "['owner']['refresh']")"
LIVE_POST="$(field "['live_post']['id']")"
CLOSED_POST="$(field "['closed_post']['id']")"

echo "Running ${CLASS} on ${SERIAL}..."
ANDROID_SERIAL="$SERIAL" ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class="$CLASS" \
  -Pandroid.testInstrumentationRunnerArguments.reviewerAccess="$REVIEWER_ACCESS" \
  -Pandroid.testInstrumentationRunnerArguments.reviewerRefresh="$REVIEWER_REFRESH" \
  -Pandroid.testInstrumentationRunnerArguments.ownerAccess="$OWNER_ACCESS" \
  -Pandroid.testInstrumentationRunnerArguments.ownerRefresh="$OWNER_REFRESH" \
  -Pandroid.testInstrumentationRunnerArguments.livePostId="$LIVE_POST" \
  -Pandroid.testInstrumentationRunnerArguments.closedPostId="$CLOSED_POST"
