# Crew restyle to the Circle look (visual only)

Owner ask 2026-11-12: "now Crew improve same way, the main page and sub tabs also". Circle reference: ui/TicketCard.kt, TicketStub.kt, FeedRows.kt, FeedMine.kt, WaitingScreen.kt, SegmentedTabs.kt.

Decisions by the head:
- NO floating bars on Crew (plan steps 2.3 and 2.4 are dropped). Purely visual.
- My apps cards (AppCard on CrewCard) will follow CrewCard to the hairline look. Accepted.

Rules for every piece: colours only from Tt.colors; files under 500 lines; TextStyles set fontFamily = Barlow or copy a MaterialTheme style; no em dashes; no copy, data or action changes; don't commit; no device. While pieces 2-4 run in parallel, run only `python scripts/check_app.py` (a mid-edit Gradle compile breaks for the others); the head compiles once at the end.

## Map: today and the change

| Where | Built from | Today | Change |
|---|---|---|---|
| Every card (hero, guide, pictures due, tools, Play feedback, tracking grid, tester nights) | `CrewCard` (CrewRows.kt:27) | glass fill, glassBorder, 18dp | bg, cardLine, 14dp (P1) |
| `CrewHero` | CrewCard + StatusChip + meter + Primary | glass card | inherits; chip, meter, button stay |
| `StatTiles` (CrewPageParts:242) | three boxes | glass2 fill, 14dp | no fill, 1dp cardLine, 10dp (P3) |
| `TintBox` | box | glass2 fill | no fill, 1dp cardLine, 10dp (P3) |
| `Quotes` | box | tone.soft() fill | no fill, 1dp tone.ink().copy(alpha = 0.42f) edge, 10dp (P3) |
| `YourAppCard` | CrewCard, 48dp icon | glass | 52dp icon, name up to 2 lines, status text3 (P3) |
| Tab rows Run/Lobby/Record (`CrewSummaryRow`, CrewTabs:390) in ListTile | 40dp mark on tone.soft(), 3dp grouped tiles | glass, tinted mark | HairlineRow, 52dp mark on neutral t.tile with the number in markTone.ink(), 8dp apart (P2) |
| `CrewPageRow` | 40dp icon, grouped tiles | glass | HairlineRow, 52dp AppMark, 8dp gaps (P3) |
| `MemberRow` | 38dp Initial | glass | HairlineRow, 52dp Initial (P4) |
| `ListTile` (Garden.kt:295) | | glass, grouped corners | bg, cardLine, 14dp all corners, 8dp gap (P1) |
| `CrewRulesCard` band | verticalGradient(tile to glass) + overlapUp(30) | gradient | drop gradient and overlap (P2) |
| Rules `Figure`, `TixCard` | field fill, glassBorder | filled | no fill, cardLine, 12dp (P2) |
| `DayStrip` box | glass2 fill | filled | no fill, cardLine, 12dp; empty cells field to Color.Transparent keeping glassBorder edge (P2) |
| `IconSquare` | tone.soft() fill | wash | unfilled uses t.tile, glyph stays tone.ink(); filled tick unchanged (P2) |
| Meter empty segments (SegmentMeter callers, DayMeter, SeatMeter) | glass2 / surfaceVariant | grey | Tt.colors.track (P2, P3, P4 in their own files) |
| `MembersButton` | pill | glass, glassBorder | bg, cardLine (P3) |
| `RowPill` GHOST | | glass fill | bg fill, border unchanged (P3) |
| `SheetOption` (row sheet) | 18dp, glass, icon on tone.soft() | | no fill, cardLine, 14dp; icon box t.tile, glyph tone.ink() (P3) |
| Phase tabs | SegmentedTabs | already Circle | no change |
| StatusChip, QuietMark, buttons, tracking grid cells, pictures Mark cells, SeatCountTiles | | | no change (colour is meaning) |

## Piece 1: shared foundation (first)
Files: ui/Garden.kt, ui/CrewRows.kt, ui/TicketCard.kt
1. Garden.kt: add `/** The Circle tile's corner: tickets, the ask row, every hairline tile. */ val TileRadius = 14.dp` next to CardRadius.
2. Garden.kt `ListTile`: keep signature (index, count, content). Shape RoundedCornerShape(TileRadius) for every row (drop the place calc); top padding when index > 0 from 3dp to 8dp; fill glass to bg; border glassBorder to cardLine; update KDoc. Leave TilePlace.shape() (settings TileRow uses it).
3. CrewRows.kt `CrewCard`: fill Tt.colors.bg, border Tt.colors.cardLine, shape RoundedCornerShape(TileRadius) for clip and border.
4. TicketCard.kt: add exactly:
```kotlin
@Composable
internal fun HairlineRow(
    leading: @Composable () -> Unit,
    title: String,
    note: AnnotatedString,
    modifier: Modifier = Modifier,
    noteTone: Color = Tt.colors.text3,
    titleTone: Color = Tt.colors.text,
    titleMark: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
)
```
Row: modifier.fillMaxWidth().heightIn(min = 76.dp).clip(shape).background(t.bg).border(1.dp, t.cardLine, shape), clickable only if onClick != null, then padding(start 12, end 8, top 12, bottom 12); CenterVertically; shape = RoundedCornerShape(TileRadius). Content: leading(), Spacer(12.dp), Column(Modifier.weight(1f)) { Row(CenterVertically) { Text(title, Modifier.weight(1f, fill = false), style = TicketTitle, fontWeight = SemiBold, color = titleTone, maxLines = 2, overflow = Ellipsis); titleMark?.invoke() }; Text(note, style = TicketNote, color = noteTone, maxLines = 1, overflow = Ellipsis) }, Spacer(8.dp), trailing(). KDoc: "A Circle row with no stub, for rows that carry no tix."
Verify: check_app.py, `./gradlew assembleDebug ktlintCheck compileDebugUnitTestKotlin --offline`; Circle unchanged.

