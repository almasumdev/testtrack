package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.*
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.Tt
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.imageio.ImageIO

@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewLobbyIconsFramesTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val frame = Frame(compose, "crew")
    private val listings =
        listOf(
            "Auto Silent" to "com.eazyverse.soundscheduler",
            "Bluefy" to "com.eazyverse.bluefy",
            "Astra Match" to "com.shahadat.astramatch",
            "Baka Run" to "com.markuljdev.bakarun",
            "Cherish" to "app.cherish.people",
            "EditMe" to "com.arisoftware.editme",
            "Gaan" to "com.arisoftware.gaan",
        )
    private val seats =
        listings.mapIndexed { i, (name, pkg) ->
            CrewSeat(
                "seat-$i",
                name,
                if (i == 0) "You" else "Tester",
                pkg,
                mine = i == 0,
                icon = "https://icons.testtrack.invalid/circle_actual_${pkg.replace('.', '_')}.png",
                state = if (i < 5) SeatState.CONFIRMED else SeatState.CHECKING,
            )
        }
    private val crew = Crew("lobby", "Crew 59", CrewPhase.LOBBY, filling = true, filled = 5, taken = 7, seats = seats)
    private val fullSeats =
        seats.map { it.copy(state = SeatState.CONFIRMED) } +
            listOf(
                "Pocket Recipes",
                "Steps Daily",
                "Tiny Tasks",
                "Lingo Cards",
                "Pixel Weather",
                "Habit Garden",
                "Lift Log",
                "Sleep Sounds",
            ).mapIndexed { i, name -> CrewSeat("fallback-$i", name, "Tester", "demo.lobby$i") }

    @Before fun onlyWhenAsked() = assumeTrue("set -PvideoFrames", videoFramesOn)

    @Test fun actualIconsCheckingAndVacanciesKeepCardNavigation() {
        val dark = mutableStateOf(true)
        var opened = 0
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = dark.value) {
                    Column(
                        Modifier.fillMaxSize().background(Tt.colors.bg).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CrewLobbyRow(crew) { opened++ }
                        CrewLobbyRow(
                            crew.copy(
                                name = "Crew 60",
                                filled = 4,
                                seats = seats.map { if (it.mine) it.copy(state = SeatState.CHECKING) else it },
                            ),
                        ) {}
                        CrewLobbyRow(
                            crew.copy(
                                name = "Crew 61",
                                filling = false,
                                filled = 15,
                                taken = 15,
                                seats = fullSeats,
                                startsIn = 3_600_000L,
                            ),
                        ) {}
                    }
                }
            }
        }
        frame.settle(800)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("5 confirmed, 2 checking, 8 open seats. The dot marks your app.").assertExists()
        frame.write("lobbyAppIconsDark")
        compose.onNodeWithText("Crew 59").performClick()
        assertEquals(1, opened)
        compose.runOnIdle { dark.value = false }
        frame.settle(400)
        frame.write("lobbyAppIconsLight")
    }

    @Test fun iconStripAndLoaderFitANarrowTile() {
        frame.show {
            Pushed {
                Column(Modifier.padding(16.dp).width(260.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.testTag("narrow")) { CrewLobbyRow(crew) {} }
                    CrewLobbyRowLoading()
                }
            }
        }
        frame.settle(800)
        val tile = compose.onNodeWithTag("narrow", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val strip =
            compose
                .onNodeWithContentDescription("5 confirmed, 2 checking, 8 open seats. The dot marks your app.", useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
        org.junit.Assert.assertTrue(strip.left >= tile.left && strip.right <= tile.right)
        org.junit.Assert.assertTrue(strip.height <= with(compose.density) { 24.dp.toPx() } + 1f)
        frame.write("lobbyAppIconsNarrow")
    }

    @Test fun statusElevationNeverRecolorsDarkOrLightArtwork() {
        frame.show {
            Pushed {
                TestTrackTheme(darkTheme = true) {
                    Row(
                        Modifier.fillMaxSize().background(Tt.colors.bg).padding(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        listOf(false, true).forEach { marked ->
                            Canvas(
                                Modifier
                                    .size(48.dp)
                                    .then(if (marked) Modifier.crewIconElevation(Tt.colors.ok) else Modifier)
                                    .testTag(if (marked) "framed-logo" else "original-logo"),
                            ) {
                                drawRect(Color.Black)
                                drawLine(
                                    Color.White,
                                    Offset(size.width * 0.3f, size.height * 0.4f),
                                    Offset(
                                        size.width * 0.7f,
                                        size.height * 0.4f,
                                    ),
                                    2.dp.toPx(),
                                )
                            }
                        }
                    }
                }
            }
        }
        frame.settle(800)
        frame.write("iconElevation")
        val original = compose.onNodeWithTag("original-logo").fetchSemanticsNode().boundsInRoot
        val marked = compose.onNodeWithTag("framed-logo").fetchSemanticsNode().boundsInRoot
        val shot = ImageIO.read(File(System.getProperty("videoFrames.out"), "crew/iconElevation.png"))
        var checked = 0
        var changed = 0
        for (y in 0 until original.height.toInt()) {
            for (x in 0 until original.width.toInt()) {
                val before = shot.getRGB(original.left.toInt() + x, original.top.toInt() + y)
                if (before == Color.Black.toArgb() || before == Color.White.toArgb()) {
                    checked++
                    if (before != shot.getRGB(marked.left.toInt() + x, marked.top.toInt() + y)) changed++
                }
            }
        }
        org.junit.Assert.assertTrue("Fixture includes opaque artwork", checked > 1000)
        assertEquals("The status elevation must not recolor artwork", 0, changed)
        org.junit.Assert.assertNotEquals(
            shot.getRGB(original.left.toInt() - 3, original.center.y.toInt()),
            shot.getRGB(marked.left.toInt() - 3, marked.center.y.toInt()),
        )
    }
}
