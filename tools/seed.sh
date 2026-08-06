#!/usr/bin/env bash
#
# Fills Firestore with a cohort you can walk the whole app against, and takes it out again.
#
#   TESTTRACK_UID=<your firebase uid> tools/seed.sh up
#   TESTTRACK_UID=<your firebase uid> tools/seed.sh down
#
# There is no admin app yet, so groups and placements — which the security rules reserve for an
# admin — can only be created out of band. This writes them with an owner credential from gcloud,
# which bypasses rules the same way the admin app eventually will.
#
#   gcloud auth login
#   gcloud config set project <your firebase project>
#
# Environment:
#   TESTTRACK_UID       required. Your Firebase uid — read it from users/ in the console,
#                       or from the app's Firestore `users` collection after signing in once.
#   TESTTRACK_EMAIL     optional. Shown as the owner address on your seeded apps.
#   TESTTRACK_PROJECT   optional. Defaults to the active gcloud project.
#   TESTTRACK_PKGS      optional. Space-separated packages that are actually installed on the
#                       test phone. These get real launcher icons and working Open buttons;
#                       everything else seeds as "Install".
set -euo pipefail

UID_ME="${TESTTRACK_UID:?set TESTTRACK_UID to your Firebase uid}"
EMAIL_ME="${TESTTRACK_EMAIL:-you@example.com}"
PROJECT="${TESTTRACK_PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
[ -n "$PROJECT" ] || { echo "no project — set TESTTRACK_PROJECT or gcloud config set project"; exit 1; }

BASE="https://firestore.googleapis.com/v1/projects/$PROJECT/databases/(default)/documents"
TOKEN=$(gcloud auth print-access-token)
DAY_MS=86400000

# Installed on the phone: real icons, working Open. Override for your own device.
read -r -a REAL <<< "${TESTTRACK_PKGS:-com.daraz.android com.linkedin.android com.adobe.reader com.macdom.ble.blescanner}"

# Never installed: these exercise the "Install" state. One long pool, sliced so no package
# appears in two groups — an app document is keyed by its package name, so a shared name would
# silently move the app out of the first group and into the second.
POOL=(Fernpost Halo Kettle Lumen Marrow Nimbus Orchid Pebble Quill Rowan Sable Thistle
      Umber Verdant Willow Yarrow Zephyr Cobalt Dovetail Cinder Harbour Juniper Kestrel
      Lantern Meadow Onyx)
FAKE=()
for n in "${POOL[@]}"; do FAKE+=("com.seed.$(echo "$n" | tr '[:upper:]' '[:lower:]')"); done

MINE_A="${TESTTRACK_MY_APP:-com.seed.myapp}"

put() { curl -s -X PATCH "$BASE/$1" -H "Authorization: Bearer $TOKEN" \
          -H "Content-Type: application/json" -d "$2" >/dev/null; }
del() { curl -s -X DELETE "$BASE/$1" -H "Authorization: Bearer $TOKEN" >/dev/null; }

str() { printf '{"stringValue":"%s"}' "$1"; }
int() { printf '{"integerValue":"%s"}' "$1"; }

# ---- teardown ---------------------------------------------------------------------------
if [ "${1:-up}" = "down" ]; then
    for g in seed-group-a seed-group-b seed-group-c; do del "groups/$g" & done
    for p in "${REAL[@]}" "${FAKE[@]}" "$MINE_A" com.seed.notely com.seed.parcel; do
        del "apps/$p" &
        for t in $(seq 1 13) "$UID_ME"; do
            for d in $(seq 0 9); do del "proofs/${p}__seed-tester-${t}__${d}" & done
            for d in $(seq 0 9); do del "proofs/${p}__${UID_ME}__${d}" & done
        done
    done
    for i in $(seq 1 13); do del "users/seed-tester-$i" & done
    wait
    echo "seed removed from $PROJECT"
    exit 0
fi

NOW=$(($(date +%s) * 1000))

# ---- the cast ---------------------------------------------------------------------------
FIRST=(Asif Rifat Nabil Sadia Munir Rony Tanha Imran Farhan Sumaiya Jubayer Nadia Shakil)
for i in $(seq 1 13); do
    put "users/seed-tester-$i" "{\"fields\":{
      \"uid\":$(str "seed-tester-$i"),
      \"email\":$(str "tester$i@example.com"),
      \"displayName\":$(str "${FIRST[$((i-1))]}"),
      \"updatedAt\":$(int "$NOW")}}" &
done