## Piece 2: Crew main page and its tabs (after P1)
Files: ui/CrewTabs.kt, ui/CrewLoading.kt, ui/CrewOpenTab.kt, ui/CrewOpenGuide.kt, ui/CrewOpenRules.kt, ui/CrewSeats.kt
1. CrewTabs `CrewSummaryRow` on HairlineRow: leading = private CrewMark(mark, markTone): Box(size 52.dp, clip RoundedCornerShape(16.dp), background Tt.colors.tile) with Text(mark, style = TextStyle(fontFamily = Barlow, fontSize = 20.sp, lineHeight = 22.sp, fontFeatureSettings = "tnum"), SemiBold, color = markTone.ink()). note = AnnotatedString(line), noteTone = lineTone?.ink() ?: text3, onClick = onOpen, trailing = { StatusChip(chip, chipTone); chevron 18dp, start padding 2dp, text4 }. Keep params and when blocks.
2. Run/Lobby/Record lists: replace ListTile(index, n) { ... } with Box(Modifier.animateItem().padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)) { row }. Run contentPadding top 12.dp. Lobby/Record top = HeadedListTop with first CrewHeading(..., top = 0.dp). Run: split first item into item("hero") CrewHero and item("pictures") PicturesDueCard, each in Box(padding(horizontal = Gutter)). Run meter glass2 to track.
3. (dropped: no floating bars)
4. CrewOpenTab: list top 12.dp. No floating bar.
5. CrewLoading: CrewRowLoading heightIn(min 76.dp), padding start 12, end 8, vertical 12, 52dp skeleton 16dp corner. Run loading top 12. Record loading padding(top = HeadedListTop) + CrewHeading("Finished", top = 0.dp). Lobby loading top 12. CrewOpenCardLoading bottom padding 12 to 0.
6. CrewOpenGuide: GuideCard top 12 to 8. IconSquare unfilled background tone.soft() to Tt.colors.tile; glyph tone.ink().
7. CrewOpenRules: head column remove background(Brush...), bottom padding 44 to 4; remove overlapUp(30) and delete overlapUp + now-unused imports; Figure: drop field background, border cardLine, 16 to 12dp; TixCard same, 14 to 12dp; DayStrip outer: drop glass2, add border(1.dp, cardLine, RoundedCornerShape(12.dp)); empty cells fill ?: Color.Transparent keeping glassBorder edge.
8. CrewSeats SeatMeter: surfaceVariant to Tt.colors.track. Leave SeatCountTiles, EmptySeat.

## Piece 3: a crew's own page (after P1)
Files: ui/CrewPageParts.kt, ui/CrewPageRows.kt, ui/CrewPhases.kt, ui/CrewRunCards.kt, ui/CrewPicturesDue.kt, ui/CrewRowSheet.kt (CrewRunHero.kt, CrewLeftRun.kt: check only)
1. CrewPageParts: StatTiles, TintBox, Quotes, YourAppCard, MembersButton, DayMeter per the table. YourAppCard title bodyLarge.copy(fontSize = 16.sp, lineHeight = 19.sp), SemiBold, maxLines 2, Ellipsis. DayMeter only else -> Tt.colors.track.
2. CrewPageRows `CrewPageRow` on HairlineRow. Keep `place: TilePlace` with @Suppress("UNUSED_PARAMETER") (androidTest CrewRowsTest, CrewRunCardsTest pass it). leading = { Box(if (out) Modifier.alpha(0.5f) else Modifier) { AppMark(packageName, title, icon, size = 52.dp) } }; titleTone = if (out) text3 else text; titleMark = if (mark > 0) {{ QuietMark(mark) }} else null; onClick = if (out) null else onOpen; trailing = { trailing(); if (!out) chevron }. RowPill GHOST fill to Tt.colors.bg.
3. CrewPhases: rows() gap bottom = if (index < list.size - 1) 8.dp else 0.dp. TestersCard meter glass2 to track. Leave ListTop = 8.dp.
4. CrewRunCards (both cards) and CrewPicturesDue: padding(top = 10.dp) to 8.dp.
5. CrewRowSheet SheetOption per table.

## Piece 4: members and every-night pages (after P1)
Files: ui/CrewMembersPage.kt, ui/CrewPicturesPage.kt (CrewTrackingGrid.kt check only)
1. MemberRow on HairlineRow: leading = Initial(letter, size = 52.dp, letterStyle = MaterialTheme.typography.titleMedium); note = AnnotatedString(line); no onClick; trailing = PictureThumb(..., 40.dp) or StatusChip, same branch as today. Remove the private place param and placeOf call.
2. Row gaps 3dp to 8dp.
3. Members hero meter glass2 to track; "See every night" top padding 10 to 8.
4. CrewPicturesPage spacedBy(8.dp) (was 3). Cells untouched.

## Risks
- Colour with meaning stays: lit fills in DayMeter, SegmentMeter, SeatMeter, DayStrip cells, TrackingGrid.Cell, pictures Mark cells. Only empty tracks change (glass2 to track).
- Washes removed keep meaning: summary mark keeps tone in the number ink and chip; Quotes keeps it as an edge; IconSquare on the glyph.
- My apps AppCard follows CrewCard.
- Light cardLine on bg is faint on big hero cards (accepted on Circle).
