# TestTrack

Daily proof tracking for Play Console **closed-testing swap groups**.

A new personal Play developer account needs 12 testers opted in for 14 continuous days before it
can apply for production access. Groups of developers form to test each other's apps, and the
coordination usually happens in a WhatsApp thread full of screenshots. TestTrack replaces that
thread: every member posts proof from the app, and each owner reviews their own 14-day grid.

## Groups

Testing happens in **cohorts of 14**, one app per member.

Twelve is the number Google asks for, but it does not count an app's own developer — so a cohort of
twelve leaves every app one tester short. Fourteen members gives each app twelve or thirteen
testers with a slot of margin, and the run only starts once **thirteen** have been placed, because
below that Play Console is not counting either.

The run belongs to the group, not to the app. Every app in a cohort is on the same day, which is
what lets it be read as a cohort at all. Submitting an app registers it; an **admin approves it by
placing it in a group**, and those are the same act — there is no approved-but-unassigned state.
Being in a group follows from having an app in it, so "one different app per group" needs no rule
of its own.

If a member drops out mid-run the day count **keeps going**. Play Console will have reset its own,
so the group screen says so outright rather than letting a grid that reads "day nine" imply
otherwise.

## The daily loop

```
onboarding -> sign in -> setup (4 steps) -> home
                                             |
                        your groups  +  apps awaiting a group
                                             |
                        a group --> your app pinned on top,
                                    the other 13 below
                                             |
                          "Start testing · 9 left"
                                             |
        app 1 -> 36s -> app 2 -> 36s -> app 3 -> …  (no stop in between)
                                             |
                  screenshot at an unannounced moment in each
                                             |
              back here -> every proof uploaded to your Drive
```

Confirm screen sharing **once** and the round walks the whole group — each app opens, is captured,
and hands straight over to the next without coming back to a list to press the same button twelve
times. A single **Open** on any row runs a round of one, so both paths behave identically.

**A day needs 30 seconds of use, not a launch.** The screenshot lands at a random point inside the
visit, so it cannot be timed around: opening the app, waiting for the flash and leaving does not
work because there is no flash to wait for. Foreground time comes from `UsageStatsManager` and
covers the whole day, not just the visit TestTrack started.

The visit is held open for **36 seconds** to clear a 30-second bar. The clock starts when the
service schedules it, but usage only accrues once the app has actually reached the foreground a
second or two later — measured at exactly thirty, honest full-length visits came back at 28.7s and
29.4s and failed the very rule they had satisfied.

Owners get a dashboard per app: how many of the thirteen reported today with their screenshots and
times, the 14-day grid, who is still to report, and a per-tester breakdown.

---

## Setup

```bash
git clone https://github.com/<you>/testtrack.git
cd testtrack
cp local.properties.example local.properties   # then fill it in
./gradlew assembleDebug
```

**Nothing deployment-specific is committed.** `local.properties` is gitignored, and every value in
it can equally be supplied as an environment variable of the same name, which is what CI should do.
The build reads them and exposes them through `BuildConfig`; a missing key logs a warning at
configure time and the app says plainly that it is unconfigured rather than failing obscurely.

| Key | What it is |
|---|---|
| `TESTTRACK_WEB_CLIENT_ID` | OAuth 2.0 **Web** client ID. Not the Android one |
| `TESTTRACK_GATE_URL` | Deployed Apps Script web app that answers the membership check |
| `TESTTRACK_GROUP_URL` | Public URL of the testers Google Group |
| `TESTTRACK_KEYSTORE` and friends | Optional. Release signing; debug builds work without them |

The Web client ID must be **byte-identical** to the one the membership service checks the ID
token's `aud` claim against — a mismatch is rejected as `wrong_audience`.

### Something to look at

Groups and placements are admin-only in the rules and the admin app does not exist yet, so a fresh
install has nothing to show. [tools/seed.sh](tools/seed.sh) writes a cohort you can walk the whole
app against — a healthy mid-run group, one still forming, one a member short so the at-risk banner
shows, a fortnight of proof against your own app, and a submission awaiting placement.

