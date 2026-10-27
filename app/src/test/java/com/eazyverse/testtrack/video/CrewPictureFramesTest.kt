package com.eazyverse.testtrack.video

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.net.ApiSession
import com.eazyverse.testtrack.ui.Crew
import com.eazyverse.testtrack.ui.CrewApp
import com.eazyverse.testtrack.ui.CrewMember
import com.eazyverse.testtrack.ui.CrewMembersPage
import com.eazyverse.testtrack.ui.CrewPage
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewPicturesPage
import com.eazyverse.testtrack.ui.CrewReady
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.DockUi
import com.eazyverse.testtrack.ui.LocalPictureImage
import com.eazyverse.testtrack.ui.MainTab
import com.eazyverse.testtrack.ui.NightPicture
import com.eazyverse.testtrack.ui.PictureViewer
import com.eazyverse.testtrack.ui.Scene
import com.eazyverse.testtrack.ui.VisitDockContent
import com.eazyverse.testtrack.ui.crewGuide
import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.CrewPicturesViewModel
import com.eazyverse.testtrack.viewmodel.MemberNight
import com.eazyverse.testtrack.viewmodel.MemberNights
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The crew picture screens, from the app's own composables: Drive in Get ready, the night's two
 * asks, the control once a picture is in, an app missing its picture, and what the owner sees.
 * The pictures are the repo's sample screenshots, as the Circle frames use. Written to
 * media/public/frames/crew-pictures, opt-in like the other frames.
 */
