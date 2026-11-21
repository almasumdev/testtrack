package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.eazyverse.testtrack.viewmodel.SpotState
import com.eazyverse.testtrack.viewmodel.VolunteerSpot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The volunteer states, drawn by the real screens and saved as pictures in files/gallery/volunteers,
 * as CrewGalleryTest does for the rest of Crew. Nothing is asked of the server: the crews go in
 * through [CrewViewModel.preview].
 */
@RunWith(AndroidJUnit4::class)
class VolunteerGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val out: File by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), "gallery/volunteers").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private val names = listOf("Budget Buddy", "Astra Match", "Baka Run", "Cherish", "EditMe", "Auto Silent", "Music Player")

    private fun seats(crew: String) =
        names.mapIndexed { i, app ->
            CrewSeat(id = "$crew-$i", appName = app, owner = "Owner $i", packageName = "com.example.app$i", crew = crew)
        }

    private fun volunteerCrew(
        name: String,
        day: Int,
        firstNight: Int,
        state: String = "active",
        opened: Int = 2,
        paid: Int = 2,
    ): Crew {
        val seats = seats(name)
        val apps = seats.mapIndexed { i, s -> CrewApp(s.id, s.appName, s.packageName, s.owner, name, done = i < opened, day = day) }
        val waiting = day < firstNight
        val ended = state != "active"
        return Crew(
            id = name,
            name = name,
            phase = if (waiting) CrewPhase.LOBBY else CrewPhase.RUN,
            taken = 15,
            filled = 15,
            seats = seats,
            apps = if (waiting || ended) emptyList() else apps,
            strip = if (waiting) emptyList() else apps,
            day = day,
            firstNight = firstNight,
            startsIn = 6 * 3_600_000L + 20 * 60_000L,
            volunteer =
                CrewVolunteer(
                    "v-$name",
                    state,
                    nightsPaid = paid,
                    tixEarned = paid * CrewRules.VOLUNTEER_NIGHT_TIX,
                    endReason = if (state == "out") "Night 5 wasn't finished. Pixel Pets wasn't opened." else "",
                    lastNight = if (ended) 4 else null,
                ),
        )
    }

    private val spots =
        listOf(
            VolunteerSpot("c58", "Crew 58", 6, 14, 2, List(13) { "" }, 7, 40, SpotState.OPEN),
            VolunteerSpot("c61", "Crew 61", 11, 14, 1, List(12) { "" }, 12, 15, SpotState.OPEN),
            VolunteerSpot("c55", "Crew 55", 3, 14, 0, List(13) { "" }, 4, 55, SpotState.FULL),
            VolunteerSpot("c57", "Crew 57", 9, 14, 0, List(13) { "" }, 10, 25, SpotState.VOLUNTEERING),
            VolunteerSpot("c60", "Crew 60", 11, 14, 0, List(12) { "" }, 12, 15, SpotState.MEMBER),
            VolunteerSpot("c49", "Crew 49", 8, 14, 0, List(13) { "" }, 9, 30, SpotState.OUT),
        )

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

    @Test
    fun draw_every_volunteer_state() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }

        val waiting = volunteerCrew("Crew 58", day = 6, firstNight = 7, opened = 0, paid = 0)
        val running = volunteerCrew("Crew 57", day = 9, firstNight = 7)
        val out = volunteerCrew("Crew 49", day = 6, firstNight = 1, state = "out", paid = 4)
        val left = volunteerCrew("Crew 50", day = 6, firstNight = 1, state = "left", paid = 3)
        val vm = CrewViewModel()
        vm.preview(listOf(waiting, running, out, left), tab = CrewPhase.RUN, installed = setOf("com.example.app0", "com.example.app1"))

        shot("v1-open-tab") {
            CrewOpenTab(vm, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), volunteers = VolunteerSection(spots))
        }
        shot("v1-open-empty") {
            CrewOpenTab(vm, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), volunteers = VolunteerSection(emptyList()))
        }
        shot("v1-open-loading") {
            CrewOpenTab(vm, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), volunteers = VolunteerSection(null))
        }
        shot("v2-join-sheet") { VolunteerJoinSheet(spots[0], busy = false, onJoin = {}, onDismiss = {}) }
        shot("v3-rows") {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spots.drop(2).forEach { VolunteerSpotRow(it, {}, {}) }
            }
        }
        shot("v4-before-first-night") { CrewPage(vm, waiting.id, onBack = {}, onSignIn = {}) }
        shot("v5-night") { CrewPage(vm, running.id, onBack = {}, onSignIn = {}) }
        shot("v6-run-tab") { CrewRunTab(vm, {}, CrewActions({}, {}, {}, {}, {}, {}, { _, _ -> }), {}, {}) }
        shot("v8-out") { CrewPage(vm, out.id, onBack = {}, onSignIn = {}) }
        shot("v8-left") { CrewPage(vm, left.id, onBack = {}, onSignIn = {}) }
    }
}
