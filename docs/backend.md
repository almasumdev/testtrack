# Backend

A backend of our own, on the VPS, for everything new. Decided September 2026. Nothing built yet.

The old app keeps running on Firestore and is not touched by any of this. The same Firebase project
stays in use for sign-in and push. What leaves Firebase is the data.

## Decisions

| | | Why |
| --- | --- | --- |
| **Language** | Python, FastAPI | Joins the Python already running on the VPS. Typed request and response models, free OpenAPI docs, and a language the owner maintains without help. |
| **Database** | PostgreSQL 16 | Real transactions, which seat-taking needs: two people cannot take the last seat. Timezone-aware timestamps, which this product needs badly. Matches the seven Postgres containers already on the box. |
| **Auth** | Google sign-in, exchanged once for our own JWT | Google has to stay: proofs go to the tester's own Drive and the testers-group check reads Google's roster. But the API trusts its own token after that, so requests do not depend on Firebase and dropping it later is one endpoint. |
| **Realtime** | WebSocket on every screen, Redis for fan-out | The app and the admin console both sync live. Redis because the moment there is more than one worker, two phones on one crew land on different workers and never hear each other. |
| **Event shape** | Full payload, with a sequence number | The event carries the change rather than a nudge to go and look. Fastest to feel and the cheapest to serve: a nudge means every watching phone fires a fresh query. The counter lets a client notice it missed one and catch up. |
| **Repository** | Its own, separate from the app | Backend and app deploy on different clocks and share nothing but an OpenAPI file. |
| **Deployment** | GitHub Actions on push to main | Build, test, ship, restart. One path every time, no manual step to forget. |
| **Admin console** | React and Vite, a static SPA | Admin work is reading tables and typing, which a browser does far better than a phone. Static files behind nginx, no extra container. |
| **Rate limiting** | Per user, on writes, through Redis | Reads are free. Posting and reviewing are capped per user per hour. Redis is already there for the socket. |

Realtime is the requirement that shapes everything else, and it has a cost worth naming: **every
write has to publish an event, and an event nobody publishes is a screen that is quietly wrong
until somebody refreshes.** Publishing is part of writing, not a thing added afterwards.

## The machine, as found

`myvps` · 51.195.41.252 · Ubuntu 25.04 · 4 cores · 7.6 GB RAM · 72 GB disk.

- **3.9 GB of RAM in use**, 3.7 free. Our three containers want roughly 300 MB, so there is room.
- **CPU is nearly idle.** The busiest container sits at 4%.
- **Disk is at 80% and most of it is waste**: 40 GB of Docker build cache with 29 GB reclaimable,
  and images reporting 99% reclaimable. `docker builder prune` before anything else.
- 25 containers across nine projects, including a mail server and an MQTT broker. Seven Postgres
  instances, one per project.

**The house pattern is one stack per project**, fronted by nginx: an app container on a localhost
port, its own Postgres, its own nginx site conf. TestTrack follows it rather than sharing anything.
Sharing a database with another project is how an unrelated migration takes this one down.

## Shape

```
nginx :443  testtrack.eazyverse.com
   /api   ->  127.0.0.1:4400   testtrack-api      FastAPI, uvicorn
   /api/v1/ws                                     WebSocket upgrade
   /admin ->  static files      testtrack-admin    React, built by CI

docker compose
   testtrack-api        depends on db, redis
   testtrack-postgres   postgres:16-alpine, own volume
   testtrack-redis      redis:7-alpine, own instance
```

Its own Redis rather than the MarketPulse one. It is 20 MB, and a shared broker means one project's
flush is another project's outage.

```
backend/
  app/
    main.py           app, middleware, lifespan
    api/              routers, one per surface
    core/             config, security, deps
    db/               engine, session, models
    schemas/          Pydantic in and out
    services/         the rules, no HTTP in here
    realtime/         hub, channels, publisher
  alembic/            migrations
  docker-compose.yml
  Dockerfile
```

The rules live in `services/` and know nothing about HTTP or sockets. That is what makes the same
rule reachable from a REST call, a socket message and a scheduled job without being written three
times.

## The admin console

A browser console at `testtrack.eazyverse.com/admin`, replacing the Android admin app. That app's
main job was placing submitted apps into groups, and crews that form themselves delete that job
outright. What is left is refereeing.

**An admin can do anything a situation needs.** Force-start a crew that will never fill, remove an
app or a member, post a Notice to Circle, correct a balance. The list is not meant to be closed,
because the cases that need a human are by definition the ones nobody predicted.

What makes that safe is not a short list of powers but two rules:

- **Every admin write goes through a named admin endpoint**, never by bypassing an ownership check
  on an ordinary one. A privileged path that looks like a normal path is how a bug becomes a
  breach.
- **Every admin write is logged**: who, what, which row, and the reason they typed. Adjusting tix
  writes a ledger row with that reason attached, like every other movement.