# ---- a group ----------------------------------------------------------------------------
# group <id> <name> <members incl. me> <startDate ms, 0 = forming> <status> <my app> <others…>
group() {
    local id=$1 name=$2 count=$3 start=$4 status=$5 mine=$6; shift 6
    local others=("$@")

    local members; members=$(str "$UID_ME")
    for i in $(seq 1 $((count - 1))); do members="$members,$(str "seed-tester-$i")"; done

    local ids; ids=$(str "$mine")
    for p in "${others[@]}"; do ids="$ids,$(str "$p")"; done

    put "groups/$id" "{\"fields\":{
      \"name\":$(str "$name"),
      \"memberUids\":{\"arrayValue\":{\"values\":[$members]}},
      \"appIds\":{\"arrayValue\":{\"values\":[$ids]}},
      \"startDate\":$(int "$start"),
      \"status\":$(str "$status")}}"

    app "$mine" "$id" "$UID_ME" "$EMAIL_ME" "${TESTTRACK_MY_APP_NAME:-My App}"
    local n=1
    for p in "${others[@]}"; do
        app "$p" "$id" "seed-tester-$n" "tester$n@example.com" "$(label "$p" "$n")" &
        n=$((n + 1))
    done
}

label() {
    case "$1" in
        com.seed.*) local key=${1#com.seed.}; echo "${key^}" ;;
        *) echo "$(echo "$1" | awk -F. '{print $2}' | sed 's/.*/\u&/')" ;;
    esac
}

app() {
    put "apps/$1" "{\"fields\":{
      \"id\":$(str "$1"),
      \"ownerUid\":$(str "$3"),
      \"ownerEmail\":$(str "$4"),
      \"name\":$(str "$5"),
      \"packageName\":$(str "$1"),
      \"groupId\":$(str "$2"),
      \"submittedAt\":$(int "$NOW"),
      \"status\":$(str "assigned")}}"
}

# proof <appId> <groupId> <testerUid> <testerName> <day> <usageMs> <startMs>
proof() {
    put "proofs/${1}__${3}__${5}" "{\"fields\":{
      \"appId\":$(str "$1"),
      \"groupId\":$(str "$2"),
      \"testerUid\":$(str "$3"),
      \"testerEmail\":$(str "$4"),
      \"day\":$(int "$5"),
      \"fileId\":$(str "seed"),
      \"imageUrl\":$(str "https://picsum.photos/seed/tt${3}${5}/400/860"),
      \"capturedAt\":$(int "$(( $7 + $5 * DAY_MS ))"),
      \"usageMs\":$(int "$6")}}"
}

# --- Group A: healthy, mid-run. The main flow. -------------------------------------------
# Every installed package goes here, so this is the group with working Open buttons.
START_A=$((NOW - 3 * DAY_MS))
FAKES_A=$((13 - ${#REAL[@]}))
OTHERS_A=("${REAL[@]}" "${FAKE[@]:0:$FAKES_A}")
group seed-group-a "Group A" 14 "$START_A" running "$MINE_A" "${OTHERS_A[@]}"

# --- Group B: forming, still short of the threshold. -------------------------------------
group seed-group-b "Group B" 9 0 forming com.seed.notely "${FAKE[@]:$FAKES_A:8}"

# --- Group C: running but a member short — the at-risk banner. ---------------------------
group seed-group-c "Group C" 12 "$((NOW - 8 * DAY_MS))" running com.seed.parcel \
      "${FAKE[@]:$((FAKES_A + 8)):11}"
wait

# ---- proof history against my Group A app ------------------------------------------------
# Days 0–2 mostly kept, with two clear gaps; today (day 3) part-reported, one under the bar.
for i in $(seq 1 13); do
    for d in 0 1 2; do
        [ "$i" = "4" ] && [ "$d" = "1" ] && continue
        [ "$i" = "9" ] && [ "$d" = "2" ] && continue
        proof "$MINE_A" seed-group-a "seed-tester-$i" "tester$i@example.com" \
              "$d" "$(( 34000 + i * 5000 ))" "$START_A" &
    done
done
for i in $(seq 1 8); do
    usage=$(( 40000 + i * 9000 ))
    [ "$i" = "3" ] && usage=14000          # under 30s — the amber state
    proof "$MINE_A" seed-group-a "seed-tester-$i" "tester$i@example.com" 3 "$usage" "$START_A" &
done

# One app already done today, so the list shows a Done pill beside the Open buttons.
proof "${REAL[0]}" seed-group-a "$UID_ME" "$EMAIL_ME" 3 47000 "$START_A" &
wait

# ---- one submission still waiting for an admin -------------------------------------------
put "apps/com.seed.waiting" "{\"fields\":{
  \"id\":$(str com.seed.waiting),
  \"ownerUid\":$(str "$UID_ME"),
  \"ownerEmail\":$(str "$EMAIL_ME"),
  \"name\":$(str "Waiting Room"),
  \"packageName\":$(str com.seed.waiting),
  \"groupId\":$(str ""),
  \"submittedAt\":$(int "$((NOW - 2 * DAY_MS))"),
  \"status\":$(str pending)}}"

cat <<SUMMARY
seeded $PROJECT

  Group A   running, day 4 of 14, 14 members, 14 apps
            ${#REAL[@]} installed (real icons, Open works), the rest show Install
            ${REAL[0]} already reported today
            your app: $MINE_A — 13 testers, days 1-3 with two misses,
            8 reported today and one of those under 30s
  Group B   forming, 9 of 13 members
  Group C   running, day 9, 12 members - short one, shows the at-risk banner
  Pending   "Waiting Room" awaiting placement

  tools/seed.sh down   removes all of it
SUMMARY
