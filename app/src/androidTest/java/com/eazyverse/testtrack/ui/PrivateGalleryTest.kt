package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.viewmodel.InviteRefusal
import com.eazyverse.testtrack.viewmodel.PrivateNight
import com.eazyverse.testtrack.viewmodel.PrivateResults
import com.eazyverse.testtrack.viewmodel.PrivateState
import com.eazyverse.testtrack.viewmodel.PrivateTest
import com.eazyverse.testtrack.viewmodel.PrivateTester
import com.eazyverse.testtrack.viewmodel.PrivateTestsViewModel
import com.eazyverse.testtrack.viewmodel.ResultsFault
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The private testers states, P1 to P14 of docs/design/crew-volunteers/flow.html, drawn by the real
 * screens and saved as pictures in files/gallery/private. Nothing is asked of the server: the crews
 * and the asks go in through the view models' preview.
 */
@RunWith(AndroidJUnit4::class)
class PrivateGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val out: File by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), "gallery/private").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private val mineSeat =
        CrewSeat("s1", "Budget Buddy", "Hasan Rahman", "com.hasanapps.budgetbuddy", crew = "Crew 58", mine = true)

    private val ownerCrew =
        Crew(
            id = "c58",
            name = "Crew 58",
            phase = CrewPhase.RUN,
            taken = 15,
            filled = 15,
            seats = listOf(mineSeat) + List(6) { CrewSeat("o$it", "App $it", "Owner $it", "com.example.app$it", crew = "Crew 58") },
            day = 9,
        )

    private fun nights(
        from: Int,
        to: Int,
        missed: Set<Int> = emptySet(),
    ) = (from..to).map { PrivateNight(it, it !in missed) }

    private val results =
        PrivateResults(
            "s1",
            "Budget Buddy",
            14,
            listOf(
                PrivateTester(
                    "t1",
                    "s1",
                    "Lina Akter",
                    state = PrivateState.ACCEPTED,
                    firstNight = 3,
                    day = 9,
                    nights = nights(3, 9, setOf(6)),
                ),
                PrivateTester(
                    "t2",
                    "s1",
                    "Omar Faruk",
                    state = PrivateState.ACCEPTED,
                    firstNight = 6,
                    day = 9,
                    nights = nights(6, 9, setOf(9)),
                ),
                PrivateTester("t3", "s1", "Sam Rahman", email = "sam.rahman@gmail.com"),
            ),
        )

    private val invite =
        PrivateTest("a1", "s1", "c58", PrivateState.INVITED, "Budget Buddy", ownerName = "Hasan Rahman", crewName = "Crew 58")
    private val testing =
        PrivateTest(
            "a2",
            "s1",
            "c58",
            PrivateState.ACCEPTED,
            "Budget Buddy",
            "com.hasanapps.budgetbuddy",
            ownerName = "Hasan Rahman",
            crewName = "Crew 58",
            day = 9,
            firstNight = 5,
            runDays = 14,
            openedToday = true,
            openedDays = 4,
        )
    private val tide =
        testing.copy(id = "a3", appName = "Tide Times", packageName = "com.example.tide", ownerName = "Mina Das", openedToday = false)

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        screen = { key(name) { TestTrackTheme { Backdrop(Scene.CREW) { content() } } } }
        compose.mainClock.advanceTimeBy(1_500)
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun testers(
        results: PrivateResults?,
        fault: ResultsFault? = null,
    ): @Composable () -> Unit =
        {
            PrivateTestersBody("Budget Buddy", "Crew 58", 9, 5, results, fault, canAdd = results != null, {}, {}, {}, {})
        }

    private fun addSheet(
        busy: Boolean = false,
        refusal: InviteRefusal? = null,
    ): @Composable () -> Unit =
        {
            PrivateAddSheet("Budget Buddy", mineSeat.packageName, "", "Crew 58", busy, refusal, { _, _ -> }, {})
        }

    @Test
    fun draw_every_private_state() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        val vm = CrewViewModel()
        vm.preview(listOf(ownerCrew), tab = CrewPhase.RUN, installed = setOf(mineSeat.packageName))
        val privates = PrivateTestsViewModel()
        privates.preview(results = listOf(results))
        val run = { tests: List<PrivateTest> -> PrivateRunSection(tests, isInstalled = { it == mineSeat.packageName }) }
        val empty = CrewViewModel()
        empty.preview(emptyList(), tab = CrewPhase.RUN)

        shot("p1-crew-page-row") { CrewPage(vm, ownerCrew.id, onBack = {}, onSignIn = {}, privates = privates) }
        shot("p2-add-sending", addSheet(busy = true))
        shot("p4-no-account", addSheet(refusal = InviteRefusal("", InviteRefusal.NO_ACCOUNT, "No TestTrack account uses that email.")))
        shot(
            "p5-already-testing",
            addSheet(refusal = InviteRefusal("", InviteRefusal.ALREADY_TESTING, "That person already tests in this crew.")),
        )
        shot("p6-results", testers(results))
        shot("p7-empty", testers(results.copy(testers = emptyList())))
        shot("p8-loading", testers(null))
        shot("p8-offline", testers(null, ResultsFault("Couldn't reach TestTrack.", offline = true)))
        shot("p10-invite-row") {
            Column(Modifier.fillMaxSize()) {
                PrivateInviteRows(listOf(invite), {}, Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp))
                CrewRunTab(empty, {}, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), {}, {})
            }
        }
        shot("p11-invite-sheet") { PrivateInviteSheet(invite, busy = false, onAccept = {}, onDecline = {}, onDismiss = {}) }
        shot("p12-run-tab") { CrewRunTab(empty, {}, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), {}, {}, run(listOf(testing, tide))) }
        shot("p13-test-page") { PrivateTestBody(testing, false, installed = true, busy = false, {}, {}, {}, {}) }
        shot("p14-ended") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val section = run(emptyList())
                PrivateTestTile(testing.copy(state = PrivateState.ENDED, day = 14), section)
                PrivateTestTile(testing.copy(state = PrivateState.ENDED, day = 7), section)
                val declined = PrivateTester("t4", "s1", "Sam Rahman", state = PrivateState.DECLINED)
                TesterTile(privateTesterOf(declined, 14), onFace = {}, dayLabel = "")
            }
        }
    }
}
