#!/usr/bin/env bash
#
# Sends a TestTrack reminder through FCM HTTP v1.
#
#   tools/push.sh group seed-group-a "4 apps still waiting" "Open TestTrack and clear day 4."
#   tools/push.sh uid   <firebase-uid> "You're behind" "Two apps left in Group A."
#   tools/push.sh token <fcm-token>    "Test" "Does this arrive?"
#
# There is no backend yet. This does what a scheduled job or the admin app will eventually do, with
# an owner credential from gcloud, so the client side can be exercised today.
#
#   gcloud auth login
#   gcloud config set project <your firebase project>
#
# `group` is the cheap one: every device subscribes to `group_<id>` on the way in, so one call
# reaches a whole cohort without reading a single token. `uid` fans out to that account's devices,
# which is what a personal "you specifically are behind" needs.
#
# Environment:
#   TESTTRACK_PROJECT   optional. Defaults to the active gcloud project.
#   TESTTRACK_CURL_OPTS optional. Extra flags for curl. On Windows, curl uses schannel and can
#                       fail with CRYPT_E_REVOCATION_OFFLINE when it cannot reach a revocation
#                       server; `--ssl-no-revoke` gets past that. It is a real weakening of
#                       certificate checking, so it stays opt-in rather than baked in here.
#
# Messages are sent DATA-ONLY on purpose. A `notification` block would be posted by the system
# whenever the app is backgrounded, bypassing PushService — wrong icon, wrong channel, and no way
# to open the group it is about. See PushService.kt.
set -euo pipefail

MODE="${1:-}"
TARGET="${2:-}"
TITLE="${3:-}"
BODY="${4:-}"

case "$MODE" in
    group|uid|token) ;;
    *) echo "usage: $0 group|uid|token <target> <title> [body]"; exit 1 ;;
esac
[ -n "$TARGET" ] || { echo "missing target"; exit 1; }
[ -n "$TITLE" ]  || { echo "missing title"; exit 1; }

PROJECT="${TESTTRACK_PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
[ -n "$PROJECT" ] || { echo "no project — set TESTTRACK_PROJECT or gcloud config set project"; exit 1; }

TOKEN=$(gcloud auth print-access-token)
FCM="https://fcm.googleapis.com/v1/projects/$PROJECT/messages:send"
FIRESTORE="https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"

read -r -a CURL_OPTS <<< "${TESTTRACK_CURL_OPTS:-}"

# Escapes for embedding in JSON: backslash, quote, newline. Pure parameter expansion rather than
# sed and tr, which are byte-oriented here and quietly mangle anything outside ASCII — an em dash
# in a reminder came out the other end as whitespace.
esc() {
    local s=$1
    s=${s//\\/\\\\}
    s=${s//\"/\\\"}
    s=${s//$'\n'/ }
    printf '%s' "$s"
}

# The data payload every mode shares. groupId is optional and only present where it is meaningful;
# PushService uses it to open that group instead of home.
payload() {
    local group_field=""
    [ -n "${1:-}" ] && group_field=",\"groupId\":\"$(esc "$1")\""
    printf '"data":{"title":"%s","body":"%s"%s},"android":{"priority":"HIGH"}' \
        "$(esc "$TITLE")" "$(esc "$BODY")" "$group_field"
}

# `|| rc=$?` rather than a bare call: under `set -e` a curl that cannot even connect — a proxy, a
# revocation check that times out — would take the whole script down without printing why.
#
# The body goes in over a pipe rather than as `-d "$body"`. On Windows, arguments crossing from
# the shell to a native curl.exe are converted to the console code page, and anything outside it
# is best-fit mapped — an em dash in a reminder reached the phone as a space, with the correct
# UTF-8 present in the payload right up to the call. A pipe carries bytes and is not converted.
send() {
    local body=$1 out rc=0
    out=$(printf '%s' "$body" | curl -sS "${CURL_OPTS[@]}" -w '\n%{http_code}' -X POST "$FCM" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json; charset=utf-8" --data-binary @- 2>&1) || rc=$?

    if [ "$rc" -ne 0 ]; then
        echo "  curl failed (exit $rc): ${out%%$'\n'*}"
        return 1
    fi

    local code=${out##*$'\n'}
    if [ "$code" = "200" ]; then
        echo "  sent"
    else
        echo "  FAILED (HTTP $code): ${out%$'\n'*}"
        return 1
    fi
}

case "$MODE" in
    group)
        echo "topic group_$TARGET"
        send "{\"message\":{\"topic\":\"group_$TARGET\",$(payload "$TARGET")}}"
        ;;

    token)
        echo "token ${TARGET:0:16}…"
        send "{\"message\":{\"token\":\"$TARGET\",$(payload "")}}"
        ;;

    uid)
        # One send per device. FCM has no "send to a user", so the tokens have to be read first —
        # which is the whole reason topics exist for the group case.
        docs=$(curl -sS "${CURL_OPTS[@]}" "$FIRESTORE/users/$TARGET/tokens" \
            -H "Authorization: Bearer $TOKEN")

        # Firestore's REST shape wraps every value in its type: `"token": {"stringValue": "…"}`.
        # jq if it is here, and if not, key off the field name and take the value on the next line
        # — which holds whatever order the fields come back in.
        if command -v jq >/dev/null 2>&1; then
            mapfile -t tokens < <(
                printf '%s' "$docs" | jq -r '.documents[]?.fields.token.stringValue // empty'
            )
        else
            mapfile -t tokens < <(
                printf '%s' "$docs" | grep -A1 '"token"' \
                    | grep -o '"stringValue": *"[^"]*"' | cut -d'"' -f4
            )
        fi
        [ ${#tokens[@]} -gt 0 ] || { echo "no registered devices for $TARGET"; exit 1; }
        echo "${#tokens[@]} device(s) for $TARGET"
        for t in "${tokens[@]}"; do
            echo "  ${t:0:16}…"
            send "{\"message\":{\"token\":\"$t\",$(payload "")}}"
        done
        ;;
esac
