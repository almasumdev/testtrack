# Crew chat plan

Owner decisions 2026-11-19. Planner (Opus) read the backend (feat/dev-all, latest migration 0036), the app (main) and the admin panel.

## Fixed by the owner
1. One chat per crew. Anyone holding a seat (lobby included) can post; open through the run; read only after the crew ends; deleted with the crew prune 3 days after the end.
2. Text, replies, mentions of members and of crew apps. No links of any kind: strict server rejection (incl. "example . com", hxxp); the app blocks them too.
3. Pictures: 3 per user per day, each kept 1 day, then shown as expired. Existing upload pipeline (PUT /uploads/{uuid}, WebP redraw, 507 disk guard) with a new kind.
4. 10 messages per user per day. Every limit comes from the server; the app shows what is left.
5. FCM push per message with grouping; server-side mute per crew; a mention breaks through mute.
6. No report feature for now.
7. Own delete hides it for everyone; the row is kept; admin sees all with a deleted mark and time; admin can delete any.
8. A bot posts a small set of system lines; admin posts from the panel appear as the bot. Bot name and avatar from server config.
9. Small VPS: no websockets, no new services; poll with a cursor only while the chat is open; small indexed tables; efficient pushes.
10. Unread counts on the crew tile and the Crew tab.

## Approach
- Three small tables. Each chat row has a change counter `rev`. Every insert, delete and picture expiry takes the next `rev` under a row lock on the chat, so commit order equals `rev` order and a `since=rev` poll never misses a row. A message keeps its insert number as `seq` (display order) and its latest change as `rev` (cursor). Deletes reach pollers as tombstones.
- Limits live in server settings (env), are enforced inside the post transaction and are sent to the app in every chat response.
- Push: one outbox row per message, delivered after a short delay; the newest message of a burst sends one summary per recipient.
- Bot lines come from one sweeper stage that compares each crew with a small "said" memory on its chat row.
- Rejected: posting bot lines from every write site (take_seat, withdraw, reports, votes, sweep, admin seats, appeals, transfers, cancel). That is 10+ call sites, `services/crews.py` is at the 500-line cap, and any new path would silently miss its line. The sweeper diff costs at most one sweep (5 min) of delay, fine for system lines.
- Rejected: carrying chat over the existing Live socket (owner rule: polling only).

## Data model: migration `0037_crew_chat`
`alembic/versions/0037_crew_chat.py`, down_revision `0036_crew_prune_failures`, full downgrade. Models in `app/db/models/crew_chat.py`.

`crew_chats` (class `CrewChat`, one per crew, made on first need)
- `crew_id` uuid PK, FK crews ON DELETE CASCADE
- `rev` bigint not null default 0: last change number
- `said` jsonb not null default '{}': bot memory `{"started": bool, "night": int, "last": int, "ended": bool, "seats": {"<seat_id>": "in"|"out"}}`
- created_at, updated_at (Timestamped)

`crew_chat_messages` (class `ChatMessage`)
- `id` uuid PK (minted by the phone for member posts, `new_id()` for bot and staff)
- `crew_id` uuid FK crews CASCADE, not null
- `seq` bigint not null; `rev` bigint not null
- `kind` varchar(8): `user` | `bot` | `staff`
- `author_id` uuid FK users RESTRICT, null (null for bot and staff)
- `author_seat_id` uuid FK seats SET NULL, null: the seat they spoke as (app name)
- `staff_id` uuid FK users RESTRICT, null: who posted a staff line
- `text` varchar(3000) not null default '': mention tokens kept inline
- `event` varchar(24) not null default '': bot line key
- `mentions` uuid[] not null default '{}': user ids mentioned (members and app owners), resolved at post
- `reply_to_id` uuid FK crew_chat_messages SET NULL, null
- `picture_id` uuid FK uploads SET NULL, null; `had_picture` bool default false (`had_picture and picture_id is null` = expired or removed); `picture_gone_at` timestamptz null
- `deleted_at` timestamptz null; `deleted_by` varchar(8) default '' (`author` | `staff` | `erased`); `deleted_staff_id` uuid FK users null
- created_at, updated_at
- Indexes: unique `(crew_id, seq)`; unique `(crew_id, rev)`; `ix_chat_author_day (crew_id, author_id, created_at)` for the daily count; partial `ix_chat_pictures_live (created_at) where picture_id is not null`; FK indexes (house rule) on `author_id`, `author_seat_id`, `staff_id`, `deleted_staff_id`, `reply_to_id`, `picture_id`, the mostly-null ones partial `where <col> is not null`.
- Deleted rows stay visible to members as tombstones (no text, picture, mentions or reply). The blanking happens in exactly one function, `chat_views.message_out`, used by every member read. Admin reads use their own view.

`crew_chat_members` (class `ChatMember`)
- PK `(crew_id, user_id)`; crew FK CASCADE, user FK RESTRICT
- `read_rev` bigint default 0; `muted` bool default false
- `last_pushed_seq` bigint default 0; `last_push_at` timestamptz null
- `paused_at` timestamptz null; `paused_by` uuid FK users null (admin pause)
- Index `ix_chat_members_user (user_id)`; partial index on `paused_by`.

`push_tokens.chat` bool not null server_default false: this phone's build can show chat pushes (old builds never get them).

Sizes: a message row with its indexes is about 400 B. Worst case per crew 15 members x 10 a day x ~25 days (lobby + run + 3) = 3,750 rows, about 1.5 MB; a normal crew far less. Ten crews at once stay under 15 MB, gone at prune. Pictures: at most 15 x 3 live per crew (1 day each) x ~150 KB = about 7 MB per crew, rolling. Outbox: one row per message, already pruned after 7 days.

