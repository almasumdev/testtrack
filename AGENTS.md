# AGENTS.md — TestTrack (Android app)

Handoff for a coding agent picking this repo up. Read this first, then `docs/app-rules.md` and
`docs/architecture.md`. Last updated 2026-10-03.

## What this is

TestTrack (`com.eazyverse.testtrack`) is a native Android app (Kotlin, Jetpack Compose) for new
Play developers who need 12 testers for 14 days. Five tabs in a floating dock:

| Tab | File | What it does |
| --- | --- | --- |
| Circle | `ui/FeedScreen.kt`, `FeedRows.kt`, `WaitingScreen.kt` | Feed of review requests (tix bounties), Activity, Waiting (approve/reject, jury) |
| Crew | `ui/CrewScreen.kt`, `CrewTabs.kt`, `CrewPhases.kt` | 15-seat crews that run 14 days; Open / Lobby / Run / Record |
| Ranks | `ui/RanksScreen.kt`, `RanksPodium.kt` | Leaderboard: podium + standings tiles + pinned "you" row |
| My apps | `ui/AppsScreen.kt` | Your apps: running ones as progress cards, finished as tiles |
| Profile | `ui/MeScreen.kt`, `MeRecord.kt` | Identity, record grid, account actions |

The shell is `ui/MainShell.kt` (header `TopHud`, the dock, the contour backdrop). Routes and
pushed screens are in `MainActivity.kt` (`AppNav`).

- Backend: `H:\_group_projects\test_track_backend` (FastAPI + Postgres + Redis, `/api/v1`, base
  `https://testtrack.eazyverse.com`). Has its own `CLAUDE.md`. Firebase (project `test-track-ev`)
  is only sign-in, push, Crashlytics, Analytics and the `config/app` update document.
- About 7,900 lines of the old Firestore-era app still ship (Home, Group, Dashboard, Submit,
  Setup, CaptureService, reminders). Nothing in the dock reaches them except Setup and a legacy
  reminder. Do not restyle them unless asked.
- Release tooling is the shared mkit engine at `H:\tools\mkit` (`mkit.toml` here).

## Rules the user has set (follow them)

- **Commit finished work without asking.** Small, focused commits.
- **Never push, deploy, release, or touch Play Console / Firebase config unless the user asks for
  that exact action.** `mkit deploy` is the only release path; it is never run unprompted.
- **The user runs the device tests.** After a change: run the checks below, build, and look at it
  on the emulator. Do not run the instrumented suite, and do not install or tap on the user's
  phone.
- Keep replies short. The user writes tersely and judges by screenshots; show, don't describe.
- When the user rejects a design twice, stop guessing: offer 2–3 concrete options (a mock page
  works well) and build the one they pick.

## Devices

`adb devices` shows two:

- `emulator-5554` — yours to use. Install with `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`,
  then `adb -s emulator-5554 shell am start -n com.eazyverse.testtrack.debug/com.eazyverse.testtrack.MainActivity`.
  Screenshot: `adb -s emulator-5554 exec-out screencap -p > shot.png`.
- `R58X80C1M6P` — **the user's phone. Never touch it.** Never run `./gradlew installDebug`
  (it installs on every attached device).

On the emulator: check the app is in front before every tap
(`dumpsys activity activities | grep topResumedActivity`). Dock tab positions move because the
selected tab is wider. Dark mode: `adb -s emulator-5554 shell cmd uimode night yes|no`; font scale:
`settings put system font_scale 1.3`. Put both back when done. Debug builds pad the Ranks board
with fake people (`SampleBoard`, debug only) so every state has data.

## Checks before every commit

```
python scripts/check_app.py                     # architecture ratchet; must say "no new violations"
./gradlew ktlintFormat                          # then:
./gradlew ktlintCheck assembleDebug compileDebugAndroidTestKotlin
```

- `check_app.py` is a ratchet against `scripts/app-baseline.txt` (40 known violations). It fails on
  any **new** one: a file over **500 lines**, `ui/` importing `data` or `net`, or a **colour literal
  outside `ui/theme/`**. `--update` only ever shrinks the baseline and refuses to add; if a file
  grows past 500, split it (see how `MeRecord.kt` was split out of `MeScreen.kt`).
- Don't pipe `check_app.py` into `tail` inside an `&&` chain; that hides its exit code. One commit
  (`4ccfa63`) slipped through that way.
- `ktlintFormat` removes unused imports; after it, a missing import shows up as a compile error.
- The IDE's diagnostics in this repo are stale and noisy. Trust Gradle.
- Instrumented tests in `app/src/androidTest` assert visible strings. When UI copy changes, update
  them in the same commit; `compileDebugAndroidTestKotlin` proves they still compile, and the user
  runs them.

## Design system (done this session; keep to it)

The app was moved to **Value Guard's look** (`H:\flutter\onGoing\value_guard`), its **Ocean blue**
palette, light and dark following the phone. Value Guard, ListKin and TestTrack share this look;
mkit's `docs/app-ui.md` Part 2 describes the family.

