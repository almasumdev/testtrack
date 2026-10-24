package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewApp
import com.eazyverse.testtrack.ui.CrewMember
import com.eazyverse.testtrack.ui.CrewMembersPage
import com.eazyverse.testtrack.ui.CrewPage
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewScreen
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.MainTab
import com.eazyverse.testtrack.ui.Scene
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The promo's Crew frames, from the app's own composables with sample crews: one taking seats, and
 * one six days into its run. Written to media/public/frames/crew, opt-in like [CircleVideoFramesTest].
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewVideoFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose, "crew")

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private fun icon(name: String) = "https://icons.testtrack.invalid/$name.png"

    /** The fourteen other apps of a crew, and the reader's own. */
    private val others =
        listOf(
            Triple("Pocket Recipes", "Sofia Alvarez", "icon_recipes"),
            Triple("Steps Daily", "Kenji Sato", "icon_steps"),
            Triple("Tiny Tasks", "Nadia Karim", "icon_tasks"),
            Triple("Lingo Cards", "Daniel Okafor", "icon_lingo"),
            Triple("Pixel Weather", "Zara Ahmed", "icon_weather"),
            Triple("Habit Garden", "Lucas Moreau", "icon_habit"),
            Triple("Lift Log", "Grace Liu", "icon_lift"),
            Triple("Sleep Sounds", "Emma Lindqvist", ""),
            Triple("Plant Pal", "Arjun Mehta", ""),
            Triple("Fuel Log", "Hana Kim", ""),
            Triple("Budget Buddy", "Tom Becker", ""),
            Triple("Quiet Timer", "Priya Nair", ""),
            Triple("Bird Notes", "Rafiul Islam", ""),
            Triple("Chess Clock", "Aisha Rahman", ""),
        )

    private val seats =
        others.mapIndexed { i, (app, owner, art) ->
            CrewSeat(id = "seat-$i", appName = app, owner = owner, packageName = "com.example.app$i", icon = if (art.isEmpty()) "" else icon(art), crew = "Crew 05")
        } + CrewSeat(id = "seat-mine", appName = "Focus Flow", owner = "You", packageName = "com.example.focusflow", icon = icon("icon_focus"), crew = "Crew 05", mine = true)

    /** Six days in: nine of tonight's fourteen still to open, nine testers have opened yours. */
    private val running =
        Crew(
            id = "crew-05",
            name = "Crew 05",
            phase = CrewPhase.RUN,
            taken = 15,
            filled = 15,
            seats = seats,
            day = 6,
            members =
                others.mapIndexed { i, (_, owner, _) ->
                    CrewMember(
                        id = "m-$i",
                        name = owner,
                        appName = others[i].first,
                        day = 6,
                        openedMineToday = i < 9,
                        openedMineAt = if (i < 9) "2026-10-24T${18 + i % 4}:${10 + i * 5}:00+06:00" else "",
                        openedMineDays = 5 + if (i < 9) 1 else 0,
                    )
                } + CrewMember(id = "m-mine", name = "You", appName = "Focus Flow", day = 6, mine = true),
            apps = others.mapIndexed { i, (app, owner, _) -> CrewApp(id = "seat-$i", label = app, packageName = "com.example.app$i", owner = owner, crew = "Crew 05", done = i >= 9) },
        )

    /** Taking seats: eleven of fifteen in. */
    private val filling =
        Crew(
            id = "crew-06",
            name = "Crew 06",
            phase = CrewPhase.OPEN,
            filling = true,
            taken = 11,
            filled = 11,
            icons = others.take(11).map { it.third }.filter { it.isNotEmpty() }.map(::icon),
        )

    private fun crews(): CrewViewModel {
        ApiSession.load(compose.activity)
        // Tonight's apps are on this phone already, so each row opens rather than installs.
        val packages = org.robolectric.Shadows.shadowOf(compose.activity.packageManager)
        seats.filter { !it.mine }.forEach { seat ->
            packages.installPackage(
                android.content.pm.PackageInfo().apply {
                    packageName = seat.packageName
                    applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = seat.packageName }
                },
            )
        }
        return CrewViewModel().apply {
            setState("crews", listOf(running))
            setState("open", filling)
            setState("loading", false)
        }
    }

    @Test
    fun c01_open() {
        val vm = crews()
        frame.show { Ground { TabShell(MainTab.GROUPS, Scene.CREW) { CrewScreen(vm = vm) } } }
        frame.settle(800)
        frame.target("takeSeat", compose.onNode(hasText("Take a seat", substring = true) and hasClickAction()))
        frame.target("headline", compose.onNodeWithText("seats left", substring = true))
        frame.target("whatCrew", compose.onNodeWithText("15 developers", substring = true))
        frame.write("crewOpen")
    }

    @Test
    fun c02_tonight() {
        val vm = crews()
        frame.show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        runCatching { frame.target("openAll", compose.onNode(hasText("Open all", substring = true) and hasClickAction())) }
        runCatching { frame.target("left", compose.onNodeWithText("left tonight", substring = true)) }
        frame.write("tonight")
    }

    @Test
    fun c03_members() {
        val vm = crews()
        frame.show { Pushed { CrewMembersPage(vm = vm, id = running.id, onBack = {}) } }
        frame.settle(800)
        frame.target("opened", compose.onNodeWithText("opened your app tonight", substring = true))
        frame.write("members")
    }
}
