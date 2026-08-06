#!/usr/bin/env bash
#
# Checks firestore.rules against the Security Rules test API — including the refusals, which are
# the half that is easy to believe and hard to verify.
#
#   tools/rules-test.sh
#
# Runs the local firestore.rules, not the deployed copy, so a rule can be proved before it is
# released. Needs only a gcloud login:
#
#   gcloud auth login
#   gcloud config set project <your firebase project>
#
# Every ALLOW here has a matching read or write in Repo.kt. Every DENY is something a tester could
# otherwise grant themselves — starting their own 14-day clock, placing their own app into a
# cohort, or reading a proof that is nobody's business but its owner's and its author's.
#
# Environment:
#   TESTTRACK_PROJECT   optional. Defaults to the active gcloud project.
#   TESTTRACK_CURL_OPTS optional. See tools/push.sh — `--ssl-no-revoke` on Windows.
set -euo pipefail

cd "$(dirname "$0")/.."

PROJECT="${TESTTRACK_PROJECT:-$(gcloud config get-value project 2>/dev/null)}"
[ -n "$PROJECT" ] || { echo "no project — set TESTTRACK_PROJECT or gcloud config set project"; exit 1; }
[ -f firestore.rules ] || { echo "firestore.rules not found"; exit 1; }

TOKEN=$(gcloud auth print-access-token)
read -r -a CURL_OPTS <<< "${TESTTRACK_CURL_OPTS:-}"

# Probe rather than take the first hit: on Windows, `python3` on PATH is often the Microsoft Store
# stub, which exits with an advert instead of running anything.
PY=""
for candidate in python3 python py; do
    if command -v "$candidate" >/dev/null 2>&1 && "$candidate" -c "pass" >/dev/null 2>&1; then
        PY=$candidate
        break
    fi
done
[ -n "$PY" ] || { echo "needs python to build the request"; exit 1; }