- **Colours live in one file:** `ui/theme/Tokens.kt` (`TtTokens`, `OceanLight`, `OceanDark`), read
  as `Tt.colors.<role>` (`bg`, `text`..`text4`, `glass`, `glass2`, `glassBorder`, `hair`,
  `accent` (fills), `accentInk` (accent as text/icon), `tile`/`tileInk`, `container`,
  `ok`/`warn`/`bad` + `*Bg`, `gold`/`silver`/`bronze`, `nameTile(hue)`...).
  `ui/theme/Theme.kt` only maps them into Material's scheme and `Status`. No `Color(0x..)`,
  `Color.White`, `Color.hsl` anywhere else; the check fails on it.
- **Accent rule:** `accent` is a fill (main button, filled meter, selected dock tab). Text and icons
  use `accentInk`. In dark they differ a lot.
- **Type:** `ui/theme/Type.kt`. Barlow for everything; `TextStyle.tabular()` = IBM Plex Mono with
  tabular digits, only for bare figures (places, points, days). Words stay Barlow. Plex Mono at
  large sizes looked like code to the user; big stat numbers use Barlow + `fontFeatureSettings = "tnum"`.
- **Shared pieces** in `ui/Garden.kt`:
  - `Backdrop(scene)` — the contour ground (glows + wobbly ring "hills", one layout per tab).
  - `IconTile`, `CountBadge`, `Panel` (dashed read-only panel), `TileGroup`/`TileRow` (settings
    rows), `ListTile(index, count)` (any list on the page ground becomes tiles 3dp apart, ends
    rounded), `CrewHeading` (sentence-case section heading, numeric trailing → badge), `Tally`.
  - `LocalDockRoom` / `dockRoom()` — height the floating dock covers. Every tab list adds it to
    its bottom padding.
- Cards: `CrewCard`/`FeedCard`, 18dp radius, glass + 1dp `glassBorder` (`CardRadius`).
  Buttons: `Primary` = 52/48dp accent pill; `Secondary` = soft `glass2` pill (red ground when
  destructive), never outlined. `Ask` dialogs: 26dp glass with pill buttons.
- **Insets:** edge-to-edge, both system bars clear (no contrast scrim). The tab shell takes only
  the top inset; the dock floats over content and the page runs under the nav bar. Pushed screens
  get the full safe-drawing inset via `Inset { }` in `MainActivity.kt`.

### Taste the user has expressed (don't repeat these mistakes)

- No card inside a card. No dashed panel stacked on a dashed panel.
- Ranks podium stands plainly on the page (no panel, no gold glow).
- Text should not sit over contour lines: lists go on tiles.
- Profile = face left + name/email column, a compact chip row under it (join month, local time),
  then a **2-column grid of six stat cards** (icon tile left, number + label right; Rank card gold
  and opens Ranks), then the Account tile group, then `v1.5.11`. Big hero numbers, floating
  captions and tall empty tiles were all rejected.

## What's open

From the readiness audit (2026-10-01). Nothing here has been started unless noted.

**App, release blockers**
1. Circle → Activity shows invented events in release (`FeedViewModel.kt` uses `SampleFeed`).
   Needs a backend feed or the tab hidden — user hasn't decided.
2. No in-app account deletion. Profile now links to `https://eazyverse.com/apps/testtrack/delete-account`
   (which says there is no in-app delete yet); a real delete needs a backend endpoint.
3. Privacy policy (`eazyverse` repo, `content/apps/testtrack/privacy.md`) still describes the
   Firestore-only app; Play Data safety needs the same update.
4. The optional-update icon lives only on `HomeScreen`, which the tab shell never shows, so
   optional updates are invisible (forced ones work).
5. Version is still 1.5.11 (63); bump name and code before the next release. The OkHttp +
   kotlinx.serialization client has never shipped in an R8 release build: test one.
6. Profile pictures never render: the backend stores an upload id in `photo_url`, but `Avatar`
   only decodes the old base64 Firestore photos. Needs the backend to serve the image and the app
   to load it with Coil (already a dependency).

**Backend blockers** (in `test_track_backend`): no scheduler runs `moderation.sweep` /
`crews.close_finished`; claimed reviews never expire; the Play worker can stay blocked across
restarts and a sign-in console left open locks it; no database/uploads backups.

**Decisions waiting on the user:** Activity feed (build vs hide), one crew at a time vs several,
remove the old Firestore half or keep its permission declarations, where backups go.

**Other:** store screenshots and feature graphic still show the old dark app (`mkit art`);
`docs/app-rules.md` says CI runs the checks but the workflows were removed.

## Style of the code and commits

- Comments explain **why**, in plain prose, often at length (KDoc above composables). Match that
  density and tone; don't strip existing ones.
- Commit subject: one plain-English sentence in the imperative, no prefix (e.g. "Show the profile
  record as a grid of six stat cards"), body says what changed and why.
- Line endings: the repo is CRLF on checkout; Git warns "LF will be replaced by CRLF" on files you
  write — harmless.
