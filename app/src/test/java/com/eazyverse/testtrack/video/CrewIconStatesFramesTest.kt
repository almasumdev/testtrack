package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.Tt
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Crew list tiles (Lobby and Run) and every picture their app-icon strips can draw for one
 * app, as frames from the real composables. Writes media/public/frames/crew:
 * listTilesLobby, listTilesRun, iconStates + iconStatesDark (Lobby strip), iconStatesRun +
 * iconStatesRunDark (Run strip and the tile footer).
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewIconStatesFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val frame = Frame(compose, "crew")

    private val mineListing = "Bluefy" to "com.eazyverse.bluefy"
    private val listings =
        listOf(
            "Auto Silent" to "com.eazyverse.soundscheduler",
            "Astra Match" to "com.shahadat.astramatch",
            "Baka Run" to "com.markuljdev.bakarun",
            "Cherish" to "app.cherish.people",
            "EditMe" to "com.arisoftware.editme",
            "Gaan" to "com.arisoftware.gaan",
        )
    private val letterNames =
        listOf(
            "Pocket Recipes",
            "Steps Daily",
            "Tiny Tasks",
            "Lingo Cards",
            "Pixel Weather",
            "Habit Garden",
            "Lift Log",
            "Sleep Sounds",
            "Mood Notes",
            "Budget Jar",
            "Plant Pal",
            "Quiet Timer",
            "Word Trail",
            "Night Sky",
            "Coin Tally",
        )

    @Before fun onlyWhenAsked() = assumeTrue("set -PvideoFrames", videoFramesOn)

    private fun art(pkg: String) = "https://icons.testtrack.invalid/circle_actual_${pkg.replace('.', '_')}.png"

    /** A seat with real artwork (or none, for a letter tile). The reader's own is the Bluefy one. */
    private fun seat(
        i: Int,
        state: SeatState = SeatState.CONFIRMED,
        mine: Boolean = false,
        icon: Boolean = true,
    ): CrewSeat {
        val (name, pkg) = if (mine) mineListing else listings[i % listings.size]
        return CrewSeat(
            "seat-$i",
            name,
            if (mine) "You" else "Tester $i",
            pkg,
            mine = mine,
            icon = if (icon) art(pkg) else "",
            state = state,
            yes = if (state == SeatState.CHECKING) 1 else 0,
        )
    }

    private fun letterSeat(i: Int) = CrewSeat("seat-$i", letterNames[i % letterNames.size], "Tester $i", "demo.letter$i")

    private fun lobby(
        filled: Int,
        taken: Int,
        seats: List<CrewSeat>,
        name: String = "Crew 59",
        filling: Boolean = true,
        startsIn: Long = 0L,
    ) = Crew("lobby-$name", name, CrewPhase.LOBBY, filling = filling, filled = filled, taken = taken, seats = seats, startsIn = startsIn)

    private fun runApp(
        i: Int,
        done: Boolean = false,
        reported: Boolean = false,
        letter: Boolean = false,
    ): CrewApp {
        val (name, pkg) = if (letter) letterNames[i % letterNames.size] to "demo.run$i" else listings[i % listings.size]
        return CrewApp("app$i", name, pkg, "Tester $i", done = done, reported = reported)
    }

    private fun runCrew(
        apps: List<CrewApp>,
        name: String = "Crew 58",
        openedMine: Int = 0,
        withSeats: Boolean = true,
    ): Crew {
        val seats =
            if (withSeats) {
                apps.map { CrewSeat(it.id, it.label, it.owner, it.packageName, icon = art(it.packageName)) }
            } else {
                emptyList()
            }
        val members =
            listOf(CrewMember("me", "You", "Bluefy", day = 5, mine = true)) +
                List(3) { CrewMember("t$it", "Tester $it", "App $it", day = 5, openedMineToday = it < openedMine) }
        return Crew(
            "run-$name",
            name,
            CrewPhase.RUN,
            apps = apps,
            seats = seats + seat(99, mine = true),
            members = members,
            day = 5,
        )
    }

    // ---- list tiles -----------------------------------------------------------------------

    @Composable
    private fun Caption(text: String) = Text(text, fontSize = 12.sp, color = Tt.colors.text3)

    @Test fun lobbyListTiles() {
        val heldSeats = List(5) { seat(it, mine = it == 0) }
        val checkingSeats =
            List(4) { seat(it + 1) } + seat(0, SeatState.CHECKING, mine = true)
        val fullSeats = List(15) { seat(it, mine = it == 0) }
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = false) {
                    Column(
                        Modifier.fillMaxSize().background(Tt.colors.bg).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Caption("Lobby, still filling, my seat held")
                        CrewLobbyRow(lobby(5, 5, heldSeats)) {}
                        Spacer(Modifier.height(6.dp))
                        Caption("Lobby, my app is being checked")
                        CrewLobbyRow(lobby(4, 5, checkingSeats, "Crew 60")) {}
                        Spacer(Modifier.height(6.dp))
                        Caption("Lobby, full and starting")
                        CrewLobbyRow(lobby(15, 15, fullSeats, "Crew 61", filling = false, startsIn = 3_600_000L)) {}
                    }
                }
            }
        }
        frame.settle(800)
        frame.write("listTilesLobby")
    }

    @Test fun runListTiles() {
        val none = runCrew(List(14) { runApp(it) }, "Crew 58", openedMine = 0)
        val some = runCrew(List(14) { runApp(it, done = it < 6, reported = it == 6) }, "Crew 62", openedMine = 2)
        val all = runCrew(List(14) { runApp(it, done = true) }, "Crew 63", openedMine = 3)
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = false) {
                    Column(
                        Modifier.fillMaxSize().background(Tt.colors.bg).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Caption("Run, nothing opened yet tonight")
                        CrewListRow(none) {}
                        Spacer(Modifier.height(6.dp))
                        Caption("Run, 6 opened and 1 reported")
                        CrewListRow(some) {}
                        Spacer(Modifier.height(6.dp))
                        Caption("Run, all opened")
                        CrewListRow(all) {}
                    }
                }
            }
        }
        frame.settle(800)
        frame.write("listTilesRun")
    }

    // ---- icon states ----------------------------------------------------------------------

    private class StateRow(
        val title: String,
        val condition: String,
        val count: Int,
        val fixedSide: Dp? = null,
        val strip: @Composable () -> Unit,
    )

    private class Section(
        val header: String,
        val rows: List<StateRow>,
    )

    private fun lobbyRow(
        title: String,
        condition: String,
        crew: Crew,
    ) = StateRow(title, condition, CrewRules.SEATS) { CrewLobbyAppIcons(crew) }

    private fun runRow(
        title: String,
        condition: String,
        crew: Crew,
    ) = StateRow(title, condition, crew.apps.size) { CrewRunAppIcons(crew) }

    private val lobbySection
        get() =
            Section(
                "Lobby strip: CrewLobbyAppIcons.kt",
                listOf(
                    lobbyRow(
                        "Accepted app",
                        "SeatState.CONFIRMED, seat known. Plain icon, no mark. (line 67)",
                        lobby(15, 15, List(15) { seat(it) }),
                    ),
                    lobbyRow(
                        "Your app",
                        "seat.mine. Your chosen icon and an accent dot, bottom right. (lines 67, 79)",
                        lobby(15, 15, List(15) { seat(it, mine = it == 0) }),
                    ),
                    lobbyRow(
                        "Your app, checked",
                        "seat.mine and SeatState.CHECKING. Accent dot and amber lift. (lines 61, 79)",
                        lobby(0, 15, List(15) { seat(it, SeatState.CHECKING, mine = it == 0) }),
                    ),
                    lobbyRow(
                        "Checking",
                        "SeatState.CHECKING, seat known. Amber lift behind the icon, artwork untouched. (line 61)",
                        lobby(0, 15, List(15) { seat(it, SeatState.CHECKING) }),
                    ),
                    lobbyRow(
                        "Accepted, app unknown",
                        "CONFIRMED but no seat in the list. Grey tile with a phone glyph. (line 76)",
                        lobby(15, 15, emptyList()),
                    ),
                    lobbyRow(
                        "Checking, app unknown",
                        "CHECKING but no seat in the list. Phone glyph and amber lift. (lines 61, 76)",
                        lobby(0, 15, emptyList()),
                    ),
                    lobbyRow(
                        "Open seat",
                        "No state, nobody in it. Empty grey tile. (line 69)",
                        lobby(0, 0, emptyList()),
                    ),
                    lobbyRow(
                        "Letter tile",
                        "Seat has no icon and the app is not installed. AppMark draws a coloured initial. (AppCards.kt 59)",
                        lobby(15, 15, List(15) { letterSeat(it) }),
                    ),
                ),
            )

    private val runSection
        get() =
            Section(
                "Run strip: CrewRunAppIcons.kt",
                listOf(
                    runRow(
                        "Plain",
                        "Not opened, opened, or reported then opened. All drawn the same. (line 35)",
                        runCrew(
                            List(14) {
                                runApp(it, done = it % 4 == 1 || it % 4 == 2, reported = it % 4 == 2)
                            },
                        ),
                    ),
                    runRow(
                        "Reported tonight",
                        "app.reported && !app.done. Faded to 40% with an amber minus dot. (lines 34, 42)",
                        runCrew(List(14) { runApp(it, reported = true) }),
                    ),
                    runRow(
                        "Letter tile",
                        "No seat icon and the app is not installed. Coloured initial. (AppCards.kt 59)",
                        runCrew(List(14) { runApp(it, letter = true) }, withSeats = false),
                    ),
                    StateRow("Loading", "CrewRunAppIconsLoading. Grey skeleton squares. (line 61)", CrewRules.NIGHTLY) {
                        CrewRunAppIconsLoading()
                    },
                ),
            )

    private val footerSection
        get() =
            Section(
                "Tile footer: CrewTileParts.kt",
                listOf(
                    StateRow(
                        "Your app, footer",
                        "CrewTileApp under every Lobby and Run tile: your own chosen icon, 28dp. (line 74)",
                        1,
                        fixedSide = 28.dp,
                    ) {
                        val mine = seat(99, mine = true)
                        AppMark(mine.packageName, mine.appName, mine.icon, size = 28.dp, ownIcon = true)
                    },
                ),
            )

    /** The first [slots] slots of a real strip at the real tile width, drawn [factor] times larger. */
    @Composable
    private fun ZoomStrip(
        factor: Float,
        slots: Int,
        count: Int,
        fixedSide: Dp?,
        content: @Composable () -> Unit,
    ) {
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale)) {
            val d = LocalDensity.current
            val full = STRIP_WIDTH_DP.dp
            val side: Dp
            val gap: Dp
            if (fixedSide != null) {
                side = fixedSide
                gap = 0.dp
            } else {
                val px =
                    (with(d) { full.roundToPx() } - with(d) { 4.dp.roundToPx() } * (count - 1)) / count
                side = with(d) { px.coerceIn(1, 24.dp.roundToPx()).toDp() }
                gap = if (count > 1) (full - side * count) / (count - 1) else 0.dp
            }
            val pad = 2.dp
            Box(Modifier.width(side * slots + gap * (slots - 1) + pad * 2).height(side + pad * 2).clipToBounds()) {
                Box(Modifier.offset(pad, pad).wrapContentSize(Alignment.TopStart, unbounded = true).width(full)) { content() }
            }
        }
    }

    @Composable
    private fun StateLine(row: StateRow) {
        val slots = if (row.count == 1) 1 else 4
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(100.dp)) { ZoomStrip(1f, slots, row.count, row.fixedSide, row.strip) }
            ZoomStrip(3f, 1, row.count, row.fixedSide, row.strip)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
                Text(
                    row.condition,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    color = Tt.colors.text2,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    private fun sheet(
        name: String,
        dark: Boolean,
        vararg sections: Section,
    ) {
        frame.show {
            Ground {
                TestTrackTheme(darkTheme = dark) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Tt.colors.bg)
                            .padding(start = 32.dp, end = 32.dp, top = 56.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        sections.forEach { section ->
                            Text(section.header, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Tt.colors.text3)
                            section.rows.forEach { StateLine(it) }
                        }
                    }
                }
            }
        }
        frame.settle(800)
        frame.write(name)
    }

    @Test fun lobbyIconStates() = sheet("iconStates", false, lobbySection)

    @Test fun lobbyIconStatesDark() = sheet("iconStatesDark", true, lobbySection)

    @Test fun runIconStates() = sheet("iconStatesRun", false, runSection, footerSection)

    @Test fun runIconStatesDark() = sheet("iconStatesRunDark", true, runSection, footerSection)

    private companion object {
        /** The strip's width inside a tile on this phone: 412dp, 16dp page gutters, 16dp card padding. */
        const val STRIP_WIDTH_DP = 348
    }
}
