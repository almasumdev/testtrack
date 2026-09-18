#!/usr/bin/env sh
set -eu
command -v mkit >/dev/null 2>&1 || { echo "mkit is not installed. Install it with: uv tool install -e PATH_TO_MKIT_REPO" >&2; exit 127; }
exec mkit --project "$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)" "$@"