The console subscribes to the same socket on the `admin` channel, so it is live without polling.

## Auth

```
ONCE, at sign-in
  app     Google sign-in through Firebase, as it does today
  app  -> POST /auth/session { idToken }
  api     verifies the token against Google's public keys
  api     upserts users row keyed by the Firebase uid
  api  -> { accessToken (15 min), refreshToken (30 days) }

EVERY request after
  Authorization: Bearer <our access token>
  no call to Firebase

WEBSOCKET
  connect with the same access token
  refused before the upgrade completes, never after
```

The refresh token is stored server side so a session can be revoked, which a Firebase token cannot
be. Device fingerprint (`data/Device.kt`) is recorded on the session, which is where the one device
one seat rule will read from.

## Realtime contract

**Channels** are named after the thing they are about, and a client subscribes only to what is on
screen.

```
feed              posts and reviews, global
crew:{id}         seats, verification, fill, start
user:{id}         your tix, your seat verdicts, your removals
admin             everything, for the console
```

**Every event** carries the channel, a per-channel sequence number, a type and the changed object.

```json
{ "ch": "crew:15", "seq": 84, "type": "seat.confirmed",
  "data": { "id": "...", "appName": "...", "owner": "...", "state": "confirmed" } }
```

**The client keeps the last seq per channel.** If the next one is what it expected, it applies it
with no round trip. If there is a gap, it fetches that resource once over REST and resumes. On
reconnect it sends its last seq and the server either replays the few it missed or tells it to
resync. That is the whole correctness story, and it is why screens cannot go quietly wrong.

**Background is push, not socket.** A socket is only alive while the app is. Anything that matters
while the app is shut goes out as an FCM data message and the app refetches when it opens. The push
plumbing already exists.

## The first slice: Feedback

Circle's Feedback tab, end to end, with tix real and enforced on the server.

### Tables

```
users            id, firebase_uid, email, display_name, tix, created_at
sessions         id, user_id, refresh_hash, device_fp, expires_at
posts            id, author_id, app_name, ask, reward, reviews_bought,
                 reviews_count, status, created_at
reviews          id, post_id, author_id, body, created_at
                 unique (post_id, author_id)
tix_ledger       id, user_id, delta, reason, ref_id, created_at
channel_seq      channel, seq
```

`tix_ledger` rather than a column that goes up and down. A balance you cannot explain is a balance
nobody trusts, and the first argument about a missing twenty is unanswerable without it.
`users.tix` is a cached total, and the ledger is the truth.

### Endpoints

```
POST   /auth/session          exchange a Google token for ours
POST   /auth/refresh

GET    /feed/posts            page of open posts
POST   /feed/posts            create, charges reward * 3
GET    /feed/posts/{id}
POST   /feed/posts/{id}/reviews   write one, pays the reward

GET    /me                    profile and balance
GET    /me/tix                the ledger, paged

WS     /ws                    subscribe to feed, user:{me}
```

### Rules that belong on the server, not the phone

- **The price of a post** is `reward * 3`, charged in the same transaction that creates the post.
  A client that sends its own price is ignored.
- **The reward for a review** is the post's, read from the row, not from the request.
- **A post stops paying** once it has the reviews it bought.
- **Nothing is refunded.** A post that buys three reviews and gets one keeps none of the
  difference. You paid to be listed, not for an answer, and a refund rule is a thing to argue
  about later rather than now.
- **Every tix movement writes a ledger row** in the same transaction as the thing that caused it.
- **Writes are rate limited per user**, in Redis, on the endpoints that create rows.

**Still undecided, and deliberately not designed here: what a review actually is.** The shape of
feedback is being rethought, so the `reviews` table above is a placeholder for whatever that turns
out to be. Nothing else in this document depends on it, which is why the foundation can be built
first.

### Events

```
feed        post.created      post.closed      review.added
user:{id}   tix.changed       review.accepted
```

## Local development, and testing on the phone

The backend runs on the Windows machine, the Samsung talks to it over the LAN.

```
docker compose up          on the laptop
                           api on 0.0.0.0:4400

phone      http://<laptop LAN ip>:4400
```

Two things this needs on the Android side:

- **A base URL per build type.** Debug points at the LAN address, release at
  `https://api.eazyverse.com`. A `BuildConfig` field, not a constant somebody has to remember to
  change back.
- **Cleartext for that one address.** Android blocks plain HTTP by default, so the debug manifest
  needs a network security config permitting the LAN subnet. Debug only, never release.

Both machines have to be on the same network, and Windows Firewall has to allow inbound 4400.

## Not in this slice

- Crews. The data model above has no crews in it on purpose: Feedback is the smaller half and it
  proves the auth, the socket and the ledger before anything harder is built on them.
- The admin console. It subscribes to the same socket and calls the same API, but it is a separate
  piece of work and still speaks Firestore today.
- Any migration of existing Firestore data. Nothing here reads or writes the old project.