```bash
export TESTTRACK_UID=<your firebase uid>      # from the users collection after signing in once
export TESTTRACK_PKGS="com.some.app com.other.app"   # installed on the test phone: real icons, working Open
tools/seed.sh up
tools/seed.sh down
```

It writes with a `gcloud` owner credential, bypassing the rules exactly as the admin app will.

---

## How membership verification works

Google publishes **no API** for reading members of a consumer `@googlegroups.com` group. The Admin
SDK Directory API and the Cloud Identity Groups API are both Workspace-only, and Play Console
accepts only `@googlegroups.com` groups. Those two facts have no overlap.

What does work is **Apps Script's `GroupsApp`**, which authorises on *permission* — being a group
owner or manager — rather than account edition:

```
Android app                    Apps Script (runs as group owner)         Google Groups
     │  Google ID token  ─────────────▶  verify via tokeninfo
     │                                   check aud == our client id
     │                                   GroupsApp.getUsers()  ──────────▶ member list
     │  ◀── { email, isMember } ───────  compare
```

Two properties make this worth the awkwardness:

- **Unforgeable** — the verdict is computed on Google's servers from a token the device cannot
  fake. The phone never asserts its own membership.
- **No roster leak** — the endpoint returns only `{email, isMember}` about the caller, so a
  decompiled APK reveals nothing about anyone else.

Gmail addresses are normalised on both sides — lowercased, dots stripped, `+suffix` removed —
because the group and the ID token can hold different strings for the same account.

---

## Proof of testing

Three signals:

- **`QUERY_ALL_PACKAGES`** — protectionLevel `normal`, granted silently at install. Gives
  installed yes/no, the installing package, and `firstInstallTime`, which is the
  **continuous-install streak, retroactively, in one call**. It survives app updates and resets on
  uninstall, which is exactly the semantics a 14-day streak needs.
- **MediaProjection** — one consent per round, held open while each app is opened, screenshotted
  and closed.
- **`PACKAGE_USAGE_STATS`** — real foreground time per app per day. The only thing here that costs
  the tester a trip to Settings, and it is mandatory: a screenshot proves the app opened, and
  nothing else proves anyone stayed.

Usage is summed from **raw `UsageEvents`, not `queryUsageStats`**. The daily buckets that method
returns are written periodically, so a visit that ended seconds ago still reads as zero — which is
precisely when the reading is taken. Events are exact and include the session in progress, which
matters because the figure is read on the way out of a visit while the app under test is still on
screen. The aggregate is consulted as well and the larger of the two wins, since some
manufacturers trim the event stream.

An accessibility service for auto-scrolling and auto-tapping was built and removed. It worked, but
it costs each tester a *"full control of your device"* grant that Android revokes on every
reinstall, for one extra page of proof.

---

## Data

Four flat collections. Rules are in [firestore.rules](firestore.rules) — reads are open across the
Google Group because grids need other testers' proofs by design, every write is locked to the
owning account, and anything deciding *who tests what* is locked to an admin.

```
users/{uid}                            email, displayName
users/{uid}/tokens/{installId}         token, platform, updatedAt — one row per device
admins/{uid}                           exists ⇒ admin. An empty document is the whole mechanism.
groups/{groupId}                       memberUids, appIds, startDate, status
apps/{packageName}                     ownerUid, name, groupId, status
proofs/{appId}__{testerUid}__{day}     fileId, imageUrl, capturedAt, usageMs
events/{eventId}                       uid, type, title, body, actorUid — an admin decision,
                                       read and raised by the device it names
```

Tokens sit in a subcollection rather than on the user document because rules do not cascade into
subcollections, and the user document is deliberately readable by every signed-in account so that
grids can turn a uid into a name. A push address should not inherit that.

The composite proof id is what keeps this simple: posting twice overwrites instead of duplicating,
and a whole grid is one query rather than 13 × 14 reads. Keying apps by package name gives the same
property one level up — re-submitting corrects the record rather than creating a second one, and a
correction merges, so fixing a typo on day nine cannot eject the app from its group. An owner may
withdraw their own app; its proofs are left in place, because letting clients delete those would
mean write access to other testers' rows.

