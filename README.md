# TestTrack

Daily proof tracking for Play Console **closed-testing swap groups**.

A new personal Play developer account needs 12 testers opted in for 14 continuous days before it
can apply for production access. Groups of developers form to test each other's apps, and the
coordination usually happens in a WhatsApp thread full of screenshots. TestTrack replaces that
thread: every member posts proof from the app, and each owner reviews their own 14-day grid.

## The daily loop

```
onboarding -> sign in -> setup checklist -> today's list
                                              |
                    tap an app --> it opens --> 8s --> proof captured --> back here
                                              |
                                    uploaded to your Drive
                                    recorded against day N
```

Confirm screen sharing **once**; every app that day is captured under that single grant. Each app
is opened deliberately by the tester — nothing is scripted, so the proof reflects a real visit.

Owners get a grid: every tester down one side, all 14 days across the top. Green posted, red
missed, grey still to come. Tapping a green cell shows the screenshot.

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

Two signals, neither costing the tester a visible permission:

- **`QUERY_ALL_PACKAGES`** — protectionLevel `normal`, granted silently at install. Gives
  installed yes/no, the installing package, and `firstInstallTime`, which is the
  **continuous-install streak, retroactively, in one call**. It survives app updates and resets on
  uninstall, which is exactly the semantics a 14-day streak needs.
- **MediaProjection** — one consent per round, held open while each app is opened, screenshotted
  and closed.

`PACKAGE_USAGE_STATS` was measured and deliberately skipped: it adds only "did they open it" and
costs every tester a manual Settings grant that reads as surveillance.

An accessibility service for auto-scrolling and auto-tapping was built and removed. It worked, but
it costs each tester a *"full control of your device"* grant that Android revokes on every
reinstall, for one extra page of proof.

---

---

## Data

Three flat collections. Rules are in [firestore.rules](firestore.rules) — reads are open across
the group because the grid needs other testers' proofs by design, and every write is locked to the
owning account.

```
users/{uid}                            email, displayName
apps/{packageName}                     ownerUid, name, startDate, status
proofs/{appId}__{testerUid}__{day}     fileId, imageUrl, capturedAt
```

The composite proof id is what keeps this simple: posting twice overwrites instead of duplicating,
and an owner's whole grid is one query rather than 12 × 14 reads.

**Approval is a human glance.** `status` is pinned to `pending` by the security rules, so a client
cannot approve itself; an admin flips it to `approved` in the Firebase console. Verifying a Play
track automatically needs developer-level authorization from every owner — a service account in
their Play Console, a linked Cloud project, or a sensitive scope requiring Google verification.
All three cost more than the glance does.

## Roadmap

- [x] Onboarding → Google sign-in → setup checklist
- [x] Server-verified group membership
- [x] Drive-hosted proof on the narrow `drive.file` scope
- [x] Firestore: users / apps / proofs, with rules
- [x] Daily capture: open, auto-screenshot, auto-return
- [x] The 14-day owner grid
- [ ] Admin approval in-app rather than via the console
- [ ] Reminders for testers who have not posted today
- [ ] Multi-group support

## Licence

MIT — see [LICENSE](LICENSE).