# Everything moves over pipes, never through a temp file. A native Windows python reads `/tmp/x`
# as H:\tmp\x while MSYS hands curl.exe C:\…\Temp\x, and the two never meet.
#
# The cast: ADMIN is in admins/, MEMBER is in the cohort, OUTSIDER is neither.
built=$("$PY" - <<'PYEOF'
import json

rules = open("firestore.rules", encoding="utf-8").read()

DB      = "/databases/(default)/documents"
ADMIN   = "admin-uid"
MEMBER  = "member-uid"
OUTSIDE = "outsider-uid"
GROUP   = "group-1"
APP     = "com.example.app"

# `resource` and `request.resource` are not the same document and the rules lean on both:
# `resource.data` is what is already stored (what a read is checked against), `request.resource
# .data` is what is being written. The test API keeps them apart too — the stored one on the test
# case, the incoming one on the request — and conflating them makes a passing read rule look
# broken, which is exactly what it did here first time round.
def case(expect, uid, method, path, stored=None, incoming=None, name=""):
    req = {
        "auth": {"uid": uid, "token": {"email": uid + "@example.com"}},
        "path": DB + path,
        "method": method,
        "time": "2026-01-01T00:00:00Z",
    }
    if incoming is not None:
        req["resource"] = {"data": incoming}
    test = {"expectation": expect, "request": req, "_name": name}
    if stored is not None:
        test["resource"] = {"data": stored}
    return test

group_doc = {
    "name": "Group 1",
    "memberUids": [MEMBER],
    "appIds": [APP],
    "startDate": 0,
    "status": "forming",
}
proof_doc = {
    "appId": APP, "groupId": GROUP, "ownerUid": MEMBER,
    "testerUid": MEMBER, "day": 3, "usageMs": 31000,
}

token_doc = {"token": "abc", "platform": "android", "updatedAt": 1}

cases = [
    # --- the whole admin mechanism -------------------------------------------------------
    case("DENY",  OUTSIDE, "create", "/groups/" + GROUP, incoming=group_doc,
         name="a tester cannot create a group"),
    case("DENY",  MEMBER,  "update", "/groups/" + GROUP, stored=group_doc, incoming=group_doc,
         name="a member cannot edit their own group (no self-started clock)"),
    case("ALLOW", ADMIN,   "create", "/groups/" + GROUP, incoming=group_doc,
         name="an admin can create a group"),

    # --- placement is not the owner's to grant -------------------------------------------
    case("DENY",  OUTSIDE, "create", "/apps/" + APP,
         incoming={"ownerUid": OUTSIDE, "status": "assigned", "groupId": GROUP},
         name="an owner cannot register an app already placed"),
    case("ALLOW", OUTSIDE, "create", "/apps/" + APP,
         incoming={"ownerUid": OUTSIDE, "status": "pending", "groupId": ""},
         name="an owner can register a pending app"),
    case("DENY",  OUTSIDE, "update", "/apps/" + APP,
         stored={"ownerUid": OUTSIDE, "status": "pending", "groupId": ""},
         incoming={"ownerUid": OUTSIDE, "status": "assigned", "groupId": GROUP},
         name="an owner cannot place their own app into a group"),

    # --- reading other people's things ---------------------------------------------------
    case("DENY",  OUTSIDE, "get", "/groups/" + GROUP, stored=group_doc,
         name="a non-member cannot read a group"),
    case("ALLOW", MEMBER,  "get", "/groups/" + GROUP, stored=group_doc,
         name="a member can read their group"),
    case("DENY",  OUTSIDE, "get", "/proofs/x", stored=proof_doc,
         name="an unrelated tester cannot read a proof"),
    case("ALLOW", MEMBER,  "get", "/proofs/x", stored=proof_doc,
         name="the tester who posted a proof can read it"),

    # --- push tokens must not inherit the open read on the user document ------------------
    case("ALLOW", OUTSIDE, "get", "/users/" + MEMBER, stored={"email": "m@example.com"},
         name="any signed-in tester can read a user document (grids need names)"),
    case("DENY",  OUTSIDE, "get", "/users/" + MEMBER + "/tokens/install-1", stored=token_doc,
         name="but not the push tokens underneath it"),
    case("ALLOW", MEMBER,  "create", "/users/" + MEMBER + "/tokens/install-1",
         incoming=token_doc,
         name="a device can register its own token"),

    # --- admin decisions, delivered by the device that cares -------------------------------
    case("ALLOW", MEMBER,  "get", "/events/e1",
         stored={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": ADMIN},
         name="a tester reads a decision addressed to them"),
    case("DENY",  OUTSIDE, "get", "/events/e1",
         stored={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": ADMIN},
         name="but not one addressed to somebody else"),
    case("DENY",  MEMBER,  "create", "/events/e2",
         incoming={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": MEMBER},
         name="a tester cannot write themselves an approval"),
    case("ALLOW", ADMIN,   "create", "/events/e2",
         incoming={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": ADMIN},
         name="an admin can record a decision"),
    case("DENY",  ADMIN,   "create", "/events/e3",
         incoming={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": OUTSIDE},
         name="but not under somebody else's name"),
    case("DENY",  ADMIN,   "delete", "/events/e1",
         stored={"uid": MEMBER, "type": "assigned", "title": "x", "actorUid": ADMIN},
         name="and cannot erase one afterwards"),

    # --- the admin list ---------------------------------------------------------------------
    case("ALLOW", MEMBER,  "get", "/admins/" + MEMBER, stored={},
         name="an account can ask whether it is itself an admin"),
    case("DENY",  MEMBER,  "get", "/admins/" + ADMIN, stored={},
         name="but not whether anyone else is"),
    case("DENY",  ADMIN,   "create", "/admins/" + OUTSIDE, incoming={},
         name="an admin cannot grant admin (console only)"),

    # --- proof is append-only -------------------------------------------------------------
    case("DENY",  MEMBER,  "delete", "/proofs/x", stored=proof_doc,
         name="nobody deletes a proof"),
]

names = [c.pop("_name") for c in cases]

# get()/exists() inside a rule read real documents, and the test API has no database behind it.
# The membership lookup and the admin check are supplied here instead; the catch-all `exists`
# returning False is what makes every non-admin in the suite genuinely not an admin.
mocks = [
    {
        "function": "get",
        "args": [{"exactValue": DB + "/groups/" + GROUP}],
        "result": {"value": {"data": {"memberUids": [MEMBER]}}},
    },
    {
        "function": "exists",
        "args": [{"exactValue": DB + "/admins/" + ADMIN}],
        "result": {"value": True},
    },
    {"function": "exists", "args": [{"anyValue": {}}], "result": {"value": False}},
    {
        "function": "get",
        "args": [{"exactValue": DB + "/apps/" + APP}],
        "result": {"value": {"data": {"ownerUid": MEMBER}}},
    },
]

body = {
    "source": {"files": [{"name": "firestore.rules", "content": rules}]},
    "testSuite": {"testCases": [dict(c, functionMocks=mocks) for c in cases]},
}
print(json.dumps({"body": body, "names": names}))
PYEOF
)

echo "testing firestore.rules against $PROJECT"

out=$(printf '%s' "$built" \
    | "$PY" -c "import json,sys; sys.stdout.write(json.dumps(json.load(sys.stdin)['body']))" \
    | curl -sS "${CURL_OPTS[@]}" -X POST \
        "https://firebaserules.googleapis.com/v1/projects/$PROJECT:test" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json; charset=utf-8" \
        -H "x-goog-user-project: $PROJECT" \
        --data-binary @-)

names=$(printf '%s' "$built" \
    | "$PY" -c "import json,sys; sys.stdout.write(json.dumps(json.load(sys.stdin)['names']))")

# The checker goes in via -c, not a heredoc: a heredoc *is* stdin, so it would displace the API
# response being piped in and the script would read an empty document.
printf '%s' "$out" | "$PY" -c '
import json, sys

names = json.loads(sys.argv[1])
res = json.load(sys.stdin)

if "error" in res:
    print("API error:", res["error"].get("message", res["error"]))
    sys.exit(1)

for issue in res.get("issues", []):
    print("  rules issue:", issue.get("description"))

results = res.get("testResults", [])
if not results:
    print("no results returned"); sys.exit(1)

failed = 0
for name, r in zip(names, results):
    state = r.get("state", "?")
    if state != "SUCCESS":
        failed += 1
    print("  {}  {}".format("ok  " if state == "SUCCESS" else "FAIL", name))

print()
print("{} passed, {} failed".format(len(results) - failed, failed))
sys.exit(1 if failed else 0)
' "$names"
