# How the app and the backend talk

What was actually built, as of 20 September 2026.

The other files in this folder (`backend.md`, `crew.md`, `feedback.md`) are the plans, written
before any of it existed. They are worth keeping: they say what was decided and why, and the why
does not go stale. What did go stale was their opening lines, which claimed nothing was built
long after it was, so each now carries a banner pointing here. This file is the one that
describes what runs, and the last section says plainly what does not.

## The two halves

| | Repository | Visibility |
| --- | --- | --- |
| App | `almasumdev/testtrack` | **public** |
| Backend | `almasumdev/testtrack-backend` | private |

Because the app repository is public, nothing in it may name a secret, a host key or a server
path. The API's own address is public by definition (the app calls it) and is the only server
detail in the app repo, in `app/build.gradle.kts` as `BuildConfig.API_BASE`.

The old Firestore app still runs and is untouched by any of this. The same Firebase project is
still used, but only for sign-in and push. No new feature reads or writes Firestore.

## Signing in

Google is kept, because Drive and the testers-group check both need it. What changed is that the
API trusts its own token afterwards, so no ordinary request depends on Firebase being reachable.

```
Google Sign-In  ──►  Firebase ID token  ──►  POST /auth/session  ──►  access + refresh
   (in the app)          (RS256, Google's)      (verified against          (ours, HS256)
                                                 Google's JWKS)
```

- **Access token**: 15 minutes. Sent as `Authorization: Bearer …` on every request.
- **Refresh token**: 30 days. A random secret, not a JWT — there is nothing in it to read and
  nothing to forge, because it is only ever compared against a stored hash. `POST /auth/refresh`
  trades it for a new pair.
- A session is a row, so it can be revoked. That is what protects a session, rather than where
  the token happens to be stored.

In the app this is three files: `net/ApiAuth.kt` bridges Firebase to our session, `net/Api.kt`
puts the token on each request and retries once on a 401, `net/ApiSession.kt` keeps the pair in
`SharedPreferences`. Leaving Firebase later means changing `ApiAuth.kt` and nothing else.

## One request, end to end

```
CrewScreen (Compose)
   └─ CrewViewModel            holds state, decides nothing
        └─ Api.takeSeat()      net/Api.kt, OkHttp + kotlinx.serialization
             │  HTTPS
             ▼
        nginx  (Let's Encrypt certificate, testtrack.eazyverse.com)
             ▼
        FastAPI  app/api/v1/crews.py        parse, authorise, call a service, return a schema
             ▼
        app/services/crews.py               every rule lives here
             ▼
        app/repositories/seats.py           SQL only, no rules
             ▼
        Postgres 16
```

Four layers, and the arrows only point one way. `import-linter` fails the build if they do not —
the boundary is a fact rather than an intention. The reason is narrow and practical: a rule that
cannot be called without an HTTP request cannot be called from a scheduled job, a socket handler
or a test, and this product wants all three.

**The app decides nothing.** Which crew a seat goes into, whether a verdict counts, whose day it
is, what a review is worth: all of it is the server's answer, and the app redraws whatever comes
back. A client that works out for itself when a crew is full is a client somebody will edit.

## The endpoints

27 of them, all under `/api/v1`. `GET /health` is the only one that needs no session.

**Account** — `POST /auth/session`, `POST /auth/refresh`, `GET /me`, `GET /me/tix`,
`GET /me/profile`, `GET /me/apps`, `GET /board`

**Circle (Feedback)** — `GET|POST /feed/posts`, `GET /feed/posts/{id}`,
`POST /feed/posts/{id}/claim`, then the review it created:
`POST /reviews/{id}/installed` → `/used` → `/submit`, or `/abandon`.
The owner reads `GET /reviews/waiting` and answers `/approve` or `/reject`.
A rejection opens a dispute: `GET /disputes`, `POST /disputes/{id}/vote`.

