# TestTrack UI guide

The app is a checklist people work through every day for a fortnight. It should feel like a tool
they already know by day three: the same shapes in the same places, and nothing decorative
competing with the one thing they came to do.

Everything below is enforced by shared composables in
[`ui/Common.kt`](../app/src/main/java/com/eazyverse/testtrack/ui/Common.kt) and the palette in
[`ui/theme/Theme.kt`](../app/src/main/java/com/eazyverse/testtrack/ui/theme/Theme.kt). Reach for
those before writing a new variant.

---

## Colour

**One accent for things you can act on. A separate set for things that happened.**

| Token | Light | Dark | Used for |
|---|---|---|---|
| `primary` — iris | `#4F46E5` | `#8B87F5` | buttons, links, focus, the progress ring and meter |
| `Status.posted` | `#059669` | `#34D399` | a day banked |
| `Status.short` | `#C2670A` | `#FBBF24` | reported, but under thirty seconds |
| `Status.missed` | `#E11D48` | `#FB7185` | missed, not installed, withdraw |
| `Status.upcoming` | `#E3E7F0` | `#212940` | days still to come, meter tracks |
| `background` — paper | `#F6F7FB` | `#080C16` | the ground under everything |
| `onSurface` — ink | `#0B1020` | `#EEF1F8` | titles and values |

**TestTrack ships dark.** `ALWAYS_DARK` in `Theme.kt`. A committed look beats one that changes
with the phone: the palette was chosen and checked against a dark ground, and a tool people open at
the same time every evening for a fortnight should look the same every time they open it. The light
scheme above is complete and correct — pass `darkTheme = isSystemInDarkTheme()` to follow the
system instead.

**Dynamic colour was removed deliberately.** It made the app look different on every phone and
identical to every other Material app on each of them — and it painted the primary action and a
completed day the same hue, so a control and a result were indistinguishable.

Iris sits outside the status range on purpose. Green already means *done* in every grid cell, so it
cannot also mean *press me*.

The only raw hex outside the theme is the four-colour Google mark on the sign-in screen, which is
Google's artwork and cannot be recoloured.

## Surfaces

Rows live in a **`Panel`** — a rounded 18dp `surface` block on the `background` ground, one per
section, with a `SectionLabel` above it.

**Rows inside a panel are not divided.** A rule between every one of fourteen apps is fourteen
lines competing with the content they are meant to organise; the panel edge does that job once and
row padding does the rest. `Hairline()` still exists for the rare boundary that means something.

No elevation anywhere. The panel is a lighter plane, not a floating card.

## Empty states

`EmptyState(icon, title, subtitle)` — a tinted disc, a title, and a line explaining what has to
happen next. A bare sentence in the middle of a screen reads like a failure; this reads like a
state the product expected. `Blank(text)` remains for places where a full empty state would be
too much.

**Nothing-to-do is not one state.** A group with no outstanding apps might be finished for the day,
or might be a cohort none of whose apps are installed, or a run that has ended — and only the first
is good news. Each gets its own line and its own colour. A screen that reports all three as *done*,
in green, is one the tester learns to distrust.

## Bars and insets

Handled once, centrally. No screen touches window flags or adds its own system-bar padding.

- [`MainActivity`](../app/src/main/java/com/eazyverse/testtrack/MainActivity.kt) wraps the nav host
  in `safeDrawingPadding()`. Android 15 forces edge-to-edge, so without this every screen starts
  underneath the status bar.
- `TestTrackTheme` sets `isAppearanceLightStatusBars` and `isAppearanceLightNavigationBars`. The
  bars are transparent and show our own background through them, so their icons are ours to darken.
- Every `TopAppBar` uses `containerColor = background`, so the bar is not a separate plane.

Back arrow on every pushed screen, **at most two actions**, and the title is the thing you are
looking at — the group's name, the app's name — not the feature's.

## Spacing and type

`Gutter` = **20dp**, every screen, every row. `RowInset` = **68dp**, where a row's divider starts —
past the icon, so a column reads as a list rather than a stack. Sections 22dp apart, rows 10–14dp
vertical, everything else on an 8dp rhythm.

| Role | Used for |
|---|---|
| `displaySmall` bold | the one number a screen exists to show — `Day 4`, `7 of 13` |
| `titleMedium` | screen titles, secondary figures |
| `titleSmall` semibold | a row's primary line |
| `bodyMedium` | supporting copy |
| `bodySmall` | a row's second line |
| `labelSmall` tracked | section labels, pills, legends |

Supporting copy is always `onSurfaceVariant`; primary copy always `onSurface`. Text wears a status
colour only when the status *is* the message — "Not installed", "Short 1 member".

