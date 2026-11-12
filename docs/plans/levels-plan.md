# Levels that mean something

Plan, 2026-11-12, updated the same day with the owner's decisions. Read-only research against `main`
of the app (H:\android\onGoing\test_track), the backend (H:\_group_projects\test_track_backend) and
the admin panel (H:\next\eazyverse, `app/(testtrack-admin)` + `components/testtrack-admin`).
Nothing here is built yet.

## Decisions (2026-11-12)

1. **Ranks uses the new number.** Public Play reviews give 0 exp everywhere: level, level tix,
   chips and Ranks.
2. **Level tix are 3 x level** when reaching level n, for n = 2 to 20. Lifetime total 627 tix.
3. **Existing members** get one bonus, for their current level only.
4. **No level tix** for a second account on the same phone (starter state `phone_used`) or for an
   account the team has held.
5. **App bar:** the left side stays as today (the level robot badge plus "Level N" and "X exp to
   level N+1"; signed out stays as today). Only the right side changes, to Option A's balance:
   plain figures with a small ticket, thousands separators, no pill, no gold; tapping it opens the
   You page. The update button keeps its place, with a robot-blue dot instead of gold. Option C
   (the chip and the sheet behind it) is dropped. Perks and "what's next" live in a Level sheet
   opened by tapping the robot or the level lines (section 6). The robot shows no sign-in arrow
   while signed-in data is loading.
6. **Level chip at level 1: still open.** A mockup is being made. Recommendation unchanged: hide it
   at level 1.
7. **No limit on reviews in hand.**
8. **Starter tix stay at 120.**

## 0. Where it stands today (verified)

| Fact | Evidence |
|---|---|
| Exp = `opens * 1 + approved_reviews * 5`, the same number Ranks sorts by | backend `app/core/rules.py:239-273` |
| An open counts only once its picture is in | `app/repositories/visits.py:14-20` (`counted()`) |
| A review counts only when APPROVED, live and closed alike | `app/repositories/standing.py:25-50` (no `is_live` filter) |
| Level curve: reach level n at `25*n*(n-1)/2` | `rules.py:297-312` |
| Level computed on read, never stored | `app/services/standing.py:79-96`, `app/api/v1/me.py:39` |
| No rule reads level. Only `/me` and the admin member list show it | `services/standing.py:89`, `services/admin_members.py:67` |
| App shows it only in the bar | `ui/TopHud.kt`, `ui/LevelRobotBadge.kt`, `viewmodel/HudViewModel.kt` |
| Approved reviews are final; nothing turns APPROVED back | the only transitions to APPROVED: `services/moderation.py:54,309,372` |
| Exp can still drop: deleting a request hard-deletes its reviews | `repositories/removal.py:113-126` (`drop_post`) |
| The only tix minted today: starter (120, once per phone) and team gifts/adjusts | `rules.py:24`, `services/tix_gift.py`, `admin_members.adjust_tix` |
| Juror gate: account 7+ days old and 2+ approved reviews | `rules.py:78-79`, `services/moderation.py:179-182` |
| Crews have no queue: a seat goes into the first crew with room | `services/crews.py:484` (`_crew_with_room`) |
| A clean run refunds the seat in full | `rules.py:26-31`, `services/seat_price.py` |
| The bar mockup (today, A, B, C) is untracked; Option A's balance is `.tixq` and `barA` | `docs/design/appbar/circle.html:137-139, 329-336` |
| Signed in but still loading, the robot draws the sign-in arrow (its alpha is `1 - badgeAlpha`, and `badgeAlpha` is 0 whenever level is null) | `ui/LevelRobotBadge.kt:62, 162-167` |
| The update button's dot is gold | `ui/UpdateChip.kt:152, 164` |
| The You page shows no tix balance; its record grid has points, place, opens, reviews, crews | `ui/MeRecord.kt:87-95`, `ui/MeScreen.kt:203-215` |
| No helper formats numbers with thousands separators (no `NumberFormat` or `%,d` in the app) | search of `app/src/main/java` |

Files near the 500-line limit that this plan must not grow much: `services/visits.py` 495,
`services/feed.py` 493, `services/moderation.py` 486, `services/views.py` 451, app `net/Wire.kt` 493,
`ui/MainShell.kt` 489.

## 1. Approach

Level becomes a trust and reliability measure built only from verified testing work: crew opens
with their picture, and private (closed-test) feedback a developer accepted. Public Play reviews
stop adding exp, on Ranks too. On that number we add a small tix bonus per level (3 x level, levels
2 to 20), a quiet "Lv 7" chip next to names, and in a second phase a level route into jury duty.
Every level-up is paid once, idempotently, through the existing ledger.

**Rejected alternative:** keep exp as it is and pay the bonus only on "the opens part" (two tracks
under one bar). A member would level up from reviews and get nothing, or get tix for a level partly
earned with public reviews, and nobody could tell which. It is also two counters over one set of
facts, which `rules.py:291-295` already argues against. One clean definition is easier to explain
and to defend under Play policy.

## 2. Goals and principles

1. **Level = verified work, not activity.** Only things with proof count: an open with its picture,
   private feedback a developer accepted.
2. **Nothing that pays tix is fed by public Play reviews.** See section 5.
3. **Calm.** No confetti, no popups in the middle of a nightly round, no push for a level-up.
4. **Economy stays small.** Level tix are a new faucet; they stay well under what a seat or a
   request costs per month.
5. **Abuse costs more than it pays.** Opens need a seat (100 tix) and a picture each; a second
   account on the same phone already gets no starter tix and gets no level tix either.
6. **Exp is a record of facts.** No decay, no manual edits. The team can hold an account's level
   rewards, not rewrite its level.

## 3. The level curve

**Keep `LEVEL_STEP = 25`.** `tests/services/test_levels.py`
(`test_a_whole_fortnight_is_real_progress...`) pins one crew run (196 opens) to level 3 to 5; it
lands at level 4 and still will. The curve slows itself (level n costs `25*(n-1)` more), so no
level cap is needed.

Exp needed: L2 25, L3 75, L4 150, L5 250, L6 375, L7 525, L8 700, L9 900, L10 1125, L15 2625,
L20 4750.

How long it takes (14 opens a night on 14 nights; crews take time to fill, so one run a month per
seat is assumed):

| Level | Crew only, 1 seat (~196 exp/month) | 1 seat + 3 private feedback a week (~260/month) | 2 seats + 3 private a week (~460/month) |
|---|---|---|---|
| 2 | night 2 | night 2 | night 1 |
| 3 | night 6 | night 5 | night 3 |
| 4 | night 11 (first run) | about day 12 | about day 7 |
| 5 | month 2 (run 2, night 4) | end of month 1 | about day 14 |
| 6 | month 2 (run 2, night 13) | about day 43 | about day 25 |
| 7 | month 3 | about day 61 | month 2 |
| 8 | month 4 | about day 81 | month 2 |
| 9 | month 5 | about day 104 | about day 60 |
| 10 | month 6 | about day 130 | about day 75 |

Levels 2 to 4 come inside the first run (early feedback), then roughly one level a month for a
typical member. Level 10 means about half a year of steady crews. That is the shape wanted.

**Decay or loss: none.**
- No decay. A level that drains while someone is away punishes a break and turns the bar into a
  worry, the opposite of calmer.
- No exp penalty for a lost dispute. A refused review never counted (only APPROVED counts), so the
  bad act earned nothing. Instead a recent lost case blocks jury duty (phase 2).
- A fake review the team finds later: the team deletes the request (exp drops by itself through
  `drop_post`), holds level rewards, and corrects tix with the existing audited adjust. No
  automatic clawback; ledger rows are never edited.
- Because exp can drop and rise again, the bonus is keyed by level in the ledger, so each level is
  paid at most once, ever.

## 4. Each benefit as an exact rule

### B1. Level tix (phase 1)

- Reaching level n (2 <= n <= 20) pays `3 * n` tix once: L2 6, L3 9, L4 12, L5 15, L8 24, L10 30,
  L20 60. Levels above 20 pay nothing. Lifetime total 627 tix.
- Ledger: `reason="level"`, `ref_id=str(n)`, note `Reached level n`. `"level"` joins
  `ONCE_PER_CAUSE` (`services/tix.py:24-26`), so the ledger's unique idempotency key makes a double
  payment impossible even under a race or a reset column.
- Not paid while: the server switch is off; the team holds the account's level rewards; the account
  is a second account on a phone (starter state `phone_used`, `repositories/starter.py`); the
  account is deleted.
- Paid when `/me` is read (instant for someone using the app) and on each sweeper pass (backstop).
  No push notification.

Economy per active member per month (3 x level against the table above; it works out near 0.14 tix
per exp, about 1 tix per 7 opens):

| Profile | Month 1 | Months 2-6 average | Compare |
|---|---|---|---|
| Crew only, 1 seat | 27 (L2-L4) | ~27 (L5-L10) | about a quarter of a seat (100); about one minimum review (20) |
| 1 seat + private feedback | 42 (L2-L5) | ~31 (L6-L11) | |
| 2 seats + private feedback | 60 (L2-L6) | ~59 (L7-L15) | hits the level 20 limit around month 10 |
| Starter tix | 120 once | 0 | |

Over a first year a typical member (crew only, one seat, about level 14) gets about 312 level tix,
about 2.6 times the 120 starter tix. The whole lifetime total (627) is about five starter grants.

A community of 100 active members (50 light at ~13/month, 40 typical at ~27, 10 heavy at ~60)
mints about 2,300 tix a month, the same as the starter tix of about 19 new members. Starter tix
stay at 120. Tix are only spent inside TestTrack (no shop), so the effect is more Circle requests
and seats, which is demand for testers. Watch it (section 10); the two constants and the switch are
the dials.

Backfill: an existing member gets the bonus for the level they are at when it ships, once, not all
past levels (a level 9 member gets 27, not 132). That is at most 60 tix per account (level 20 and
above); a level 4 member gets 12. As an illustration, 300 accounts settling at an average level 5
would mint about 4,500 tix once. Levels drop for members whose exp was mostly public reviews, so
the real figure is likely lower. No migration rule: `level_paid` starts NULL for everyone, and the
first settle with the switch on sets it to `level - 1` and pays that one level. Run the preview
script (S6) before switching on to see the exact total.

### B2. Level chip next to names (phase 1)

On Ranks rows, crew member rows, and the review rows a request owner sees. Not on the juror's case
view (jurors judge the evidence, not the person). Details in section 6.

**Level 1: open.** A mockup is being made. Recommended: hide the chip at level 1 so a newcomer is
not marked as new. The pieces below follow the recommendation; if the owner picks "show", A2 drops
the level-1 check and its test.

### B3. Jury from level 4 (phase 2)

- Today: 7+ days old AND 2+ approved reviews.
- New: 7+ days old AND (2+ approved reviews OR level 4+) AND no review of theirs refused by the
  members (dispute outcome `upheld`) closed in the last 30 days.
- Why OR and not a replacement: the juror pool is thin (`rules.py:74-77`: "Finishing a crew would
  be stronger, and almost nobody has yet"), and an unjudged case goes to the reviewer after
  `DISPUTE_DAYS`. Level 4 is about 11 nights of a crew, which is the "finished a crew" signal that
  comment wanted. Narrowing the pool would make disputes worse.
- `JUROR_DAILY_CASES` stays 5 for everybody. More cases for higher levels would let grinders farm
  juror fees and decide most cases.
- **Weighted votes: rejected.** Three jurors, majority of two. A level weight lets one high-level
  juror decide alone, level measures volume of work rather than judgement, and it makes high-level
  accounts worth buying.

### Rejected or parked

- **Faster or lighter approval for high levels: rejected.** Approval is the request owner's call,
  and the 24 h self-approval already protects reviewers (`rules.py:65-67`). Paying high levels
  faster ties a review's payment to level, pulls public reviews back into the reward loop, takes
  away the owner's time to check, and makes high-level accounts worth faking or selling. The chip
  on the review row gives owners the signal without changing a rule.
- **Crew seat priority: parked.** There is no queue (`crews.py:484`), so priority means nothing
  today. Revisit only if crews ever have a waiting list.
- **Seat discount: parked.** A clean run already refunds the seat in full, so a discount only
  saves tix for members who miss nights. It would reward the less reliable high-level member.

## 5. Policy safety: which exp feeds rewards

Google Play's ratings and reviews policy forbids incentivising ratings or reviews. Circle already
pays tix for live (public) reviews, and the app already warns owners (`ui/AskScreen.kt:174-182`).
A level bonus fed by those reviews would add a second, ongoing reward for public reviews, and a
public "Lv" badge earned from them is a status reward too.

**Rule:** exp (so level, level tix, the level chip, level gates and Ranks) counts only:
- crew opens with their picture in (`visits.counted()`), 1 each;
- approved Circle reviews of closed tests (`Review.is_live == False`), 5 each. These are private
  tester feedback to the developer, not public ratings.

Public Play reviews (`is_live == True`, including the "no review box" ones sent in TestTrack) add
0 exp, everywhere. They still pay their reward as today; that existing risk is unchanged and out of
scope.

Copy rule: no app text, store text or release note links level, level tix or any perk to reviews in
general or to Play reviews. Where exp is explained, say "private feedback", never "reviews".

**Ranks (decided):** Ranks uses the same number, so there is one number everywhere and the public
board stops rewarding public reviews too. Places reshuffle once, and members who mostly did public
reviews drop.

## 6. Visibility

| Where | Who sees it | What |
|---|---|---|
| App bar, left | the member | as today: robot badge with the level on its plaque and the exp ring, "Level 7", "60 exp to level 8". Signed in and loaded, the robot and the two lines are one button that opens the Level sheet |
| App bar, right | the member | update button (if any), then the balance: small ticket and `1,240`. Tap opens the You page |
| Level sheet | the member | progress, how exp is earned, level tix and the next level's tix, unlocks (phase 2), what tix are for |
| You screen | the member | the points cell becomes "Level 7" and "60 exp to level 8"; tap opens the Level sheet |
| Ranks rows and podium | everyone, signed-out visitors too (`GET /board` is public) | "Lv 7" chip after the name |
| Crew member rows | crew members | "Lv 7" chip after the name |
| Review rows for a request owner (To check, review detail, request page) | the owner deciding | "Lv 7" chip after the reviewer name |
| Juror case view, public feed | nobody | no chip (feed posts are anonymous by design, `views.py:66-69`) |
| Admin panel | staff | level, exp parts, level paid, level tix total, held state |

**Chip look:** plain text pill `Lv 7`, label size about 12 sp, SemiBold, `Tt.colors.text3` ink, no
fill, 1 dp `glassBorder` outline, 6 dp x 1 dp padding, after the name on the same line. No colour
tiers, no icons. Hidden at level 1 (open, see B2). Screen reader: "Level 7".

**App bar (decided).** Left side unchanged from today, right side Option A's balance:
- Balance: `Lucide.Ticket` at about 17 dp in `Tt.colors.text3`, 6 dp gap, then the figure in
  `titleSmall` with tabular figures (`fontFeatureSettings = "tnum"`), SemiBold, `Tt.colors.text`.
  Thousands separators from the phone's locale. No pill, no fill, no outline, no gold. Touch area at
  least 48 dp tall. It keeps today's two motions: the digits roll from the old figure to the new
  one, and the change floats up and fades (green `ok` for earned, `text3` for spent). The ticket
  hop and the digit swell go with the pill.
- While signed-in data loads: the ticket and a skeleton bar about 44 x 14 dp in place of the figure
  (skeletons, not spinners). The left side shows today's "TestTrack" / "Signed in" lines, and the
  robot shows no sign-in arrow and is not tappable.
- Signed out: exactly as today (robot with the sign-in arrow, crew-filling lines, the Google Sign in
  pill on the right).
- Update button: same place and states as today; in the available state its dot is
  `Tt.colors.leafAccent` (light #1D5C82, dark #8BCBF7, the robot's blue) instead of `gold`. No new
  colour token.

**Where perks and "what's next" live: the Level sheet, opened by tapping the robot or the level
lines.** The level is already drawn there, so the tap goes where the eye is, and the balance keeps
its own tap (You). Rejected: perks only on the You page. That makes both halves of the bar go to
the same page and hides "what's next" one more tap down a page that is about the record and
settings. The You page's level cell opens the same sheet, so there are two ways in, one sheet.

**Level-up moment (calm):**
- The balance's float shows "+24" (the existing change animation, kept).
- The robot does its existing small level-up lift (`LevelRobotBadge.kt:52-61`), the plaque number
  changes, and a small robot-blue dot (`leafAccent`, same size as the update dot, 2 dp `glass`
  ring) appears on the robot's top-right corner.
- Opening the sheet shows one top line, "You reached level 8. 24 tix were added.", and clears the
  dot.
- No dialog, no auto-opening sheet, no confetti, no push. A level-up usually lands in the middle of
  a nightly round of 14 apps; anything that pops up there gets in the way.
- After the update, existing members see the dot once; the sheet's top line explains the change.

## 7. Backend changes

New file `app/services/levels.py` holds every level rule; hooks elsewhere stay one or two lines.

**Constants** (`app/core/rules.py`, levels section):

    LEVEL_TIX_PER_LEVEL = 3
    LEVEL_TIX_LAST = 20
    def level_tix(level: int) -> int   # 0 below 2 or above LEVEL_TIX_LAST, else 3 * level
    JUROR_MIN_LEVEL = 4                # phase 2
    JUROR_CLEAN_DAYS = 30              # phase 2

Update the `REVIEW_POINTS` comment (`rules.py:241-243`): it now means accepted private feedback.
`STARTER_TIX` stays 120; leave it and its comment alone.

**Schema** (migration `alembic/versions/0032_levels.py`, append only, with a downgrade):
- `users.level_paid integer NULL`, no default. NULL = never settled. The highest level whose tix
  were paid or deliberately skipped.
- `users.level_held_at timestamptz NULL`, set by the team; the reason lives in the audit log.
- No stored level or exp. Still computed from facts, as the codebase prefers
  (`repositories/standing.py:28-31`).

**Exp source** (`app/repositories/standing.py`): in `tallies` and `all_tallies` the review count
adds `Review.is_live.is_(False)`. This changes Ranks too (decided). Add
`public_reviews(db, user_ids) -> dict[UUID, int]` for the profile only. `rules.standing()` is
unchanged.

**`app/services/levels.py`** (new):
- `async def of(db, ids: list[UUID]) -> dict[UUID, int]`: batched levels from
  `standing_repo.tallies` (2 grouped queries).
- `async def settle(db, *, user: User) -> int` (tix paid now):
  1. Return 0 if `settings().level_bonus` is off, the user is deleted, or held.
  2. Compute the level. If `user.level_paid is not None and level <= user.level_paid`, return 0
     without locking (the common, cheap path).
  3. `user_repo.lock_balance(db, user)` (refreshes the row via `populate_existing`), check again.
  4. If `level_paid` is NULL: set it to `max(1, level - 1)` (the backfill rule).
  5. If the starter state is `phone_used` (`starter_repo.states`): set `level_paid = level`, pay
     nothing, return 0.
  6. For n in `level_paid + 1 .. level` with `level_tix(n) > 0`: `tix.move(user, level_tix(n),
     reason="level", ref_id=str(n), note=f"Reached level {n}")`.
  7. `level_paid = max(level_paid, level)`; return the sum. Never lowers `level_paid`.
- `async def settle_due(db) -> int`: for a sweeper pass. `all_tallies` once, load only users whose
  level is above `level_paid` (or NULL) in one query, `settle` each.
- `async def card(db, *, user) -> dict`: what the Level sheet shows (below).
- `async def hold(db, *, actor, user_id, held: bool, reason: str) -> dict`: admin, section 9.
- Phase 2: `async def may_sit(db, *, juror) -> bool` (age, approved-or-level, clean record).

**Settings** (`app/core/config.py`): `level_bonus: bool = False`, plus `LEVEL_BONUS=false` in
`.env.example` (CI fails without it). An on/off switch, not a price, so the amount stays a tested
constant.

**Ledger**: `services/tix.py:24-26` add `"level"` to `ONCE_PER_CAUSE`;
`services/admin_ledger.py` `LABELS["level"] = "Reached a new level"`.

**API**:
- `GET /me` (`api/v1/me.py:22-40`): call `levels.settle(db, user=me)` before building the answer,
  `await db.commit()` if it paid. A GET that can write: bounded (at most 19 ledger rows per
  account, ever), idempotent and locked, and it is what makes the tix land with the level instead
  of up to 5 minutes later. The write-path hook points (`visits.py`, `feed.py`, `moderation.py`)
  are all at the size limit. Say so in the route docstring.
- `GET /me/level` (new, `response_model=LevelOut`, docstring required by `scripts/check_routes.py`):

      level, exp, exp_into_level, exp_for_next,
      opens, private_feedback, public_reviews,
      tix_on: bool, next_tix: int, tix_blocked: "" | "held" | "phone",
      perks: [{level: int, title: str, unlocked: bool}]

  Perk titles come from the server so wording changes need no release (like `ApiBoard.openPoints`).
  Phase 1 perks list is empty; phase 2 adds the jury row.
- `GET /board`: `standing.view` (`services/standing.py:248-262`) adds `level`;
  `schemas/standing.py` `BoardRowOut.level: int = 1`. The board already has the tallies.
- `GET /profile`: `ProfileOut` adds `level`, `public_reviews`.
- Crew view (`services/views.py:407-450`): one `levels.of(db, [s.owner_id for s in rows])` before
  the loop, then `row["owner_level"] = lv.get(s.owner_id, 1)`; `schemas/crew.py` seat out adds
  `owner_level: int = 1`.
- Owner review rows: `views.waiting_view` (`views.py:96-115`) and `views.my_post_view`
  (`views.py:118-140`, batch the reviewer ids) add `reviewer_level`; `schemas/feed.py:194,206` add
  `reviewer_level: int = 1`. Not added to dispute or case payloads.

**Sweeper** (`services/sweeper.py:101`): new stage `_levels` calling `levels.settle_due`, appended
to `_STAGES`, in its own transaction like the others.

**Admin list** (`services/admin_members.py:67`): use `levels.of` instead of `rules.standing`.

**Migration and backfill**: the migration only adds two nullable columns. Nothing writes a
backfill; the NULL rule does it lazily and safely. A read-only preview script (S6) prints what
switching on would mint.

## 8. App changes

Wire (`net/`):
- `net/Wire.kt` is at 493 lines: first move the standing types (`BoardRow`, `ApiBoard`,
  `ApiProfile`, around lines 427-470) to a new `net/StandingWire.kt`, then add `level: Int = 1` to
  `BoardRow`, and `level`, `publicReviews` to `ApiProfile`.
- New `net/LevelsWire.kt`: `ApiLevel`, `ApiPerk` matching `GET /me/level`, every field defaulted.
- `net/CrewsWire.kt` `ApiSeat`: `@SerialName("owner_level") val ownerLevel: Int = 1`.
- Owner review types (`net/Wire.kt:165` and `:341`): `@SerialName("reviewer_level") val
  reviewerLevel: Int = 1`.
- `net/Api.kt`: `suspend fun level(): ApiLevel` for `GET me/level`.

UI:
- New `ui/LevelChip.kt`: `LevelChip(level: Int)`, draws nothing at level 1 (open, B2), tokens only.
- `ui/RanksScreen.kt` `BoardRowView` (~line 319): chip after the name; podium names in
  `ui/RanksPodium.kt` too. `work()` (~line 427) reads "12 apps opened, 3 private feedback" and the
  points label says exp (one word for one number).
- `ui/CrewModels.kt` `CrewMember` adds `level`; map it in `ui/CrewWire.kt`;
  `ui/CrewMembersPage.kt` `MemberRow` (~line 165): chip after the name.
- `ui/WaitingScreen.kt:95`, `ui/RequestScreen.kt:199`, `ui/WaitingDetail.kt:294`: chip after the
  reviewer name; carry `reviewerLevel` through `ui/FeedViewModel.kt:406` and the request model.
- App bar (`ui/TopHud.kt`, 325 lines):
  - `TixChip` (lines 209-325) is replaced by a smaller private `TixFigure(tix, loading, onClick)`:
    ticket icon and grouped figure, the roll and the float kept, the pill, gold ticket drawable,
    hop and swell removed from the bar. `R.drawable.tix_ticket` and the `tixTop`/`tixBottom`/
    `tixEdge` tokens stay (`FeedRows.kt` and `ThemeCard.kt` still use them).
  - `TopHud` gains `onOpenLevel: (() -> Unit)? = null`, `onOpenYou: () -> Unit = {}`,
    `levelNews: Boolean = false`. When signed in and the level is loaded, the robot and the two
    lines are one clickable row (role Button, `onClickLabel = "Open your level"`). The large-font
    branch (`fontScale > 1.2f`, lines 100-135) gets the same changes.
  - While signed in and loading, the right side shows `TixFigure` in its loading state (today it
    shows nothing).
- `ui/LevelRobotBadge.kt`: draw `signInMark` only when `onSignIn != null` (signed out); while signed
  in and loading the plaque stays empty with no arrow. New `news: Boolean = false` draws the
  robot-blue dot at the top-right corner.
- `ui/UpdateChip.kt:152,164`: the available-state dot uses `t.leafAccent` instead of `t.gold`;
  update the comment. The Settings row (`UpdateRow`, lines 190-272) is not part of this decision
  and stays as it is.
- New `domain/Figures.kt`: `fun grouped(n: Int, locale: Locale = Locale.getDefault()): String`
  using `NumberFormat.getIntegerInstance(locale)`. Pure, unit tested; the bar uses it.
- New `ui/AppHeader.kt`: `AppHeader(hudVm, onSignIn, onOpenYou)` draws `TopHud` and owns the Level
  sheet's open state. `ui/MainShell.kt` (489 lines) replaces its `TopHud(...)` call (lines 251-262)
  with `AppHeader(hudVm = hudVm, onSignIn = { asking = StartReason }, onOpenYou = {
  show(MainTab.PROFILE) })`, so the shell gets shorter, not longer.
- New `ui/LevelSheet.kt`: `LevelSheet(onDismiss)` (wiring) + stateless `LevelSheetBody(state)`,
  framed by `PickerSheet` (`ui/PickerSheet.kt:85`). Skeleton rows while loading, not a spinner.
- New `viewmodel/LevelViewModel.kt`: loads `Api.level()`; one instance for the header and the You
  page.
- `viewmodel/HudViewModel.kt`: after `Api.me()`, if the account matches the cached header's and
  `me.level > cached.level`, record level news (reached level, tix difference) per account in a
  small `data/LevelNews.kt` prefs store; cleared when the sheet opens. A one-time
  `levels_intro_seen` per account shows the intro line. The comparison itself is a pure function
  in `domain/LevelNews.kt` so it is unit tested.
- `ui/MeScreen.kt` / `ui/MeRecord.kt`: the points cell shows the level and opens the sheet; the
  place cell keeps opening Ranks.

Copy (final wording for the coder):
- Sheet title `Level 7`; under it `60 exp to level 8`.
- News line: `You reached level 8. 24 tix were added.`
- Intro line, once: `Levels now count. Each new level adds tix, and some levels unlock more. Public
  Play reviews no longer add exp, so your level may be lower than before.`
- Section `How you earn exp`: `1 exp for each crew app you open, once its picture is in.` /
  `5 exp when a developer accepts your private feedback.` / `Public Play reviews don't add exp.`
- Section `Level tix`: `Reaching a level adds 3 tix times that level, up to level 20. Level 8 adds
  24.` Then `Next: level 8 adds 24 tix.` At level 20 and above: `You've had the tix for every
  level. Your level still shows your experience.`
- Blocked, held: `Level tix are paused on this account. Write to us if you think this is a
  mistake.` Blocked, phone: `Level tix go to one account per phone.`
- Section `Unlocks` (phase 2): `Level 4: judge review disputes`.
- Section `Tix`: `Tix pay for crew seats and for feedback on your apps.`
- Bar, left, screen reader: `Level 7, 60 exp to level 8`, click label `Open your level`.
- Bar, balance, screen reader: `1,240 tix`, click label `Open your page`.
- Ledger note: `Reached level 8`.

## 9. Admin panel

Backend:
- `services/admin_members._detail` adds `level`: `{level, exp, opens, private_feedback,
  public_reviews, level_paid, level_tix_total, held_at}` (total = ledger sum where reason is
  `level`).
- `POST /admin/members/{id}/level-hold`, body `{held: bool, reason: str}`, ADMIN role. Hold sets
  `level_held_at`; release clears it and sets `level_paid` to the current level (no back pay for
  levels reached while held). Audit actions `level_held` and `level_released`, added to
  `roles._MAY[Role.ADMIN]` (`services/roles.py:46-70`) and to the audit sentence.
- Overview (`services/admin_overview.py`): one figure, level tix minted in the last 30 days.
- Staff cannot set a level or exp. Tix mistakes use the existing audited adjust.

Panel (H:\next\eazyverse):
- `components/testtrack-admin/pages/Member.tsx` (~line 127): a Level card with the fields above
  and a Hold / Release button through `Confirm.tsx` with a required reason, hidden unless the
  staff member's `can` list has the action.
- `components/testtrack-admin/labels.ts`: labels for the two audit actions.
- The ledger page needs nothing beyond the backend label.

## 10. Rollout

**Phase 1** (levels count, level tix, chips, sheet, bar)
1. Ship server S1 to S6 with `LEVEL_BONUS=false`. Levels and Ranks change (public reviews out),
   nothing pays.
2. Run the S6 preview on production; the owner approves the total.
3. Ship the app (A1 to A6) to internal, then production.
4. Once the app is on production, set `LEVEL_BONUS=true` and restart. Earlier, old apps would show
   a "+27" float with nothing explaining it.
5. Play "What's new": `Levels now count. Each new level adds tix. Tap your level at the top to see
   what it brings.`

**Phase 2** (about 4 weeks later, on data)
- Jury route (S7) if the juror pool or unjudged cases need it.

**Phase 3**: parked (seat priority, discount), see section 4.

No Remote Config: everything is server-decided and the app shows what `/me/level` sends. An older
server sends no level fields; they default to 1, so a new app on an old server shows no chips.

Metrics (admin overview plus ad hoc SQL):
- Level tix minted per week vs starter tix granted per week (expect about 540 a week per 100
  active members).
- Members per level.
- Crew nights completed per member and clean-run rate, before vs after.
- Private (closed) vs public Circle feedback volume, before vs after.
- Same-phone flags and `tix_watch` busy-day flags around switch-on day (backfill bonuses).
- Phase 2: juror pool size, cases settled with fewer than 3 votes.

## 11. Risks

- **Play policy:** handled by the exp rule in section 5. The remaining risk is the existing paid
  live reviews, which this plan does not change.
- **Existing users:** levels drop for members whose exp was mostly public reviews, and Ranks
  reshuffles once. Explained once in the sheet and in "What's new". Nobody loses tix.
- **Economy:** a new faucet of roughly 27 tix per typical member a month, at most 627 per account
  ever. Dials: `LEVEL_TIX_PER_LEVEL`, `LEVEL_TIX_LAST`, the switch. Backfill bounded to one level
  per member (at most 60).
- **Data loss:** none. Two nullable columns, the ledger only appended, a downgrade drops the
  columns.
- **Concurrency:** two `/me` reads at once are serialised by the row lock and the ledger's
  idempotency key; tested.
- **Bias:** owners may trust low levels less. A refusal still costs a stake and goes to jurors, who
  never see levels. Level 1 visibility is open (B2).
- **Account value:** any perk makes high-level accounts worth buying. That is why there is no
  approval shortcut, no vote weight, and the jury route needs a clean record.
- **GET /me writes:** bounded and idempotent, documented in the route docstring.
- **Permissions:** hold and release are ADMIN and never change a balance directly.
- **Two dots in the bar:** the update dot (right) and the level news dot (left, on the robot) share
  a colour. Each sits on the thing it belongs to, and the level dot clears on the first open.

## 12. Open questions for the owner

1. Level chip at level 1: hide (recommended) or show? Mockup in progress.
2. The balance now opens the You page, which shows no tix today. Add a Tix cell with the balance to
   the record grid (`ui/MeRecord.kt`) so the tap lands on it? Recommended, small; it would join A4.

## 13. Build breakdown (one piece per coder run)

Server pieces follow the backend CLAUDE.md: layers, files under 500 lines, `ruff`, `mypy
--strict`, route docstrings and response models, tests in `tests/services` (rules) and `tests/api`
(wire). The full suite needs Postgres and Redis (CI); locally run the pure tests.
App pieces: `scripts/check_app.py` passes (colours only from `Tt.colors`, no new baseline lines,
500-line limit), unit tests in `app/src/test/`, stateless bodies. No device runs.

### Server

**S1. Exp counts only verified work** (no dependencies)
- `repositories/standing.py`: `Review.is_live.is_(False)` in both review counts (Ranks follows);
  add `public_reviews()`. `rules.py`: `REVIEW_POINTS` comment; `LEVEL_TIX_PER_LEVEL = 3`,
  `LEVEL_TIX_LAST = 20`, `level_tix()`. New `services/levels.py` with `of()`.
  `services/standing.py`: `view()` adds `level`; `profile()` adds `level`, `public_reviews`.
  `schemas/standing.py`: the new fields. `services/admin_members.py:67` uses `levels.of`.
- Tests: an approved live review adds 0 exp and an approved closed one adds 5; the board orders by
  the new number; board rows carry the right level; `level_tix(1) == 0`, `level_tix(2) == 6`,
  `level_tix(20) == 60`, `level_tix(21) == 0`; the sum over 2 to 20 is 627; existing
  `test_levels.py` and `test_standing.py` pass (update any that counted live reviews, saying why).

**S2. Level tix, paid once** (after S1)
- Migration `0032_levels.py` (`level_paid`, `level_held_at`, both nullable) and
  `db/models/user.py`. `config.py` `level_bonus` and `.env.example`. `tix.py` `ONCE_PER_CAUSE`
  gets `"level"`. `admin_ledger.LABELS["level"]`. `levels.settle`, `levels.settle_due`.
  `api/v1/me.py` `read_me` calls settle and commits when it paid. `sweeper.py` `_levels` stage.
- Named tests: switch off pays nothing and leaves `level_paid` NULL; NULL at level 9 pays 27 once
  and sets 9; an account whose first settle is at level 1 then gets every level as reached; two
  settles at once pay once; held pays nothing; `phone_used` pays nothing and advances
  `level_paid`; level 21 pays nothing; balance equals ledger sum after settling; exp dropping and
  rising again pays nothing twice; migration up and down.

**S3. `GET /me/level`** (after S2)
- `LevelOut`, `PerkOut` (new `schemas/level.py`); `levels.card`; route in `api/v1/me.py`.
- Tests (`tests/api`): shape; `tix_blocked` is `""`, `"held"` or `"phone"` as expected;
  `next_tix` (level 7 member gets 24); perks empty in phase 1.

**S4. Levels on rows** (after S1)
- `services/views.py`: crew loop `owner_level` (one batched lookup); `waiting_view` and
  `my_post_view` `reviewer_level`. `schemas/crew.py`, `schemas/feed.py`. `views.py` stays under 500.
- Tests: crew view rows carry each owner's level; dispute and case payloads carry no level.

**S5. Admin level card and hold** (after S2)
- `admin_members._detail` level block; `levels.hold`; route in `api/v1/admin_members.py`;
  `roles._MAY`; audit sentence; `admin_overview` 30-day figure.
- Panel: `pages/Member.tsx` card and button, `labels.ts`.
- Tests: ADMIN required (maintainer refused); audit row written with the reason; release sets
  `level_paid` to the current level and pays nothing back; holding twice is harmless.

**S6. Backfill preview** (after S2)
- `scripts/level_preview.py`, read-only: members per level, and the total the first settle would
  pay (`level_tix(level)` summed over non-deleted, non-held, non-`phone_used` accounts whose
  `level_paid` is NULL). Calls services, writes nothing, never commits.

**S7. Jury route** (phase 2, after S1)
- `rules.JUROR_MIN_LEVEL`, `rules.JUROR_CLEAN_DAYS`; `levels.may_sit` (or a new
  `services/jury.py`); replace the two checks at `moderation.py:179-182` with one call; a repo read
  in `repositories/disputes.py` for `upheld` cases against a reviewer closed since a date; a perk
  row in `levels.card`.
- Tests: level 4 with 0 approved may judge; 2 approved at level 1 may judge; an upheld case 10
  days ago blocks and one 31 days ago does not; the age rule is unchanged; existing moderation
  tests pass.

### App

**A1. Wire** (after S1, S3, S4 are live; fields are defaulted, so order is not critical)
- Move the standing types to `net/StandingWire.kt`; add the fields; `net/LevelsWire.kt`;
  `Api.level()`.
- Unit tests: old payloads without level fields parse to 1; new payloads parse.

**A2. Level chip on rows** (after A1)
- `ui/LevelChip.kt`; Ranks rows and podium, crew member rows, owner review rows; models carry the
  level.
- Checks: nothing drawn at level 1 (drop this if the owner picks "show"); screen reader says
  "Level n"; no colour literal; files under 500.

**A3. App bar right side and loading fix** (no dependencies; needs no server change)
- `domain/Figures.kt` `grouped()`; `ui/TopHud.kt` `TixFigure` replaces `TixChip`, loading state,
  new `onOpenLevel`, `onOpenYou`, `levelNews` parameters (`onOpenLevel` null until A4 wires it);
  `ui/LevelRobotBadge.kt` arrow only when signed out, `news` dot; `ui/UpdateChip.kt` dot colour.
  `MainShell.kt` passes `onOpenYou = { show(MainTab.PROFILE) }` (no net growth; the `AppHeader`
  move in A4 shortens it).
- Unit tests: `grouped(0) == "0"`, `grouped(999) == "999"`, `grouped(12450, Locale.US) ==
  "12,450"`, `grouped(1240, Locale.GERMANY) == "1.240"`.
- Compose tests: signed in with level null shows no "Sign in" node, the robot has no click action,
  and the balance area shows the skeleton; signed in and loaded, tapping the balance calls
  `onOpenYou`; signed out still shows the Sign in pill and the robot still signs in.
- Checks: no `gold` in the bar; colours only from `Tt.colors`; large-font branch matches;
  `TopHud.kt` and `MainShell.kt` under 500.

**A4. Level sheet** (after A1, A3)
- `ui/LevelSheet.kt`, `viewmodel/LevelViewModel.kt`, `ui/AppHeader.kt` (hosts `TopHud` and the
  sheet; `MainShell.kt` lines 251-262 become one `AppHeader(...)` call), `PickerSheet` frame,
  skeleton loading, copy from section 8. Opened from the robot and level lines in the bar and from
  the You page's level cell (`ui/MeRecord.kt`, `ui/MeScreen.kt`). If open question 2 is accepted,
  the Tix cell joins here.
- UI test of `LevelSheetBody` in four states: level 1 new member, mid-level with news, level 20 and
  above, blocked. Compose test: tapping the bar's level row opens the sheet; it is not tappable
  while loading or signed out.

**A5. Level news** (after A3, A4)
- `domain/LevelNews.kt` pure rule (same account, higher level, cached null, account switch) with
  unit tests; `data/LevelNews.kt` prefs per account; `HudViewModel` records it and feeds
  `levelNews` to the robot dot; the sheet clears it; the one-time intro per account.

**A6. Ranks wording** (after S1 and A2; both touch `ui/RanksScreen.kt`)
- `work()` and the points label read as exp; one header line: 1 exp per app opened, 5 per accepted
  private feedback.

**Order:** S1, then S2 and S4 in parallel, then S3, S5, S6. App: A1 and A3 in parallel, then A2 and
A4, then A5 and A6. Then switch on. Phase 2 (S7) later, on data.