**Crew** — `GET /crews` (the tab, with members), `GET /crews/{id}` (members only),
`POST /crews/seats` (checks the app on Play first), `DELETE /crews/seats/{id}`,
`POST /crews/seats/{id}/report`, `POST /crews/seats/{id}/visit`. Admins:
`POST /crews/admin/{id}/start`, `POST /crews/admin/seats/{id}/remove`, and `/play-console/*` for
the Play check's Google sign-in.

**Push** — `PUT /me/push-token`, `DELETE /me/push-token?token=…`.

The full schema is served live at `/api/v1/openapi.json`, and an instrumented test
(`ApiContractTest`) reads it from a device and fails if a field the Kotlin models expect has
gone.

## Errors

Every refusal has the same shape and a stable `code`. **The app branches on the code and never
parses the message**, so wording can change without breaking a client.

```json
{"error": {"code": "needs_green", "message": "Get one app in Crew 07 installed first…"}}
```

| code | status | means |
| --- | --- | --- |
| `unauthenticated` | 401 | no session, or it expired — the app refreshes once and retries |
| `not_allowed` | 403 | a rule about who you are |
| `not_found` | 404 | |
| `conflict` | 409 | a rule about what state something is in |
| `needs_green` | 409 | a red from somebody with nothing working yet; the app explains this one |
| `rate_limited` | 429 | per account, in Redis; reads are never limited |
| `insufficient_tix` | 400 | |

## Realtime

A socket at `/api/v1/ws?token=…`, authenticated *before* the upgrade completes. A client sends
one subscribe frame naming the channels it wants and receives events until it goes away.

Three channels plus one: `feed`, `crew:<id>`, `user:<id>`, and `admin` for the console.
Membership is checked server-side — a crew channel carries package names and the notes that get a
tester past a front door, which is exactly what `GET /crews/{id}` refuses to outsiders.

Every event carries `{ch, seq, type, data}`. **The sequence number is the whole correctness
story**: a client that sees 84 then 87 knows it missed two, refetches once, and carries on. That
is what stops a live screen going quietly wrong.

Publishing happens *inside* the writing transaction, so the number a change takes is ordered
against every other change on that channel. An event published after the commit can overtake one
published before it, which is how a client ends up applying yesterday's state over today's.

Push, separately, is how a phone hears about something while the app is closed. Services queue a
note with `app/services/notify.py`; it goes out over Firebase Cloud Messaging (HTTP v1, the
service account in `FCM_CREDENTIALS_B64`) only after the commit, data-only, to every token the
person has registered. Crew notes: the crew is full, a report on your app, an app sent back, a
missed day, out of the run, the evening reminder, the crew finished. Feedback notes: a review to
check, accepted, refused, settled, ran out; the app's half-hourly `FeedbackWatch` is kept as a
backstop and is told what push already said.

Events currently published:

```
post.created  post.changed   review.submitted  review.approved  review.disputed
seat.taken    seat.left      seat.mine         crew.changed
crew.filled   visit.recorded dispute.opened    dispute.changed  tix.changed
```

## Where the rules live

`app/core/rules.py` is the only place a number is written down. Both halves read from it and the
app mirrors it in `ui/CrewModels.kt` as `CrewRules`, which an endpoint test checks agrees.

```
REVIEWS_BOUGHT 3    USE_SECONDS 30   APPROVE_WINDOW_HOURS 48   JURORS 3
SEATS 15            RUN_DAYS 14      REPORTS_TO_RETURN 2       MISSES_IN_A_ROW 2
REMIND_HOUR 20      UNCHECKED_AFTER_HOURS 12
OPEN_POINTS 1       REVIEW_POINTS 5
```

Two rules are worth knowing because they shape the whole product:

**Days are per member.** A crew fills at one instant and starts fifteen times, each at the
member's own midnight in their own stored timezone. `app/services/days.py` is pure arithmetic
over an instant and a zone name, so the same answer serves a request, a sweep and a test. A
single global clock would put the day boundary at five in the afternoon for somebody at UTC-7.

**Identity is the device, not the account.** One review per app per handset, one seat per handset
per crew, and a reviewer's device may not be the owner's. The fingerprint is a salted SHA-256 of
`ANDROID_ID` computed in `data/Device.kt`. Two accounts on one phone is the ordinary cheat and it
is refused at the point of claiming rather than judged afterwards.

