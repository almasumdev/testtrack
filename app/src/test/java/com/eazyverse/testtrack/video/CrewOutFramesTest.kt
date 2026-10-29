package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.ui.AppealSheet
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewActions
import com.eazyverse.testtrack.ui.CrewApp
import com.eazyverse.testtrack.ui.CrewHeading
import com.eazyverse.testtrack.ui.CrewMember
import com.eazyverse.testtrack.ui.CrewPage
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.DoneRow
import com.eazyverse.testtrack.ui.LeftRow
import com.eazyverse.testtrack.ui.LeftRunNote
import com.eazyverse.testtrack.ui.SeatAppeal
import com.eazyverse.testtrack.ui.SeatState
import com.eazyverse.testtrack.ui.TonightRow
import com.eazyverse.testtrack.ui.placeOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant

/**
 * An app taken out of a run, from the app's own composables: its owner's card at each point of
 * the appeal, the appeal itself, and the Left the run rows everybody else sees. Written to
 * media/public/frames/crew-out, opt-in like the other frames.
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewOutFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose, "crew-out")

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private val others =
        listOf("Kenji Sato" to "Steps Daily", "Nadia Karim" to "Tiny Tasks", "Sofia Alvarez" to "Pocket Recipes")

    /** Crew 05 on night 7, with the reader's own Focus Flow taken out and its appeal at [appeal]. */
    private fun ownerOut(appeal: SeatAppeal?): CrewViewModel {
        ApiSession.load(compose.activity)
        val mine =
            CrewSeat(
                id = "seat-mine",
                appName = "Focus Flow",
                owner = "You",
                packageName = "com.example.focusflow",
                crew = "Crew 05",
                mine = true,
                state = SeatState.REMOVED,
                outReason = "Missed day 5 and day 6.",
                charged = 100,
                refunded = 0,
                appeal = appeal,
            )
        val crew =
            Crew(
                id = "crew-05",
                name = "Crew 05",
                phase = CrewPhase.RUN,
                taken = 15,
                filled = 15,
                day = 7,
                seats = listOf(mine),
                members = listOf(CrewMember(id = "m", name = "You", appName = "Focus Flow", day = 7, mine = true)),
            )
        return CrewViewModel().apply {
            setState("crews", listOf(crew))
            setState("loading", false)
        }
    }

    private fun page(
        name: String,
        appeal: SeatAppeal?,
    ) {
        val vm = ownerOut(appeal)
        frame.show { Pushed { CrewPage(vm = vm, id = "crew-05", onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(600)
        frame.write(name)
    }

    @Test
    fun o1_out() = page("out", null)

    @Test
    fun o2_waiting() =
        page("waiting", SeatAppeal("waiting", "My phone was in repair on day 5 and 6. I'm back now and can open everything tonight."))

    @Test
    fun o3_refused() =
        page("refused", SeatAppeal("refused", "Phone in repair.", "Visits stopped on day 4 too, so this wasn't only the repair."))

    @Test
    fun o4_ask() {
        frame.show { Pushed { AppealSheet(appName = "Focus Flow", busy = false, onSend = {}, onDismiss = {}) } }
        frame.settle(900)
        frame.write("ask")
    }

    /** The rows a tester sees: two apps still owed, and Pocket Recipes, out since [outAt]. */
    private fun left(
        name: String,
        outAt: String,
    ) {
        val seats =
            others.mapIndexed { i, (owner, app) ->
                CrewSeat(id = "s$i", appName = app, owner = owner, packageName = "com.example.app$i", crew = "Crew 05")
            }
        val apps =
            others.mapIndexed { i, (owner, app) ->
                CrewApp(
                    id = "s$i",
                    label = app,
                    packageName = "com.example.app$i",
                    owner = owner,
                    crew = "Crew 05",
                    outAt =
                        if (i ==
                            2
                        ) {
                            outAt
                        } else {
                            ""
                        },
                )
            }
        val actions = CrewActions(onOpen = {}, onOpenAll = {}, onInstall = {}, onReport = {}, onLeave = {}, onTakeSeat = {})
        frame.show {
            Pushed {
                Column {
                    CrewHeading("Tonight", "2")
                    TonightRow(apps[0], seats[0], placeOf(0, 2), actions) {}
                    TonightRow(apps[1], seats[1], placeOf(1, 2), actions) {}
                    CrewHeading("Left the run", "1")
                    LeftRunNote()
                    LeftRow(apps[2], seats[2], placeOf(0, 1), actions)
                }
            }
        }
        frame.settle(600)
        frame.write(name)
    }

    /** Tonight with a quiet skip mark: DHM left the reader's app unopened on two nights. */
    @Test
    fun t3_skips() {
        val seat =
            CrewSeat(
                id = "d",
                appName = "Deshi Help Malaysia",
                owner = "Mr. Ariyan Ahmad",
                packageName = "com.example.dhm",
                crew = "Crew 05",
            )
        val dhm =
            CrewApp(
                id = "d",
                label = "Deshi Help Malaysia",
                packageName = "com.example.dhm",
                owner = "Mr. Ariyan Ahmad",
                crew = "Crew 05",
                skips = 2,
            )
        val other = CrewSeat(id = "s0", appName = "Steps Daily", owner = "Kenji Sato", packageName = "com.example.app0", crew = "Crew 05")
        val steps = CrewApp(id = "s0", label = "Steps Daily", packageName = "com.example.app0", owner = "Kenji Sato", crew = "Crew 05")
        val actions = CrewActions(onOpen = {}, onOpenAll = {}, onInstall = {}, onReport = {}, onLeave = {}, onTakeSeat = {})
        frame.show {
            Pushed {
                Column {
                    CrewHeading("Tonight", "2")
                    TonightRow(dhm, seat, placeOf(0, 2), actions) {}
                    TonightRow(steps, other, placeOf(1, 2), actions) {}
                }
            }
        }
        frame.settle(600)
        frame.write("skips")
    }

    /** An update out: told today (still within its 24 hours), and one opened tonight on the old copy. */
    @Test
    fun t4_update_out() {
        val seats =
            others.mapIndexed { i, (owner, app) ->
                CrewSeat(id = "s$i", appName = app, owner = owner, packageName = "com.example.app$i", crew = "Crew 05")
            }
        val apps =
            others.mapIndexed { i, (owner, app) ->
                CrewApp(
                    id = "s$i",
                    label = app,
                    packageName = "com.example.app$i",
                    owner = owner,
                    crew = "Crew 05",
                    versionCode = 12,
                    versionName = "1.4.0",
                    versionAt = Instant.now().minusSeconds(5 * 3600).toString(),
                    done = i == 1,
                )
            }
        val actions =
            CrewActions(
                onOpen = {},
                onOpenAll = {},
                onInstall = {},
                onReport = {},
                onLeave = {},
                onTakeSeat = {},
                localVersion = { 11 },
            )
        frame.show {
            Pushed {
                Column {
                    CrewHeading("Tonight", "1")
                    TonightRow(apps[0], seats[0], placeOf(0, 1), actions) {}
                    CrewHeading("Done", "1")
                    DoneRow(apps[1], seats[1], placeOf(0, 1), actions) {}
                }
            }
        }
        frame.settle(600)
        frame.write("updateOut")
    }

    @Test
    fun t1_left_today() = left("left-today", Instant.now().toString())

    @Test
    fun t2_left_uninstall() = left("left-uninstall", Instant.now().minusSeconds(30 * 3600).toString())
}