@RunWith(VideoFramesRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [Phone.SDK], qualifiers = Phone.QUALIFIERS, application = Application::class)
class CrewPictureFramesTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val frame = Frame(compose, "crew-pictures")

    /** Frames with the sample pictures drawn from disk, as [LocalProof] does for Circle's. */
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        frame.show {
            androidx.compose.runtime.CompositionLocalProvider(
                LocalPictureImage provides { ref, whole ->
                    coil.compose.AsyncImage(
                        model = android.net.Uri.parse(ref),
                        contentDescription = "The picture of the app",
                        modifier =
                            if (whole) {
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            } else {
                                Modifier.fillMaxSize()
                            },
                        contentScale = if (whole) ContentScale.FillWidth else ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                },
            ) { content() }
        }

    @Before
    fun onlyWhenAsked() = assumeTrue("set -PvideoFrames to draw the video frames", videoFramesOn)

    private fun shot(app: String) =
        android.net.Uri
            .fromFile(File(FrameImages.art, "../../media/public/proof/$app.png").canonicalFile)
            .toString()

    private val pictures = listOf("habit", "weather", "lift")

    private val names =
        listOf(
            "Sofia Alvarez" to "Pocket Recipes",
            "Kenji Sato" to "Steps Daily",
            "Nadia Karim" to "Tiny Tasks",
            "Daniel Okafor" to "Lingo Cards",
            "Zara Ahmed" to "Pixel Weather",
            "Lucas Moreau" to "Habit Garden",
            "Grace Liu" to "Lift Log",
            "Emma Lindqvist" to "Sleep Sounds",
            "Arjun Mehta" to "Plant Pal",
        )

    private val seats =
        names.mapIndexed { i, (owner, app) ->
            CrewSeat(id = "seat-$i", appName = app, owner = owner, packageName = "com.example.app$i", crew = "Crew 05")
        } +
            CrewSeat(
                id = "seat-mine",
                appName = "Focus Flow",
                owner = "You",
                packageName = "com.example.focusflow",
                crew = "Crew 05",
                mine = true,
                versionCode = 41,
                versionName = "1.4.0",
                downsLeft = 2,
            )

    /** Six days in. Steps Daily was opened but its picture failed; four are still to open. */
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
                names.mapIndexed { i, (owner, app) ->
                    CrewMember(
                        id = "m-$i",
                        name = owner,
                        appName = app,
                        day = 6,
                        openedMineToday = i < 6,
                        openedMineAt = if (i < 6) "2026-10-27T${18 + i % 4}:${10 + i * 7}:00+06:00" else "",
                        openedMineDays = 5 + if (i < 6) 1 else 0,
                        openedMinePicture = if (i < 6) shot(pictures[i % 3]) else "",
                        openedMineByHand = i == 2,
                    )
                } + CrewMember(id = "m-mine", name = "You", appName = "Focus Flow", day = 6, mine = true),
            apps =
                names.mapIndexed { i, (owner, app) ->
                    CrewApp(
                        id = "seat-$i",
                        label = app,
                        packageName = "com.example.app$i",
                        owner = owner,
                        crew = "Crew 05",
                        done = i >= 5,
                        pictureDue = i == 1,
                        // Lingo Cards has an update out past its 24 hours; Tiny Tasks' feedback is late.
                        versionCode = if (i == 3) 12L else null,
                        updateRequired = i == 3,
                        feedbackSent = i % 3 == 0,
                        feedbackDueDay = 7,
                        feedbackLate = i == 2,
                    )
                },
        )

    private fun crews(): CrewViewModel {
        ApiSession.load(compose.activity)
        val packages = org.robolectric.Shadows.shadowOf(compose.activity.packageManager)
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
        return CrewViewModel().apply {
            setState("crews", listOf(running))
            setState("loading", false)
        }
    }

    @Test
    fun p01_ready() {
        crews()
        show {
            Ground {
                TabShell(MainTab.GROUPS, Scene.CREW) {
                    androidx.compose.foundation.lazy.LazyColumn {
                        crewGuide(
                            CrewReady(app = true, member = true, permissions = true, drive = false),
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
        frame.write("ready")
    }

    private fun stops() = running.apps.filter { !it.done }.map { RoundStore.Stop(it.id, it.packageName, it.label, "") }

    @Test
    fun p02_ask() {
        val vm = crews()
        Session.init(compose.activity)
        Session.updateDriveConnected(true)
        vm.pictures.ask(stops(), float = true)
        show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        frame.write("ask")
    }

    @Test
    fun p03_drive() {
        val vm = crews()
        Session.init(compose.activity)
        Session.updateDriveConnected(false)
        vm.pictures.ask(stops(), float = true)
        show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        frame.write("drive")
    }

    private fun dock(
        id: String,
        picture: Boolean?,
    ) {
        val file = File(FrameImages.art, "../../media/public/frames/circle/habitB.png").canonicalFile
        val image = javax.imageio.ImageIO.read(file)
        val bitmap = android.graphics.Bitmap.createBitmap(image.width, image.height, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.setPixels(
            image.getRGB(0, 0, image.width, image.height, null, 0, image.width),
            0,
            image.width,
            0,
            0,
            image.width,
            image.height,
        )
        val ui =
            DockUi(
                app = "Habit Garden",
                index = 2,
                total = 4,
                secondsLeft = 0,
                paused = false,
                counted = true,
                picture = picture,
                signIn = emptyList(),
                upright = true,
                open = true,
            )
        show {
            Ground {
                Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                Box(Modifier.fillMaxSize().padding(end = 12.dp), contentAlignment = Alignment.CenterEnd) {
                    VisitDockContent(ui, onToggleOpen = {}, onPlayPause = {}, onCopy = {}, onDrag = {}, onDragEnd = {})
                }
            }
        }
        frame.settle(800)
        frame.write(id)
    }

    @Test
    fun p04_counted() = dock("counted", picture = true)

    @Test
    fun p05_noPicture() = dock("noPicture", picture = false)

    @Test
    fun p06_due() {
        val vm = crews()
        show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        frame.write("due")
    }

    @Test
    fun p07_members() {
        val vm = crews()
        show { Pushed { CrewMembersPage(vm = vm, id = running.id, onBack = {}) } }
        frame.settle(1500)
        frame.write("members")
    }

    @Test
    fun p08_everyNight() {
        val vm = CrewPicturesViewModel()
        val testers =
            names.take(7).mapIndexed { i, (owner, _) ->
                MemberNights(
                    seatId = "seat-$i",
                    name = owner,
                    day = 6,
                    out = false,
                    excused = i == 6,
                    firstNight = if (i == 4) 2 else 1,
                    freeDays = if (i == 1) setOf(2) else emptySet(),
                    nights =
                        (1..6).mapNotNull { d ->
                            when {
                                i == 3 && d == 3 -> null
                                d == 6 && i >= 5 -> null
                                else ->
                                    MemberNight(
                                        day = d,
                                        at = "2026-10-2${d}T19:1$d:00+06:00",
                                        usedMs = 11_000L + d * 1000,
                                        proof = shot(pictures[(i + d) % 3]),
                                        byHand = i == 2 && d == 4,
                                        counts = true,
                                    )
                            }
                        },
                )
            }
        vm.setState("pictures", AppNights("Focus Flow", 14, testers))
        vm.setState("loading", false)
        show { Pushed { CrewPicturesPage(vm, "crew-05", "Crew 05", onBack = {}) } }
        frame.settle(1500)
        frame.write("everyNight")
    }

    @Test
    fun p09_viewer() {
        show {
            Pushed {
                PictureViewer(
                    NightPicture(shot("habit"), "Sofia Alvarez", 4, "2026-10-25T18:12:00+06:00", 14_000, false, "Focus Flow"),
                ) {}
            }
        }
        frame.settle(1500)
        frame.write("viewer")
    }

    /** A picture taken but not sent yet, and the reason the last try stopped. */
    @Test
    fun p10_stuck() {
        val vm = crews()
        val waiting =
            com.eazyverse.testtrack.data.VisitPictures
                .Waiting("seat-1", "Steps Daily", "/nowhere.jpg")

        fun <T> set(
            name: String,
            value: T,
        ) {
            val field = com.eazyverse.testtrack.data.VisitPictures::class.java.getDeclaredField(name).apply { isAccessible = true }
            @Suppress("UNCHECKED_CAST")
            (field.get(com.eazyverse.testtrack.data.VisitPictures) as kotlinx.coroutines.flow.MutableStateFlow<T>).value = value
        }
        set("queue", listOf(waiting))
        set("failure", "We could not reach TestTrack. Tonight's pictures will go by themselves when you are back online.")
        show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        // Opening the page tries to send, and without Google here it asks to reconnect Drive.
        vm.pictures.clearDriveAsk()
        set("failure", "We could not reach TestTrack. Tonight's pictures will go by themselves when you are back online.")
        frame.settle(800)
        frame.write("stuck")
        set("queue", emptyList<com.eazyverse.testtrack.data.VisitPictures.Waiting>())
        set("failure", null)
    }

    /** The member's Play feedback card on the run page. */
    @Test
    fun p12_feedback() {
        val vm = crews()
        show { Pushed { CrewPage(vm = vm, id = running.id, onBack = {}, onSignIn = {}, onAddToCrew = {}) } }
        frame.settle(800)
        vm.pictures.clearDriveAsk()
        compose
            .onAllNodes(
                androidx.compose.ui.test
                    .hasScrollToNodeAction(),
            )[0]
            .performScrollToNode(hasText("Private feedback on Play"))
        frame.settle(800)
        frame.write("feedbackCard")
    }

    /**
     * Tonight's rows for an app whose update is past its 24 hours and one whose feedback is late,
     * and the owner's card twice: Update ready, and Update waiting on a copy from Play.
     */
    @Test
    fun p13_rowsAndOwner() {
        crews()
        val actions =
            com.eazyverse.testtrack.ui.CrewActions(
                onOpen = {},
                onOpenAll = {},
                onInstall = {},
                onReport = {},
                onLeave = {},
                onTakeSeat = {},
                localVersion = { 11L },
            )
        val blocked =
            com.eazyverse.testtrack.ui.CrewActions(
                onOpen = {},
                onOpenAll = {},
                onInstall = {},
                onReport = {},
                onLeave = {},
                onTakeSeat = {},
                updateBlocked = { "Install the new version from Google Play on this phone first." },
            )
        val mine = seats.last()
        show {
            Pushed {
                androidx.compose.foundation.layout.Column(Modifier.padding(16.dp)) {
                    listOf(3, 2).forEachIndexed { k, i ->
                        com.eazyverse.testtrack.ui.TonightRow(
                            running.apps[i],
                            seats[i],
                            com.eazyverse.testtrack.ui
                                .placeOf(k, 2),
                            actions,
                        ) {}
                    }
                    com.eazyverse.testtrack.ui
                        .OwnerToolsCard(mine, actions)
                    com.eazyverse.testtrack.ui
                        .OwnerToolsCard(mine.copy(downsLeft = 1), blocked)
                }
            }
        }
        frame.settle(800)
        frame.write("rowsAndOwner")
    }
}