## How the app holds what comes back

The screens were designed against the types in `ui/CrewModels.kt`, which say what a screen is
about; the wire types in `net/Wire.kt` say what the database holds. `ui/CrewWire.kt` maps between
them. That keeps one change from becoming two, and it is why the crew screens moved from
in-memory sample data onto a real backend without a single screen changing.

The mapping is pure functions over values, which is why it is unit-tested without a device.

## What proves what

| Layer | Where | What it can catch |
| --- | --- | --- |
| Backend rules | `tests/services/` | every rule; the awkward cases are the point |
| Backend wire | `tests/api/` | status codes, what a response contains, who may read it |
| Schema drift | `tests/db/test_migrations.py` | the migrations and the models disagreeing |
| App logic | `app/src/test/` | what the app computes from an answer |
| App screens | `app/src/androidTest/…/ui/` | what is drawn, on a device |
| App ↔ API | `app/src/androidTest/…/net/` | the real client against the real server |

The last row is the only one that catches a renamed field or a changed refusal. It needs a signed
-in session, which a test cannot get from Google, so the session is minted out of band by
`scripts/seed_e2e.py` **in the backend repository** and handed in as instrumentation arguments.
`scripts/e2e.sh` seeds, runs and cleans up. Nothing about that path exists in the app or the API:
there is no test mode to leave switched on.

Counts today: 140 backend, 81 app unit, 73 app instrumented.

## Deployment

Push to `main` on the backend repo → GitHub Actions runs lint, format, types, layers, file length,
migration safety and the tests → on green, it deploys: reset the checkout, build, `alembic upgrade
head`, restart.

The VPS runs three containers (`api`, `db`, `redis`) behind nginx. The deploy checkout is reset
rather than pulled, because it is a deployment target and not a workspace: anything local on the
server is by definition not what was reviewed.

## What is not built

Honest list, so this file does not become another one that lies.

- **Realtime beyond Crew.** The Crew tab listens (`net/Live.kt`): it opens the socket while it is
  showing, asks for its crews' channels, and refetches a moment after anything arrives. Circle and
  the rest still refetch only on arrival and after each action.
- **The admin panel.** Admin actions (force-start a crew, take an app out, the Play check's Google
  sign-in) exist as admin endpoints; the panel that calls them is not built, and the app has no
  admin screens on purpose.
- **Login and setup.** Sign-in works, but the setup checklist behind it is still the Firestore-era
  flow built on Drive and an Apps Script endpoint. Being replaced.
- **The old half.** Roughly 7,900 lines of Firestore screens still ship: Home, Group, Dashboard,
  Setup, Submit, and the services under them. Largely untested, and the reason `data/` has almost
  no coverage.
- **43 architecture violations**, held flat by `scripts/check_app.py`. Mostly one fact: the view
  models still live in `ui/`.

### Recently closed

Worth naming, because each was a journey that could not complete:

- Crews could fill but never finish, and a member could never join a second one. The sweep now
  closes a crew after its last member's day 14, and only a crew still *filling* holds your next
  seat; any number of running crews is fine.
- Seats were confirmed by members' votes, which a quiet crew never cast. The server now checks
  the app's closed test on Play through a browser signed in as a testers-group account, and
  members report rather than vote.
- Missed days were counted and nothing happened. One gets a warning, two in a row take the app
  out of the run.
- The Play check's browser could not sign itself back in once Google ended its session; it never
  went to the sign-in page. It does now, and answers "verify it's you" with the account's own
  authenticator where Google offers it.

- A crew visit was recorded on a button press and opened nothing, so one tap credited a night.
  It now launches the app and measures real foreground time, and the server holds it to ten
  seconds.
- The proof dialog asked for a screenshot with nowhere to put one. There are uploads now, and a
  rule about who may look at them.
- `approve`, `reject` and `vote` had no caller, so every review approved itself on a timer. The
  Waiting tab is the way in.
- Leaving a crew, and editing your own name and picture.
