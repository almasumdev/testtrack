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
#   TESTTRACK_VPS         ssh host or alias the isolated staging backend runs on
#   TESTTRACK_REMOTE_DIR  the directory holding its docker-compose.yml
#   TESTTRACK_STAGING_ACK  isolated-staging (required acknowledgment; never use production)
#   TESTTRACK_REVIEWER_EMAIL  Google account that owns both Drive fixture images
#   TESTTRACK_DRIVE_TOKEN     transient drive.file grant, never commit or print
#   TESTTRACK_LIVE_DRIVE_PROOF / TESTTRACK_CLOSED_DRIVE_PROOF  distinct drive:<file-id> refs
#
#   scripts/e2e.sh                 seed, run, clean
#   scripts/e2e.sh --keep          leave the seeded world in place to poke at by hand
#
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
[[ "$SERIAL" == "emulator-5554" ]] || { echo 'Only emulator-5554 is allowed.' >&2; exit 2; }
[[ "${TESTTRACK_STAGING_ACK:-}" == "isolated-staging" ]] || { echo 'Set TESTTRACK_STAGING_ACK=isolated-staging for an isolated test backend.' >&2; exit 2; }
REVIEWER_EMAIL="${TESTTRACK_REVIEWER_EMAIL:?set the isolated Drive fixture account email}"
[[ "$REVIEWER_EMAIL" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]] || exit 2
DRIVE_TOKEN="${TESTTRACK_DRIVE_TOKEN:?set a transient Drive grant}"
LIVE_PROOF="${TESTTRACK_LIVE_DRIVE_PROOF:?set an owned live proof reference}"
CLOSED_PROOF="${TESTTRACK_CLOSED_DRIVE_PROOF:?set a different owned closed proof reference}"
[[ "$LIVE_PROOF" =~ ^drive:[a-zA-Z0-9_-]+$ && "$CLOSED_PROOF" =~ ^drive:[a-zA-Z0-9_-]+$ && "$LIVE_PROOF" != "$CLOSED_PROOF" ]] || exit 2
VPS="${TESTTRACK_VPS:?set TESTTRACK_VPS to the host the backend runs on}"
# Git Bash ships an OpenSSH that cannot read a modern ed25519 key on this machine, so the
# Windows one is used when it is there. Override SSH to point at whichever works for you.
SSH="${SSH:-$( [ -x /c/Windows/System32/OpenSSH/ssh.exe ] && echo /c/Windows/System32/OpenSSH/ssh.exe || echo ssh )}"
REMOTE_DIR="${TESTTRACK_REMOTE_DIR:?set TESTTRACK_REMOTE_DIR to the backend checkout on that host}"
# Both journeys, since they share one seeded world and seeding is the slow part.
PACKAGE="com.eazyverse.testtrack.net"
KEEP="${1:-}"

# The physical phone is never a test target. Naming the serial explicitly is the only thing that
# stops connectedAndroidTest fanning out to every attached device.
if ! adb devices | grep -q "^${SERIAL}[[:space:]]*device$"; then
  echo "No emulator at ${SERIAL}. Start it first, or set ANDROID_SERIAL." >&2
  exit 1
fi

seed() {
  "$SSH" "$VPS" "cd $REMOTE_DIR && docker compose exec -T api python -m scripts.seed_e2e --reviewer-email '$REVIEWER_EMAIL'"
}

# Only ever this run's world. Seeding by hand to look at the app is a normal thing to be doing
# at the same time, and a test suite must not clear somebody's screen out from under them.
clean() {
  local cmd="cd $REMOTE_DIR && docker compose exec -T api"
  "$SSH" "$VPS" "$cmd python -m scripts.seed_e2e --clean --run $RUN" >/dev/null
}

echo "Seeding..."
WORLD="$(seed | tail -n 1)"
RUN="$(printf '%s' "$WORLD" | python -c "import json,sys;print(json.load(sys.stdin)['run'])")"

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

echo "Running ${PACKAGE} journeys on ${SERIAL}..."
ANDROID_SERIAL="$SERIAL" ./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.package="$PACKAGE" \
  -Pandroid.testInstrumentationRunnerArguments.reviewerAccess="$REVIEWER_ACCESS" \
  -Pandroid.testInstrumentationRunnerArguments.reviewerRefresh="$REVIEWER_REFRESH" \
  -Pandroid.testInstrumentationRunnerArguments.ownerAccess="$OWNER_ACCESS" \
  -Pandroid.testInstrumentationRunnerArguments.ownerRefresh="$OWNER_REFRESH" \
  -Pandroid.testInstrumentationRunnerArguments.livePostId="$LIVE_POST" \
  -Pandroid.testInstrumentationRunnerArguments.closedPostId="$CLOSED_POST" \
  -Pandroid.testInstrumentationRunnerArguments.liveDriveProof="$LIVE_PROOF" \
  -Pandroid.testInstrumentationRunnerArguments.closedDriveProof="$CLOSED_PROOF" \
  -Pandroid.testInstrumentationRunnerArguments.driveToken="$DRIVE_TOKEN"
