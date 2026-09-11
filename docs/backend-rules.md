# Backend rules

The rules the backend is built under, agreed before a line of it was written. This file moves into
the backend repository as its `CLAUDE.md` when that repository exists.

Every rule here is either enforced by CI or it is not a rule. Anything that depends on somebody
remembering is written as a note, not as a rule.

## Layers

Four of them, and the arrows only point one way.

```
api/          HTTP only. Parse, authorise, call a service, return a schema.
services/     Every rule lives here. Never imports fastapi.
repositories/ SQL only. Never holds a rule.
db/           Models and migrations.

realtime/     Sockets and the publisher. Calls services, never api.
schemas/      Pydantic in and out. Imported by api and services, imports nothing.
```

| Layer | May import | Must never import |
| --- | --- | --- |
| `api/` | services, schemas, core | db, repositories |
| `services/` | repositories, schemas, models, core | fastapi, starlette |
| `repositories/` | db models, core | services, api |
| `realtime/` | services, schemas | api |

**Enforced by `import-linter` in CI.** A layer reaching past its neighbour fails the build, so the
boundary is a fact rather than an intention. The reason for the strictness is narrow and practical:
a rule that cannot be called without an HTTP request cannot be called from a scheduled job, a
socket handler or a test, and this product will want all three.

**Corollary:** `services/` returns values and raises domain errors. It does not raise
`HTTPException`. Translating a domain error into a status code is `api/`'s job and is done in one
place.

## Files

**500 lines maximum**, enforced by a lint rule. A file at 480 is not a problem to fix; a file at
501 does not merge.

Split by what a thing is, not to hit the number. `models/user.py`, `models/post.py`,
`models/tix.py` is a good split. `services/feed_part2.py` is not.

## Database

**Primary keys are UUIDv7.** Time-ordered, so they index almost as tightly as integers, and not
enumerable, so nobody can walk `/v1/posts/1,2,3` or count the users. The app can also mint one
before the server answers, which is what lets a post appear the instant it is written.

**Every timestamp is `timestamptz`, stored in UTC.** Never a naive datetime, anywhere, for any
reason. A member's local day is computed from their stored zone at read time, see docs/crew.md.

**Every foreign key gets an index.** No exceptions and no discussion; the one time it is skipped is
the one that goes unnoticed.

**Every query behind a list endpoint has its plan checked once**, when it is written, and the
expected plan recorded above it:

```sql
-- EXPLAIN: index scan on posts_created_idx, ~20 rows, no sort
```

A later change that turns it into a sequential scan then shows up in review rather than in
production. This is a comment, not a test, and it is honest about being one.

**Soft delete where a row is still referenced, hard delete where it is not.**

```
soft   users, posts, reviews      deleted_at timestamptz null
hard   sessions, channel_seq
never  tix_ledger
```

A deleted review still paid out, so the ledger has to keep making sense. Ledger rows are never
deleted and never edited; a mistake is reversed by writing another row.

Soft-deletable tables get a partial index `where deleted_at is null`, and every read goes through a
shared base query that applies the filter. A forgotten filter on a soft-deleted table is a privacy
bug, not a display bug, which is why it is not left to each query to remember.

**Migrations are append only.** A migration that has been applied anywhere is never edited. Every
migration has a working downgrade. Both are checked in CI.

**No rules in migrations.** A migration moves the schema. Backfills that depend on a rule are a
script that calls a service, so the rule stays in one place.

## Tests

**Every rule and every endpoint, before merge.**

```
tests/
  services/     the rules, no HTTP, no web layer
  api/          through the API, against a real throwaway Postgres
  db/           migrations up and down
```

Against a real Postgres rather than SQLite or mocks, because constraints, unique indexes and
migrations are exactly where the surprises live and none of them exist in a mock.

**The rules that must have a named test**, from day one:

- A post costs `reward * REVIEWS_BOUGHT` and the charge and the row happen in one transaction.
- A review pays the post's reward, read from the row, never from the request.
- One review per person per post, refused by the database, not by a check.
- A post stops paying once it has the reviews it bought.
- Every tix movement writes a ledger row in the same transaction as its cause.
- A balance equals the sum of its ledger. Tested directly.

No coverage threshold, deliberately. It rewards tests for trivia and says nothing about whether the
rules above are covered.

## CI blocks a push on

- `pytest`, all of it
- `mypy --strict`
- `ruff check` and `ruff format --check`
- File length over 500
- `import-linter` contracts
- Migration safety: nothing edited after being applied, every migration reversible
- Every route has a response model and a docstring

Everything machine-checkable is checked by the machine, so review is about design.

## The wire

**`/v1` from day one.** The old app lives alongside this for months and a version added after
clients ship cannot be added cleanly.

**One error shape**, everywhere:

```json
{ "error": { "code": "insufficient_tix", "message": "That costs 180 tix and you have 120.",
             "details": { "needed": 180, "have": 120 } } }
```

`code` is stable and for the app to branch on. `message` is for a person and may change. The app
never parses `message`.

**Every route declares a response model.** That is what makes the OpenAPI document true, and the
OpenAPI document is what the app is generated from.

**The Android client is generated from OpenAPI.** Data classes and the Retrofit interface both. The
app physically cannot drift from the server, which is the thing given up by not writing this in
Kotlin, bought back for the price of one build step. A renamed field breaks the app's build instead
of quietly deserialising null.

## Realtime

**Publishing is part of writing.** A service that changes something publishes the event in the same
transaction boundary. Not afterwards, not in the router, not in a background task.

An event nobody publishes is a screen that is quietly wrong until somebody refreshes, and that is
the failure mode of every live app that gets it wrong.

**Every event carries its channel, a per-channel sequence number, a type and the changed object.**
The sequence is what lets a client know it missed one. See docs/backend.md for the contract.

## Privilege

**Admin actions live on admin endpoints.** An ordinary endpoint never checks "or is an admin" and
skips an ownership test. A privileged path that looks like a normal path is how a bug becomes a
breach, and it makes the ordinary path impossible to reason about.

**Every admin write is logged** with the actor, the action, the row and the reason they typed. An
admin adjusting a balance writes a ledger row carrying that reason, exactly like any other
movement.

**Writes are rate limited per user** in Redis, on every endpoint that creates a row. Reads are not
limited.

## Configuration

Settings come from the environment through `pydantic-settings`, typed and validated at boot. A
missing or malformed setting stops the process at start rather than at the first request that needs
it.

`.env` is never committed. `.env.example` is, listing every key with a safe placeholder, and CI
fails if a key exists in settings but not in the example.

## Tooling

Python 3.13 · `uv` for dependencies · `ruff` for lint and format · `mypy` strict · `pytest` ·
`alembic` · `sqlalchemy` 2.x typed · `import-linter`.
