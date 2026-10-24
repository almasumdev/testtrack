package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewAddScreen
import com.eazyverse.testtrack.ui.CrewApp
import com.eazyverse.testtrack.ui.CrewMember
import com.eazyverse.testtrack.ui.CrewMembersPage
import com.eazyverse.testtrack.ui.CrewPage
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewReady
import com.eazyverse.testtrack.ui.CrewReportDialog
import com.eazyverse.testtrack.ui.CrewScreen
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.DockUi
import com.eazyverse.testtrack.ui.MainTab
import com.eazyverse.testtrack.ui.Scene
import com.eazyverse.testtrack.ui.VisitDockContent
import com.eazyverse.testtrack.ui.crewGuide
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
            CrewSeat(
                id = "seat-$i",
                appName = app,
                owner = owner,
                packageName = "com.example.app$i",
                icon = if (art.isEmpty()) "" else icon(art),
                crew = "Crew 05",
            )
        } +
            CrewSeat(
                id = "seat-mine",
                appName = "Focus Flow",
                owner = "You",
                packageName = "com.example.focusflow",
                icon = icon("icon_focus"),
                crew = "Crew 05",
                mine = true,
            )

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
            apps =
                others.mapIndexed { i, (app, owner, _) ->
                    CrewApp(
                        id = "seat-$i",
                        label = app,
                        packageName = "com.example.app$i",
                        owner = owner,
                        crew = "Crew 05",
                        done =
                            i >= 9,
                    )
                },
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
            icons =
                others
                    .take(11)
                    .map { it.third }
                    .filter { it.isNotEmpty() }
                    .map(::icon),
        )

    /** Filling, with the reader's app in: eleven of fifteen, nothing opened yet. */
    private val lobby =
        Crew(
            id = "crew-07",
            name = "Crew 07",
            phase = CrewPhase.LOBBY,
            filling = true,
            taken = 11,
            filled = 11,
            seats = seats.drop(4).map { it.copy(crew = "Crew 07") },
        )

    /** Full and waiting for midnight. */
    private val waiting =
        lobby.copy(
            id = "crew-08",
            name = "Crew 08",
            filling = false,
            taken = 15,
            filled = 15,
            seats = seats.map { it.copy(crew = "Crew 08") },
            startsIn = 9 * 3_600_000L + 20 * 60_000L,
        )

    /** Six days in, and yesterday was missed. */
    private val slipping =
        running.copy(
            id = "crew-09",
            name = "Crew 09",
            members = running.members.map { if (it.mine) it.copy(missed = 1, missedYesterday = true) else it },
        )

    /** Over: fourteen days, thirteen of the fourteen testers went the distance. */
    private val finished =
        running.copy(
            id = "crew-04",
            name = "Crew 04",
            phase = CrewPhase.RECORD,
            day = 14,
            finishedDays = 14,
            members =
                running.members.mapIndexed { i, m ->
                    if (m.mine) {
                        m
                    } else {
                        m.copy(
                            day = 14,
                            openedMineDays =
                                if (i ==
                                    3
                                ) {
                                    9
                                } else {
                                    14
                                },
                            out = i == 3,
                        )
                    }
                },
        )

    /**
     * The Crew model holding [list], and [open] taking seats. With [installed], the crew's apps are
     * on this phone, so a row opens rather than installs.
     */
    private fun crews(
        list: List<Crew> = listOf(running, lobby, waiting, slipping, finished),
        installed: Boolean = true,
    ): CrewViewModel {
        ApiSession.load(compose.activity)
        val packages = org.robolectric.Shadows.shadowOf(compose.activity.packageManager)
        if (installed) {
            seats.filter { !it.mine }.forEach { seat ->
                packages.installPackage(
                    android.content.pm.PackageInfo().apply {
                        packageName = seat.packageName
                        applicationInfo =
                            android.content.pm
                                .ApplicationInfo()
                                .apply { packageName = seat.packageName }
                    },
                )
            }
        }
        return CrewViewModel().apply {
            setState("crews", list)
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
        runCatching { frame.target("seeWho", compose.onNode(hasText("See who tested it") and hasClickAction())) }
        frame.write("tonight")
    }

    @Test
    fun c04_ready() {
        // The Open tab's own guide cards, drawn from the top, with nothing done yet. Scrolled to
        // in the tab, the seat tiles above them keep it from ever going still.
        crews(list = emptyList())
        frame.show {
            Ground {
                TabShell(MainTab.GROUPS, Scene.CREW) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        crewGuide(
                            CrewReady(app = false, member = false, permissions = false),
                            hasApps = true,
                            onApp = {},
                            onJoin = {},
                            onAllow = {},
                        )
                    }
                }
            }
        }
        frame.settle(800)
        runCatching { frame.target("readyApp", compose.onNodeWithText("checked on Play", substring = true)) }
        runCatching { frame.target("readyGroup", compose.onAllNodesWithText("testers group", substring = true)[0]) }
        runCatching { frame.target("readyPerms", compose.onNodeWithText("permissions", substring = true)) }
        runCatching { frame.target("check", compose.onNode(hasText("Check") and hasClickAction())) }
        runCatching { frame.target("join", compose.onNode(hasText("Join") and hasClickAction())) }
        runCatching { frame.target("allow", compose.onNode(hasText("Allow") and hasClickAction())) }
        runCatching { frame.target("getReady", compose.onNodeWithText("Before you take a seat")) }
        frame.write("ready")
    }

    @Test
    fun c05_seat() {
        val vm = crews(list = emptyList())
        frame.show { Pushed { CrewAddScreen(crews = vm, app = CircleSamples.focus, onDone = {}, onBack = {}) } }
        frame.settle(800)
        runCatching { frame.target("seatName", compose.onNodeWithText("Name the crew sees", substring = true)) }
        runCatching { frame.target("takeSeat", compose.onNode(hasText("Take a seat", substring = true) and hasClickAction())) }
        runCatching {
            frame.target(
                "nameField",
                compose.onNode(
                    androidx.compose.ui.test
                        .hasSetTextAction(),
                ),
            )
        }
        runCatching { frame.target("needsSignIn", compose.onNode(hasText("Needs a sign-in") and hasClickAction())) }
        runCatching { frame.target("price", compose.onNodeWithText("A seat costs")) }
        frame.write("seat")
    }

    /** The seat page once "Needs a sign-in" is picked: where the crew's test account goes. */
    @Test
    fun c05b_seatLogin() {
        val vm = crews(list = emptyList())
        frame.show { Pushed { CrewAddScreen(crews = vm, app = CircleSamples.focus, onDone = {}, onBack = {}) } }
        compose.onNode(hasText("Needs a sign-in") and hasClickAction()).performClick()
        frame.settle(800)
        runCatching {
            frame.target(
                "login",
                compose.onAllNodes(
                    androidx.compose.ui.test
                        .hasSetTextAction(),
                )[1],
            )
        }
        runCatching { frame.target("takeSeat", compose.onNode(hasText("Take a seat", substring = true) and hasClickAction())) }
        frame.write("seatLogin")
    }

    /** The same page scrolled to its end, the price and Take a seat in view. */
    @Test
    fun c05c_seatSubmit() {
        val vm = crews(list = emptyList())
        frame.show { Pushed { CrewAddScreen(crews = vm, app = CircleSamples.focus, onDone = {}, onBack = {}) } }
        compose.onNode(hasText("Needs a sign-in") and hasClickAction()).performClick()
        frame.settle(400)
        frame.scrollTo(compose.onNode(hasText("Take a seat", substring = true) and hasClickAction()))
        frame.settle(800)
        runCatching { frame.target("takeSeat", compose.onNode(hasText("Take a seat", substring = true) and hasClickAction())) }
        runCatching { frame.target("price", compose.onNodeWithText("A seat costs")) }
        frame.write("seatSubmit")
    }

    @Test
    fun c06_lobby() {
        val vm = crews(list = listOf(lobby), installed = false)
        frame.show { Pushed { CrewPage(vm = vm, id = lobby.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        runCatching { frame.target("install", compose.onAllNodes(hasText("Install") and hasClickAction())[0]) }
        runCatching { frame.target("rowWeather", compose.onNode(hasText("Pixel Weather", substring = true) and hasClickAction())) }
        runCatching { frame.target("filling", compose.onNodeWithText("more and it starts", substring = true)) }
        runCatching { frame.target("inChip", compose.onNodeWithText("Play shows it", substring = true)) }
        frame.write("lobby")
    }

    @Test
    fun c07_report() {
        val vm = crews(list = listOf(lobby), installed = false)
        frame.show {
            Pushed {
                CrewPage(vm = vm, id = lobby.id, onBack = {}, onSignIn = {}, onAddToCrew = {})
                CrewReportDialog(seat = lobby.seats.first(), onReport = {}, onDismiss = {})
            }
        }
        frame.settle(800)
        runCatching { frame.target("report", compose.onNode(hasText("Report") and hasClickAction())) }
        runCatching { frame.target("wontInstall", compose.onNode(hasText("It won't install from Play") and hasClickAction())) }
        frame.write("report")
    }

    @Test
    fun c08_waiting() {
        val vm = crews()
        frame.show { Pushed { CrewPage(vm = vm, id = waiting.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        runCatching { frame.target("starts", compose.onNodeWithText("Starts in", substring = true)) }
        frame.write("waiting")
    }

    @Test
    fun c09_run() {
        val vm = crews(list = listOf(running))
        vm.setState("tab", CrewPhase.RUN)
        frame.show { Ground { TabShell(MainTab.GROUPS, Scene.CREW) { CrewScreen(vm = vm) } } }
        frame.settle(800)
        runCatching { frame.target("openAll", compose.onAllNodes(hasText("Open all", substring = true) and hasClickAction())[0]) }
        frame.write("run")
    }

    /** The timer over the app being visited: counting, then counted. */
    private fun dock(
        id: String,
        left: Int,
    ) {
        val file = java.io.File(FrameImages.art, "../../media/public/frames/circle/habitB.png").canonicalFile
        val picture = javax.imageio.ImageIO.read(file)
        val bitmap = android.graphics.Bitmap.createBitmap(picture.width, picture.height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            picture.getRGB(0, 0, picture.width, picture.height, null, 0, picture.width),
            0,
            picture.width,
            0,
            0,
            picture.width,
            picture.height,
        )
        val ui =
            DockUi(
                app = "Habit Garden",
                index = 2,
                total = 9,
                secondsLeft = left,
                paused = false,
                counted = left == 0,
                signIn = emptyList(),
                upright = true,
                open = true,
            )
        frame.show {
            Ground {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Box(Modifier.fillMaxSize().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    VisitDockContent(ui, onToggleOpen = {}, onPlayPause = {}, onCopy = {}, onDrag = {}, onDragEnd = {})
                }
            }
        }
        frame.settle(800)
        runCatching { frame.target("next", compose.onNode(hasText("Next", substring = true) and hasClickAction())) }
        runCatching { frame.target("pause", compose.onNode(hasText("Pause", substring = true) and hasClickAction())) }
        runCatching { frame.target("dockApp", compose.onNodeWithText("Habit Garden", useUnmergedTree = true)) }
        frame.write(id)
    }

    @Test
    fun c10_dock() = dock("dock", 6)

    @Test
    fun c11_counted() = dock("counted", 0)

    @Test
    fun c12_missed() {
        val vm = crews(list = listOf(slipping))
        frame.show(idle = false) { Pushed { CrewPage(vm = vm, id = slipping.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        runCatching { frame.target("warning", compose.onNodeWithText("Miss tonight too", substring = true)) }
        frame.write("missed", idle = false)
    }

    @Test
    fun c13_record() {
        val vm = crews()
        frame.show { Pushed { CrewPage(vm = vm, id = finished.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        runCatching { frame.target("finished", compose.onNodeWithText("finished", substring = true)) }
        frame.write("record")
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