## Destructive actions

Anything that throws work away confirms first, and the dialog says what it costs. Sign-out is the
one that reads harmless and isn't: it clears the Firebase session and the setup flags, so it asks,
and it says plainly that reported proof survives.

Everything else — a round in progress, screen sharing — gets a plain stop control rather than a
dialog, because stopping those loses nothing.

## Rows

`AppIcon` (44dp) · title · one state line · a trailing affordance.

The icon is the real launcher icon, pulled from the package manager and cached — the single
biggest thing separating a list of apps from a list of strings, and it costs nothing since the
launcher `<queries>` filter is already declared for the install streak. An app with a launcher icon
to draw is by definition one that filter can see. Apps not on the phone get a lettered tile.

Trailing is one of: an **Open** button, a `Pill` (`Done` / `Install` / `Waiting`), a chevron for
navigation, or a `MoreVert` menu. Never two.

## Loading

**Skeletons, not spinners.** `SkeletonRows(n)` renders placeholders in the shape of the list that
is arriving, with a shimmer that respects the frame budget and stops for reduced motion.

**One skeleton for the page, never one per section.** Sections do not land together, and whichever
arrives first gets drawn over the ones that have not — always stating the confident thing. An empty
worklist means nothing is outstanding, so a group page whose apps are still in flight reads
"Everything's done for today", in green, and a second later becomes "Start testing · 5 left". The
header was not wrong; it was early. Every screen therefore carries a single `ready` flag, set only
when every piece it shows is in hand, and renders `SkeletonPage(...)` until then — app-bar title
included, since a placeholder name pops the same way. `ready` is sticky: a refresh over content
already on screen must not blank it.

The skeleton stands in for **content, not structure** — no section labels, no headings. A label is
a claim that the section exists, and some of them will not.

Same rule downstream: an empty state is a statement about the world, so it may only be shown once
`ready`. It must never be a step on the way to content.

**And you rarely see any of it**, because [`Cache`](../app/src/main/java/com/eazyverse/testtrack/data/Cache.kt)
holds the last known value for every read. A ViewModel is rebuilt on every navigation, so without
it, stepping into a group and back would spin over content the app already had. Screens read the
cache synchronously, render it, and refresh underneath — the skeleton is only ever seen on
genuinely first sight. A **partial** cache hit does not count: `showCached` returns early unless
every piece is present, because half a group on screen is not a faster group, it is a wrong one.
`Cache.clear()` on sign-out, so the next account never sees the last one's groups.

A spinner survives in exactly two places, both of which are genuine waits with no prior state: the
upload after a round, and a visit in progress.

## Offering actions

**One control at a time, in the order the obstacles clear.** The group screen's `Action` picks
between: round in progress → *Stop*; uploading → progress; no usage access → *Open usage access*;
cannot switch apps → *Allow TestTrack to switch apps*; nothing outstanding but apps still owed →
"none of them are installed on this phone"; nothing left at all → "Everything's done for today";
otherwise → **Start testing · N left**.

That second-to-last branch exists because a round can only open apps that are on the phone, so a
cohort with none installed also has nothing outstanding. Reporting that as *done* — in green, over
a header reading "0 of 11 done" — is the screen contradicting itself on the same scroll.

A button that cannot succeed is worse than no button.

## Visualisation

**A count is not a chart.** "7 of 13" is stated at `displaySmall` with a `Meter` underneath so
*nearly there* and *badly behind* separate at a glance. The bar supports the number; it does not
carry it.

**The 14-day grid is a status heatmap** — testers down the side, days across. State never rests on
hue alone, because a third of a cohort of fourteen may not see the difference:

| State | Shape |
|---|---|
| reported | solid fill |
| under 30s | outline only, hollow |
| missed | tinted, struck through with a diagonal |
| to come | faint fill, no stroke |

A legend is always present and shows the shape beside the word. Every cell carries a
`contentDescription` (`"asif, day 4, missed"`), and tapping one with a proof opens it. The *Every
tester* list below the grid states the same information in words, which is the table view.

`Ring` on a group row and `Meter` in a header are both iris — progress is something you act on, not
a state that happened to you. All of it is built from `Box` and `Canvas` off the tokens, so light
and dark need no second definition.

## Writing

Sentences, not labels. Say what will happen and what it costs:

> Each app opens for just over half a minute and moves to the next on its own. The screenshot lands
> at a moment you won't know in advance, so leave the phone alone until you're back here.

Never blame the tester for a platform constraint, and never hide one. When our day count and Play
Console's can disagree, the group screen says so.