## Limits config
`app/core/config.py` Settings, plus `.env.example` and `.env.dev.example` (CI checks the example):

| Setting | Default | Meaning |
|---|---|---|
| `chat_enabled` | false | off: chat endpoints 404 `chat_off`, crews say `chat_state: "off"` |
| `chat_messages_per_day` | 10 | per member per crew per local day (the seat's timezone) |
| `chat_pictures_per_day` | 3 | same window; a picture message also counts as a message |
| `chat_picture_hours` | 24 | picture lifetime |
| `chat_max_length` | 500 | visible characters (code points; a mention counts as 1) |
| `chat_max_mentions` | 5 | per message |
| `chat_poll_seconds` | 5 | app poll while messages flow |
| `chat_poll_max_seconds` | 30 | app poll ceiling when quiet |
| `chat_push_delay_seconds` | 20 | outbox delay that groups a burst |
| `chat_push_max_wait_seconds` | 300 | longest a busy chat holds a member's push |
| `chat_bot_name` | `Crew Bot` | placeholder, the owner names it |
| `chat_bot_avatar_url` | empty | empty: the app draws its own robot mark |

Changing a value is an `.env` edit and an api restart; the app reads them from each response. Deleted messages still count toward the day. Bot and staff lines count for nobody.

## Endpoints (member)
New router `app/api/v1/crew_chat.py` (prefix `/crews`, tag `chat`), schemas `app/schemas/chat.py`. All need a session; `Cache-Control: no-store` on reads.

`GET /crews/{crew_id}/chat?since=<rev>&before=<seq>&limit=<n>` returns `ChatPageOut`:
```
{ "state": "open"|"read_only"|"gone",       // gone = pruned, list empty
  "can_post": bool, "why_not": ""|"ended"|"out"|"paused",
  "rev": 812, "reset": false, "more_before": true, "more_after": false,
  "muted": false, "read_rev": 800,
  "left":   {"messages": 7, "pictures": 2, "resets_at": "2026-11-20T00:00:00+06:00"},
  "limits": {"messages_per_day": 10, "pictures_per_day": 3, "picture_hours": 24,
             "max_length": 500, "max_mentions": 5, "poll_seconds": 5, "poll_max_seconds": 30},
  "bot": {"name": "Crew Bot", "avatar_url": ""},
  "people": [ {"seat_id", "name", "photo_url", "app_name", "app_icon", "package_name",
               "in_crew": bool, "mine": bool} ],
  "messages": [ ChatMessageOut ] }
ChatMessageOut = { "id", "seq", "rev", "kind": "user"|"bot",   // staff is shown as "bot"
  "author_seat_id": uuid|null, "mine": bool, "text", "event",
  "mentions": [{"kind": "member"|"app", "seat_id", "label"}],
  "reply_to": {"id", "seq", "author_seat_id", "excerpt", "picture": bool, "deleted": bool} | null,
  "picture": {"id", "width", "height", "state": "live"|"expired"} | null,
  "deleted": bool, "created_at" }
```
- `since=0`, no `before`: newest `limit` (default 50, max 100) by seq, plus `more_before`.
- `since>0`: rows with `rev > since` ordered by rev, at most 200; `more_after` when cut, and then `rev` is the last one returned. If the chat row is missing or `since > chat.rev`: `reset: true` plus the newest page.
- `before=<seq>`: an older page.
- `people`: every seat in the crew (live or removed) plus any author seat on the page that has left; sent on `since=0`, on `before` pages and on any poll that returns messages; `[]` on an empty poll.
- Reader: owner of a seat in the crew that is live (checking, confirmed) or removed (the `seat_repo.in_crew` set). Others 403. GET never creates rows.
- Fast path: `since >= chat.rev` answers without reading messages.

`POST /crews/{crew_id}/chat/messages`, body `ChatPostIn {id: uuid, text: str (max 3000 raw), reply_to: uuid|null, picture_id: uuid|null}`, returns `ChatSentOut {message, left, rev}` (201; 200 on a replay).
Refusals: 404 `chat_off`; 403 `not_allowed`; 409 `chat_closed` (crew done or app out, `why_not` in details); 410 `chat_gone` (pruned); 403 `chat_paused`; 422 `link_not_allowed`; 422 `validation_error` (empty, too long with `details.max`, bad mention, too many mentions); 429 `chat_limit` (`details: {kind: "messages"|"pictures", left: 0, resets_at}`); 409 `conflict` (id used by someone else).

`DELETE /crews/{crew_id}/chat/messages/{message_id}` returns the tombstone `ChatMessageOut`. Own `user` messages only, until prune, also in a read-only chat. Twice is fine. Its picture is discarded at once.

`PUT /crews/{crew_id}/chat/mute`, body `{muted: bool}`, returns `{muted}`.

`POST /crews/{crew_id}/chat/read`, body `{rev: int}`, returns `{read_rev}`: `read_rev = max(read_rev, min(rev, chat.rev))`. Not rate limited (it updates one row per member).

Posting is not Redis rate limited: the daily count under the chat lock is the limit (owner: no per-second limit). A replay of the same id never counts twice.

Pictures: `PUT /uploads/{uuid}?kind=chat&seat_id=<my seat>` (existing route; its `Literal` gains `chat`). Stored at `crew/<crew_id>/<id>.webp` like other crew pictures, so the prune folder removal and the orphan walk already cover it. Refused before decoding when the picture allowance is used (429 `chat_limit`) or the member cannot post. `may_see` for `chat`: anyone who may read the crew chat, while the message is not deleted and the picture is live.

Unread (no new endpoint):
- `GET /crews` and `GET /crews/{id}`: `CrewDetailOut` gains `chat_state` ("off"|"open"|"read_only"|"gone"), `chat_unread`, `chat_muted`.
- `GET /me/actions`: `parts.crew_chat` = unread over the member's unmuted crews. `crew` stays work-only; the app adds the two for the dock.
- Unread = messages with `seq > read_rev`, kind user or staff, not deleted, not mine. Bot lines never count. A member with no row yet has `read_rev 0`.

## Link filter
`app/services/chat_links.py`: `def blocked(text: str, *, allow: Collection[str] = ()) -> bool`. Run on every member and staff post, on the text with mention tokens replaced by a space. `allow` = this crew's package names (from its seats), removed first, so "com.crew.app" of a crew app is fine.

Normalise, in order:
1. Remove the allowed package names (case-insensitive, whole token).
2. NFKC, then casefold (folds full-width text).
3. Drop format characters (Unicode category Cf: zero-width, soft hyphen, BOM).
4. Map dot look-alikes to ".": U+3002, U+FF61, U+FF0E, U+FE52, U+2024.
5. Bracketed or spelled dots to ".": `[.]` `(.)` `{.}` `<.>`, `(dot)` `[dot]`, and ` dot ` / ` d0t ` between two words.
6. Join spaced single characters: a run of 3+ single letters or digits separated by single spaces becomes one word ("e x a m p l e" to "example", "h t t p s" to "https").
7. A space before a dot closes up: `word .com` and `word . com` become `word.com`. A dot followed by spaces is left alone (sentences); see R4.

Reject when any of:
- R1 scheme: `\b(https?|hxxps?|h\*\*ps?|ftp|mailto|intent|market|tg|whatsapp|javascript|sms|tel)\s*:`, or any `[a-z][a-z0-9+.-]{1,15}\s*:\s*/\s*/`.
- R2 `\bwww\b`.
- R3 domain: `(?<![\w@-])(?:[^\W_](?:[\w-]{0,61}[^\W_])?\.)+([^\W\d_]{2,24}|xn--[a-z0-9-]{1,59})(?![\w-])` where the last label is in the TLD set.
- R4 dot and spaces before a strong link TLD: `[^\W_]{2,}\.\s+(com|org|io|xyz|ru|cn|ly|gg|tk|ml|cf|ga|gq|info|biz|pk|bd)\b` (catches "example. com" without refusing "Thanks. Me too").
- R5 IPv4: four dot-separated numbers 0 to 255.

TLD set: `app/services/data/tlds.txt` (IANA list, lowercase, plus the Unicode form of each `xn--` TLD), loaded once into a frozenset. `scripts/update_tlds.py` regenerates it from a saved IANA file (no network in CI).

Known cost: real words that are TLDs right after a dot with no space are refused ("main.py", "notes.md", "build.sh", any package name not in this crew). The error says why. Package names of the crew's own apps are allowed.

Fixtures `tests/fixtures/chat_link_cases.json` (`{"blocked": [...], "allowed": [...], "allow_packages": [...]}`), copied into the app. At least:
- blocked: `example.com`, `EXAMPLE.COM`, `example . com`, `example .com`, `example. com`, `example dot com`, `example(dot)com`, `example[.]com`, `example。com`, full-width `ｅｘａｍｐｌｅ．ｃｏｍ`, `e x a m p l e . c o m`, `zero<U+200B>width.com`, `hxxp://x`, `https://x`, `h t t p s : / / x`, `ftp://x`, `market://details?id=a.b`, `intent://x`, `www example`, `bit.ly/abc`, `t.me/group`, `wa.me/123`, `mail me at a@b.com`, `192.168.0.1`, `play.google.com/store/apps`, `see mysite.xyz now`, `mysite.app`, `foo.co.uk`, `sub.domain.example.org`, `com.other.app` (not a crew package), `пример.рф`.
- allowed: `e.g. this`, `i.e. that`, `Version 1.2.3`, `Android 14.1`, `at 9.30 tonight`, `Thanks. Me too`, `Done. Net result is fine`, `5.5 MB`, `4.5 stars`, `Mr. Smith`, `U.S. users`, `ok...so`, `Settings > Apps`, `file.kt`, `com.crew.app` (in allow_packages), mention tokens, emoji text, an empty string.
- A timing test: 3,000 characters of adversarial input finishes under 50 ms (no catastrophic backtracking).

## Push
`app/services/chat_push.py`:
- `enqueue(db, msg, *, now)`: in the post transaction, add `Outbox(kind="chat", payload={"message_id": ...}, available_at=now + chat_push_delay_seconds)`. User and staff messages only; bot lines never push.
- `deliver(db, payload, sender, *, now)` (outbox worker):
  1. Message gone or deleted, or crew done or pruned: nothing.
  2. Recipients: owners of live seats in the crew, minus the author.
  3. `newer` = another user or staff message in the crew with a higher seq, created within `chat_push_delay_seconds` after this one.
  4. Per recipient (a missing member row means read_rev 0, not muted, never pushed):
     - skip if `read_rev >= msg.seq` (already read, chat open);
     - `waiting` = others' unread messages with `seq > max(read_rev, last_pushed_seq)`; skip if none;
     - `mentioned` = any of them has the recipient in `mentions`; `staff` = any of them is a staff line;
     - skip if muted and not mentioned and not staff;
     - skip if `newer` and the oldest waiting message is younger than `chat_push_max_wait_seconds` (the newer row carries the summary);
     - send, then `last_pushed_seq = msg.seq`, `last_push_at = now`.
  5. Only tokens with `push_tokens.chat = true`.
- Data payload (data-only, like every push): `kind=chat`, `ref=<crew_id>`, `event=<seq>`, `count`, `mention` ("1"/"0"), `title`, `body`.
  - one waiting: title `Crew 14`, body `Alex: <first 100 visible chars>`; picture only: `Alex sent a picture`;
  - several: body `5 new messages. Alex: <excerpt>`;
  - mentioned: title `Alex mentioned you in Crew 14`;
  - a staff line shows the bot name as author.
- FCM: `android.collapse_key = "chat_<crew_id>"`, `android.ttl = "86400s"`, priority high for a mention or staff line, normal otherwise. HTTP v1 has no multicast (the old batch endpoint is gone), so "batch per message" is one outbox row per message, fanned out over one HTTP client with at most 8 sends in flight. Volume is tiny next to FCM limits (at most one push per member per crew per 20 s, 15 members).
- `app/realtime/push.py`: add `Fcm.send_each(messages: list[tuple[str, dict[str, str]]], *, android: dict[str, str]) -> list[str]` (returns dead tokens). Unlike `send`, a refused token is logged and skipped, not raised, so a chat row is never retried into duplicate notifications; only an auth failure raises. `send` stays as it is.
- `PUT /me/push-token` body gains `chat: bool = False`; `push_tokens.keep` stores it; `push_tokens.of_user(..., chat_only=True)`.

Notifications off: the server cannot know and still sends (cheap). The app draws nothing when the permission or the channel is off but still nudges its badges. No token or an old build: no push; the tile and tab show the count on the next load. Muted: no push unless mentioned or a staff line; the tile still shows the count in a muted style; the tab leaves muted crews out.

## Bot
`app/services/chat_said.py` (built with the core): the `said` shape and `baseline(crew, seats, now) -> dict` = what is true now, recorded as already said, so a crew gets no burst of old news when its chat row is first made.

`app/services/chat_bot.py`: `async def sweep(db, *, now) -> dict[str, int]`. For each crew with `pruned_at is null` (skip DONE crews whose `said.ended` is set): hold the crew FOR SHARE, ensure and lock its chat, read all its seats, compare with `said`, post lines through `crew_chat.append(kind="bot")`, save `said`. Order inside a crew: left, joined, started, night lines, ended.

| event | when | text |
|---|---|---|
| `joined` | a seat becomes confirmed (lobby) | `{App} joined. {n} of {seats} apps are in.` |
| `joined` | confirmed during the run (team placed or put back) | `{App} joined the run.` |
| `left` | a seat said "in" is now withdrawn, rejected or removed | lobby: `{App} left the crew.` run: `{App} is out of the run.` |
| `started` | `filled_at` set | `{Crew} is full. Night 1 starts at midnight, your own time.` |
| `night` | the first member reaches night 1 | `Night 1 has started. Open every app in your list once a day.` |
| `halfway` | first member reaches night `(run_days + 1) // 2` | `Halfway there: night {n} of {run_days}.` |
| `last_night` | first member reaches night `run_days` (again if the team extends after) | `Last night of the run. Open every app one more time.` |
| `ended` | state DONE, not cancelled | `The run is over. This chat is now read only.` plus ` It will be deleted in 3 days.` when prune is on |
| `cancelled` | state DONE with cancelled_at | `The team stopped this crew. This chat is now read only.` |

Edges: a seat that went from checking to rejected was never "in": no line, recorded silently. A crew sent back to filling (`filled_at` cleared) resets `started` silently. An app moved to a new owner in the same pass (out and in with the same `app_id`) posts nothing. "First member" = the highest `days.day_of` over live seats.

## Cleanup and prune
`app/services/chat_cleanup.py`:
- `expire_pictures(db, *, now) -> int`: messages with a live picture older than `chat_picture_hours`: hold crew FOR SHARE, lock chat, lock upload, `uploads.discard(upload_id, subject_id=message.id)` (file after commit), set `picture_id = null`, `picture_gone_at = now`, bump `rev`.
- `drop_unclaimed(db, *, now) -> int`: `kind='chat'` uploads with no subject older than `chat_picture_hours`: row now, file after commit.
- `services/crew_prune.prune`: after `delete_uploads`, call `chat_cleanup_repo.delete_crew(db, crew.id)` (messages, members, chat row); add `chat: int` to `Pruned` and to the log line. Pictures and the folder are already covered.
- Post after prune: the post holds the crew FOR SHARE first and refuses a pruned crew with 410 `chat_gone`, so it cannot recreate a chat row behind a prune. Lock order everywhere: crew, then chat, then upload.
- `services/admin_member_delete.erase`: the member's messages get `text = ''`, `mentions = '{}'`, `deleted_at = now`, `deleted_by = 'erased'`, chat locked and `rev` bumped per crew; add `"chat_messages": n` to `erased`.
- `scripts/prune_report.py`: count chat messages per due crew.
- Chat deletion follows `PRUNE_ENABLED`: a stack with prune off keeps finished chats, read only.

## Admin
Backend: `app/services/admin_chat.py`, `app/repositories/admin_chat.py`, `app/schemas/admin_chat.py`, `app/api/v1/admin_chat.py` (prefix `/admin`); write the contract into `docs/admin-console.md` first.
- `GET /admin/crews/{crew_id}/chat?cursor=<seq>&limit=` (maintainer) returns `{items: [AdminChatItem], next: str|null, crew: {id, name, state}, bot: {name, avatar_url}, paused: [MemberRef]}`, newest first, deleted rows included with their text. `AdminChatItem {id, seq, kind: user|bot|staff, author: MemberRef|null, app_name, staff: MemberRef|null, text, mentions: [{kind, seat_id, label}], reply_to_id, picture: {id, state: live|expired|removed, width, height}|null, event, created_at, deleted_at, deleted_by, deleted_staff: MemberRef|null}`. Pictures through the existing `GET /admin/proofs/{id}`.
- `POST /admin/crews/{crew_id}/chat` (maintainer) `{text}`: kind staff, `staff_id = actor`; link filter and max length apply; no daily limit; allowed until prune (read-only crews too); pushes to every live member, muted or not. Audit `chat_posted` (target crew, detail = first 200 chars).
- `POST /admin/chat/{message_id}/delete` (admin) `{reason}`: any message; `deleted_by = staff`, picture discarded, `rev` bumped. Audit `chat_deleted`.
- `POST /admin/crews/{crew_id}/chat/pause` (admin) `{user_id, paused: bool, reason}`. Audit `chat_paused` / `chat_unpaused`. A paused member reads but cannot post (`why_not: "paused"`).
- `services/roles.py` `_MAY`: maintainer gets `chat_posted`; admin gets `chat_deleted`, `chat_paused`, `chat_unpaused`. `services/audit.py` `ACTIONS`: `posted in the chat of`, `deleted a chat message in`, `paused the chat of`, `let back into the chat`.

Panel:
- `app/(testtrack-admin)/testtrack/admin/crews/[id]/chat/page.tsx` renders `<CrewChat id={id} />`.
- `components/testtrack-admin/pages/CrewChat.tsx`: `useList<AdminChatItem>` on `crews/{id}/chat`; a post box ("Post as {bot.name}", counter, server error under it); rows with time, author (link to the member page) and app, or the bot name with "posted by {staff}"; mention tokens shown as `@label`; picture thumb via `proof.tsx`, or "Picture expired" / "Picture removed"; deleted rows greyed with "Deleted by the author, {time}" or "Deleted by {staff}, {time}"; Delete (`can("chat_deleted")`) and Pause / Let back in (`can("chat_paused")`) through `useConfirm` with reasons.
- `lib/testtrack-admin/chat-types.ts` (`types.ts` is already over 500).
- `components/testtrack-admin/pages/Crews.tsx` is 533 lines: first move `whose`, `tryOf`, `endings`, `newestFirst`, `seatStory`, `ending`, `firstName`, `AppealNote` unchanged into `components/testtrack-admin/crewStory.tsx`, then add a "Chat" link in the crew's head actions.
- Mock mode: `lib/testtrack-admin/mock-chat.ts` (seed per crew with one deleted message and one expired picture; GET list, POST post, POST delete, POST pause), three dispatch lines in `mock.ts` before the generic GET and write, the four keys in the mock `can` list, and labels wherever `labels.ts` lists action keys.

## App
Design: `docs/design/crew-chat/chat-v1.html` (frames for entry, thread, composer states, long press, mute, notifications). Where it offers two options the owner picks; this plan assumes the deleted stub (option 1) and leaves the entry point open (option A, a Chat tab on the crew page, or option B, a floating Chat button).

Rules: `docs/app-rules.md` (layers, 500 lines, stateless bodies, colours only from `Tt.colors`); back arrow through `PageBar`; sheets through `PickerSheet` + `SearchField`; `Skeleton` not spinners; `CountBadge` for counts; `RobotState` for empty and failed states; `Ask` for confirms; `ProofImage` / `PictureThumb` / `ZoomableImage` for pictures; `WhileShown` for the poll. `net/Api.kt` (497 lines), `ui/CrewViewModel.kt` (497), `ui/MainShell.kt` (497) and `MainActivity.kt` (477) must stay under 500: new code goes in new files.

Copy (plain, no em dashes):
- Composer: placeholder `Message your crew`; under it `7 messages left today`, `1 message left today`, `No messages left today. More after midnight.`; picture button state `2 pictures left today`.
- Link refused: `Links can't be sent in crew chat. Remove the web address and try again.`
- Read only: `This crew has finished. You can read the chat but not post.` Out: `Your app is out of this run, so you can read the chat but not post.` Paused: `The team has paused your messages in this chat.` Gone: `This chat was deleted with the crew's details.`
- `Message deleted`, `Picture expired`, `Not sent. Tap to try again.`
- Empty: `No messages yet` / `Say hello to the people testing with you.`
- Menu: `Reply`, `Copy text`, `Delete`, `Mute chat`, `Unmute chat`; muted note `Muted. You'll still hear when someone mentions you.`
- First open, once: title `Crew chat`, then `1. Be kind and keep it about testing.` `2. Links can't be sent.` `3. You can send {n} messages and {p} pictures a day. Pictures disappear after {h} hours.` `4. The TestTrack team can read and remove messages.` Button `Got it`.
- Channels: `Crew chat` (`New messages in your crews' chats.`) and `Crew chat mentions` (`When someone in a crew chat mentions you.`).
- No-permission hint inside the chat: `Turn on notifications to hear about new messages.`

Poll lifecycle: `WhileShown` starts the loop on resume and stops it on pause or leaving. Interval = `poll_seconds`, x1.5 after each empty poll up to `poll_max_seconds`, back to `poll_seconds` after any change or own send; on failure back off the same way and show the offline line after 2 failures. One request in flight at a time. After a poll that brought others' messages, one `POST read` with the new `rev`. While the screen shows, `ChatPresence.openCrew = crewId` so a push for it is not drawn, and its notification is cleared on open.

Mentions: typing `@` at a word start (or the @ button) opens a `PickerSheet` with `SearchField`: People (name, app) and Apps (app name, icon) from `people` where `in_crew`. Picking inserts `@Label ` and records the span; on send each span becomes `@[seat:<id>]` or `@[app:<id>]`. Editing inside a span drops it (plain text). Visible length counts code points with a span as 1, the same rule as the server.

## Pieces

### B1. Schema, settings, errors (backend)
Files: `app/db/models/crew_chat.py` (CrewChat, ChatMessage, ChatMember), `app/db/models/__init__.py`, `app/db/models/push_token.py` (`chat` column), `app/db/models/upload.py` (kind comment gains "chat"), `alembic/versions/0037_crew_chat.py`, `app/core/config.py`, `.env.example`, `.env.dev.example`, `app/core/errors.py` (`ChatOff` 404 `chat_off`, `ChatClosed` 409 `chat_closed`, `ChatGone` 410 `chat_gone`, `ChatPaused` 403 `chat_paused`, `LinkNotAllowed` 422 `link_not_allowed`, `ChatLimit` 429 `chat_limit`).
Check: migration up and down on a throwaway Postgres; `mypy --strict`; the settings-vs-example CI check; EXPLAIN comments on the new indexes.

### B2. Link filter (backend, parallel with B1)
Files: `app/services/chat_links.py`, `app/services/data/tlds.txt`, `scripts/update_tlds.py`, `tests/fixtures/chat_link_cases.json`, `tests/services/test_chat_links.py`.
Tests: every fixture case; the allow list; the timing guard; each normalise step on its own.

### B3. Core chat (backend, after B1 and B2)
Files:
- `app/repositories/chat.py`: `ensure`, `locked`, `crew_shared` (FOR SHARE), `by_id`, `latest`, `changed_since`, `before`, `count_today(crew_id, author_id, since, pictures)`, `member`, `upsert_member`, `add`, `seats_for_people`.
- `app/services/chat_access.py`: `reader_seat`, `chat_state`, `may_see_picture`.
- `app/services/chat_text.py`: `TOKEN` regex for `@[seat:<uuid>]` and `@[app:<uuid>]`, `tokens`, `visible_length`, `clean` (strip, drop control chars except newline, at most 2 blank lines in a row, NFC), `plain` (tokens to `@label`).
- `app/services/chat_limits.py`: `Left`, `day_start`, `next_day`, `left`, `require_picture_room`, `limits_view`.
- `app/services/chat_said.py`.
- `app/services/crew_chat.py`: `page`, `post`, `delete_own`, `set_muted`, `mark_read`; shared `append(db, *, chat, crew_id, kind, text, author_id=None, author_seat_id=None, staff_id=None, event="", mentions=(), reply_to_id=None, picture_id=None, message_id=None, now)` and `mark_deleted(db, *, chat, msg, by, staff_id=None, now)`.
- `app/services/chat_push.py` as a stub (`enqueue` does nothing) so B4 only fills it in.
- `app/services/chat_views.py`, `app/schemas/chat.py`, `app/api/v1/crew_chat.py`, `app/api/v1/router.py` (include).
- Uploads: `app/services/uploads.py` (`CHAT`, in `KINDS` and `CREW_KINDS`, `limit_for`, `store` calls `chat_limits.require_picture_room`, `may_see` branch to `chat_access.may_see_picture`), `app/services/upload_images.py` (`SHAPES["chat"] = Shape(1080, 2400, 75)`), `app/api/v1/uploads.py` (`Literal` gains `chat`).
- `docs/crew-chat.md`: the rules in short.

`post` order: chat on; crew FOR SHARE (404, or 410 if pruned); my live seat (else 409 `out` or 403); crew not DONE (409 `ended`); ensure and lock the chat; replay by id; paused (403); clean text; tokens valid (a seat in this crew via `in_crew`) and within the count; visible length; link filter with the crew's package names; day counts in the seat's timezone; reply in the same crew; claim the picture (`uploads.claim(kind="chat", subject_id=id, crew_id=crew.id)`); `append`; member `read_rev = rev`; `chat_push.enqueue`.

Tests (`tests/services/test_crew_chat.py`, `tests/api/test_crew_chat_api.py`, `tests/services/test_chat_pictures.py`): post by a confirmed and by a checking seat; removed seat 409 `out`; stranger 403; DONE 409 `ended`; pruned 410; off 404; the 11th message in the member's day 429 with `resets_at` at their next midnight; the next local day is free again; deleted messages still count; a replay returns the same row and does not count; the same id from someone else 409; a link 422 with nothing stored; bad and too many mentions 422; visible length with emoji and tokens; a reply from another crew 404; the 4th picture refused at PUT before a file is written; someone else's picture refused; two posts at once get seq n and n+1; `since` returns inserts and tombstones in rev order; a tombstone has no text, picture, mentions or reply; deleting someone else's 403; deleting twice ok; `since > rev` gives `reset`; `before` paging; read never moves back or past `rev`; mute kept; `may_see` member yes, stranger no, deleted no, expired no; route contracts (`scripts/check_routes.py`); `Cache-Control: no-store`.

### B4. Push (backend, after B3)
Files: `app/services/chat_push.py`, `app/repositories/chat_push.py` (recipients with member rows, waiting counts, mention check, newer message), `app/realtime/push.py` (`send_each`), `app/repositories/push_tokens.py` (`keep(..., chat)`, `of_user(..., chat_only)`), `app/schemas/user.py` (`PushTokenIn.chat`), `app/api/v1/me.py` (pass `chat`).
Tests (`tests/services/test_chat_push.py`, fake sender): enqueue sets `available_at`; author skipped; muted skipped; a mention by member token and by app token breaks mute; a staff line breaks mute; a member who has read is skipped; a burst sends once from the newest row; max wait sends during a long burst; tokens without `chat` skipped; dead tokens dropped; payload keys, collapse key, ttl, priority; a deleted message sends nothing; one refused token does not raise; existing `send` tests untouched.

### B5. Bot (backend, after B3)
Files: `app/services/chat_bot.py`, `app/repositories/chat_bot.py` (crews to look at, all seats of a crew).
Tests (`tests/services/test_chat_bot.py`): baseline posts nothing; each table row once; checking to rejected silent; a crew that refills after reopening announces again; an extension after `last_night` posts it again; cancelled vs ended; pruned crews skipped; bot lines bump `rev` but not unread or limits.

### B6. Cleanup, prune, erase (backend, after B3)
Files: `app/services/chat_cleanup.py`, `app/repositories/chat_cleanup.py` (`expiring`, `unclaimed`, `delete_crew`, `of_author`), `app/services/crew_prune.py`, `app/services/admin_member_delete.py`, `scripts/prune_report.py`.
Tests (`tests/services/test_chat_cleanup.py`, additions to `test_crew_prune.py` and `test_admin_member_delete.py`): an old picture goes (row now, file after commit, `rev` bumped, state expired); a young one stays; an old unclaimed upload goes; prune removes all chat rows and reports `chat`; a post racing a prune gets 410 and leaves no chat row; erase blanks the member's messages.

### B7. Unread on crews and actions (backend, after B3)
Files: `app/services/chat_reads.py` (`badges(db, user_id, crews) -> dict[uuid, dict]`, `unread_total`), `app/repositories/chat_reads.py` (one grouped count for all of a member's crews), `app/schemas/crew.py` (`chat_state`, `chat_unread`, `chat_muted` on `CrewDetailOut`), `app/api/v1/crews.py` (apply the badges in `list_crews` and `read_crew` with `model_copy`), `app/services/actions.py` and `app/schemas/actions.py` (`crew_chat`).
Tests (`tests/services/test_chat_unread.py`): own, bot and deleted messages left out; read clears; muted counted on the tile, not in actions; `chat_state` for off, open, out, done, pruned.

### B8. Admin backend (backend, after B3)
Files: `app/services/admin_chat.py`, `app/repositories/admin_chat.py`, `app/schemas/admin_chat.py`, `app/api/v1/admin_chat.py`, `app/api/v1/router.py` (include), `app/services/roles.py`, `app/services/audit.py`, `docs/admin-console.md`.
Tests (`tests/services/test_admin_chat.py`, `tests/api/test_admin_chat_api.py`): a maintainer reads deleted rows with their text; a staff post shows to members as `bot` with no author, with an audit row; the link filter applies to staff posts; admin deletes any message, picture discarded, audit; a maintainer's delete is 403; pause blocks posting with `chat_paused`, unpause; `GET /admin/me` lists the new keys for the right ranks.

### W. Wiring (backend, after B4, B5, B6)
Files: `app/services/sweeper.py` (stage `_chat` after `_pictures`: `chat_cleanup.expire_pictures`, `chat_cleanup.drop_unclaimed`, `chat_bot.sweep`; returns `{}` when chat is off), `app/services/outbox.py` (`elif kind == "chat": await chat_push.deliver(db, payload, notify._sender(), now=datetime.now(UTC))`).
Tests: `sweeper.run_once` returns the chat keys; an outbox `chat` row goes through `chat_push.deliver` (fake sender).
Then deploy to dev with `CHAT_ENABLED=true` and the chat settings in the dev `.env`.

### A1. App wire (once B3's shapes are final; parallel with B4 to B8)
Files: `net/ChatWire.kt` (ApiChatPage, ApiChatMessage, ApiChatPerson, ApiChatMention, ApiChatReply, ApiChatPicture, ApiChatLeft, ApiChatLimits, ApiChatBot, ChatPostIn, ApiChatSent, ChatMuteIn, ChatReadIn), `net/ChatApi.kt` (`Api.chat`, `Api.sendChat`, `Api.deleteChat`, `Api.muteChat`, `Api.readChat`, extension functions like `PictureApi.kt`), `net/CrewsWire.kt` (`chatState`, `chatUnread`, `chatMuted` on `ApiCrew`), `net/Wire.kt` (`ApiActionParts.crewChat`; `PushTokenIn(token, chat)` with **no default** on `chat`, because the shared `Json` does not encode defaults), one call site in `net/Api.kt` (`PushTokenIn(token, chat = true)`, same line count), `data/ServerUpload.kt` (`CHAT`).
Check: compile; a desk test decoding a recorded `ChatPageOut` JSON.

### A2. App domain (parallel from the start)
Files: `domain/ChatLinks.kt` (same steps and rules as the server, `fun blocked(text: String, allow: Set<String>): Boolean`), `domain/ChatTlds.kt` (generated from the server's `tlds.txt`), `domain/ChatDraft.kt` (spans, `applyEdit`, `toWire`, `visibleLength` by code points), `domain/ChatPolling.kt` (`nextDelayMs(baseS, maxS, emptyStreak, failures)`), `domain/ChatThread.kt` (generic merge by id, order by seq, tombstones replace, `reset` replaces all).
Tests: `app/src/test/.../domain/ChatLinksTest.kt` over `app/src/test/resources/chat_link_cases.json` (a copy of the server fixture; change both together), `ChatDraftTest`, `ChatPollingTest`, `ChatThreadTest`.

### A3. Chat view model (after A1, A2)
Files: `viewmodel/ChatViewModel.kt` (state, `bind(crewId)`, `start(context)`, `stop()`, poll loop, `loadOlder`, `toggleMute`, `reply`, `clearReply`), `viewmodel/ChatSend.kt` (extension funs `send`, `retry`, `delete`, `sendPicture(context, uri)` through `ServerUpload.send(id, ServerUpload.CHAT, "image/jpeg", seatId = mySeat) { ProofImages.bytes(app, uri) }`), `data/ChatPresence.kt`, `data/ChatPrefs.kt` (rules seen once), `data/ChatSignals.kt` (a `MutableSharedFlow<Unit>` ticked by a chat push).
Edges: an optimistic line under the minted id, replaced by the server's; a failed send stays with `Not sent. Tap to try again.` and retries with the same id; `chat_limit` updates `left`; 507 uses `ServerUpload.FULL_NOW`; the local link check runs before any request (the server stays the judge); `state = gone` stops polling.
Tests: the logic lives in domain and is tested there; the view model is checked by compile.

### A4. Chat screen (after A3)
Files: `ui/CrewChatScreen.kt` (wiring, `WhileShown`, photo picker `PickVisualMedia.ImageOnly`, `PageBar` titled `{crew} chat` with a mute action), `ui/ChatBody.kt` (stateless: list with newest at the bottom, `Skeleton` while loading, empty and gone `RobotState`, read-only banner, composer), `ui/ChatBubble.kt` (mine / others / bot line with bot name and avatar; reply quote; mention spans in `accentInk` bold, nothing drawn as a link; picture thumb or `Picture expired`; tombstone; long-press menu), `ui/ChatComposer.kt` (pill text field 1 to 5 lines, @ and picture buttons, send, counts, reply strip, error line), `ui/ChatMentionSheet.kt`, `ui/ChatRulesSheet.kt`, `ui/ChatEntry.kt` (the chosen entry: a Chat tab on the crew page or a floating Chat button, with `CountBadge`), `ChatRoutes.kt` (`object ChatRoute { PATTERN = "crew/{id}/chat"; fun of(id) }`, `NavGraphBuilder.chatRoutes(nav)`, `@Composable OpenChatOnTap(nav, openChat)`).
Tests: `app/src/androidTest/.../ui/ChatBodyTest.kt` for empty, read only, paused, deleted, expired and failed send (written now, run when the owner asks).

### A5. Entry points, push, badges (after A1; parallel with A4, disjoint files)
Files: `data/ChatNotifications.kt` (channels `crew_chat` default and `crew_chat_mentions` high; `show(context, data)`, `cancel(context, crewId)`; tag and id `chat:<crewId>`; tap intent extra `MainActivity.EXTRA_OPEN_CHAT`), `data/PushService.kt` (before the permission check `ChatSignals.tick()`; then `kind == "chat"` goes to `ChatNotifications.show` unless `ChatPresence.openCrew == ref`), `data/PushRepo.kt` (create the chat channels with the others), `MainActivity.kt` (`openChat` state, read in `onCreate` and `onNewIntent`, `chatRoutes(nav)` and `OpenChatOnTap(nav, openChat)` in `AppNav`, `onChat` passed to `CrewPage`), `ui/CrewPage.kt` (show the `ChatEntry` when `chatState` is open or read only), `ui/CrewModels.kt` and `ui/CrewWire.kt` (`chatState`, `chatUnread`, `chatMuted` on `Crew`), `ui/CrewTileParts.kt` (`CrewTileHeading` gets an optional unread `CountBadge`, faint when muted), `ui/MainShell.kt` (`MainTab.GROUPS to it.crew + it.parts.crewChat`, same line), `viewmodel/TabCountsViewModel.kt` (refresh on `ChatSignals`).
Check: compile; a tap on a notification with the app closed, in the background and in front opens the right chat (owner device test only when asked).

### P1. Panel (after B8)
Files as in Admin above. Check: `npm run analyze`, `npm run build`, an e2e spec in mock mode (open a crew's chat, post as the bot, delete with a reason, the deleted row stays with its mark).

## Order
1. Wave 1, in parallel: B1, B2, A2.
2. Wave 2: B3.
3. Wave 3, in parallel (disjoint files): B4, B5, B6, B7, B8, A1. `router.py` is touched only by B3 and B8, in different waves.
4. Wave 4: W, then deploy to dev; in parallel A3 and P1.
5. Wave 5, in parallel: A4, A5.
6. Owner tests on dev (debug app, panel run locally against dev).
7. Production only on the owner's word: backend with `CHAT_ENABLED=false` first (schema ships dark), app to internal, Play forms, then `CHAT_ENABLED=true`.

Branches: backend `feat/crew-chat` from `feat/dev-all` in its own worktree; app `feat/crew-chat` from `main`; panel `feat/crew-chat` from the branch carrying the VPS picture work.

## Risks
- Play user-generated content policy: Play expects an in-app way to report objectionable content and users, a way to block users, and terms users accept. The owner chose no report feature for now. Fine on dev and internal; a review risk before production. The first-open rules sheet covers only the terms part.
- Play Data safety and the privacy page: chat messages and photos are new user data (collected, shown to crew members, deleted after the crew). Update both before production.
- Prune switch: chats are deleted only where `PRUNE_ENABLED` is on.
- Old app builds never get chat pushes (`push_tokens.chat`) and ignore the new fields; members on old builds just do not see the chat.
- Link filter false positives on file and package names; tuned through the fixture file, which is copied in both repos and can drift. Change both together.
- Disk: pictures last a day and the 507 guard stays; worst case about 7 MB per crew.
- Deploy overlap: an old container's outbox meets a `chat` row, fails with "Unknown outbox kind" and retries; the new container delivers it.
- Abuse: 10 a day caps it; the team can delete and pause; no member report yet.
- Load: an open chat polls every 5 to 30 s; 15 members watching at once is at most 3 requests a second, each a primary-key read when nothing changed.

## Open questions for the owner
1. Report and block: add a minimal "Report to the team" on a message (and a way to hide a member) before production, for Play's UGC policy?
2. Limits per crew or across all of a member's crews? Plan: per crew.
3. Does a picture also use one of the 10 messages? Plan: yes.
4. Admin pause of a member: keep it (planned) or drop it?
5. Bot name and avatar.
6. Chat entry: option A (a Chat tab on the crew page) or option B (a floating Chat button) from `docs/design/crew-chat/chat-v1.html`? Deleted messages: option 1 (stub) as planned, or option 2 (gone)?
7. The mockup's notification actions (Reply, Mark as read) need a reply sent from the shade. Build them now or after v1? Plan: after v1.