**The admin app is a separate build** — [`../test_track_admin`](../test_track_admin) — and this one
holds no privileged code at all. `groups` is admin-only, and an owner's write to their own app must
leave `groupId` and `status` exactly as they were, so approval cannot be self-granted. Placing the
thirteenth app is what takes a group to threshold, so the same admin write sets `startDate`; no
client here and no Cloud Function is involved.

Being an admin is one empty document at `admins/{uid}`, written from the console. An account may
read **its own** row and nobody else's — enough for the admin app to say "this account is not an
administrator" instead of failing with an unexplained refusal, and not enough to enumerate anyone.

Verifying a Play track automatically would need developer-level authorization from every owner — a
service account in their Play Console, a linked Cloud project, or a sensitive scope requiring
Google verification. All three cost more than an admin's glance, and placement into a cohort is a
judgement call anyway.

The refusals are the half that is easy to believe and hard to check, so
[`tools/rules-test.sh`](tools/rules-test.sh) asserts them against the local rules before they are
deployed — a tester cannot create a group, cannot start their own clock, cannot place their own
app, cannot read a stranger's proof, and cannot read another device's push token.

## Reminders

A run is fourteen consecutive days, and one tester forgetting costs the other thirteen their clock.
Reminders are how that gets caught in time — and they are **raised on the phone, not sent to it**.

[`ReminderWorker`](app/src/main/java/com/eazyverse/testtrack/data/ReminderWorker.kt) reads the same
data the home screen reads, counts what is still owed today, and posts a local notification if
anything is. First run is at 7pm, because a nudge at eight in the morning is about a day that has
barely started. Nothing is scheduled server-side, so there is no cron to keep alive, no fleet of
device tokens to keep in step with who is still in which cohort, and no cost to a tester being
unreachable for a week.

**Admin decisions travel the same road.** When an app is placed or pulled back out, the console
writes an [`events`](firestore.rules) document addressed to that developer, and their device raises
it on the next pass — or within seconds, if the app is open. Written rather than pushed because the
console is a phone app: FCM's v1 API authenticates with a service account, and a service account
key inside an APK is a service account key in everybody's hands. The cost is latency, which is the
right trade for news measured in days. The document doubles as the audit trail — `actorUid` records
which admin acted.

That is the one query in the app needing a composite index; see
[firestore.indexes.json](firestore.indexes.json) for why.

FCM is still wired up — a token per device at `users/{uid}/tokens/{installId}`, a topic per group —
because it is the only path to instant delivery if a sender ever exists, and
[`tools/push.sh`](tools/push.sh) can drive it by hand today:

```sh
tools/push.sh group seed-group-a "5 apps still waiting" "Day 4 closes tonight."
```

Notifications are the fifth setup step and the only optional one: a tester who declines can still do
every part of the job, so setup finishes either way — but it will not finish without asking.

## Releases

Both apps are side-loaded — TestTrack holds `QUERY_ALL_PACKAGES`, which Play restricts — so a
signed APK on the releases page *is* the distribution channel.
[`.github/workflows/release.yml`](.github/workflows/release.yml) builds one on every push to `main`,
publishes it, and prunes so only the newest three survive. The signing key is fixed and its SHA-1 is
registered in Firebase, so updates install over the top and Google sign-in keeps working.

## Design

The interface guide is [docs/ui.md](docs/ui.md): flat surfaces and hairline dividers, insets and
status-bar appearance handled centrally, one primary action per screen, and how the grid encodes
state by shape as well as colour.

## Roadmap

- [x] Onboarding → Google sign-in → setup checklist
- [x] Server-verified group membership
- [x] Drive-hosted proof on the narrow `drive.file` scope
- [x] Firestore: users / groups / apps / proofs, with admin-gated rules
- [x] Daily capture: open, unannounced screenshot, auto-return
- [x] Real foreground time from `UsageStatsManager`
- [x] Cohorts of 14 with a shared 14-day run
- [x] Owner dashboard: today's reporters, the grid, who is behind
- [x] Push plumbing: per-device tokens, per-group topics, deep-linked taps
- [x] The admin app: review submissions, form groups, place apps, see the proof
- [x] On-device reminders and admin decisions — no server, no scheduler
- [x] Signed releases from CI, newest three kept

## Licence

MIT — see [LICENSE](LICENSE).
