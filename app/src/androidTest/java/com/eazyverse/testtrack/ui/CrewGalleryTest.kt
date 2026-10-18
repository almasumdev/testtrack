package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Every state of the Crew tab and of a crew's own page, drawn by the real screens and saved as
 * pictures, so the whole of it can be looked at side by side.
 *
 * Not an assertion test: it writes PNGs to the app's files/gallery folder for a person to look at.
 * The crews are put in with [CrewViewModel.preview], so nothing is asked of the server.
 */
@RunWith(AndroidJUnit4::class)
class CrewGalleryTest {
    @get:Rule
    val compose = createComposeRule()

    private val out: File by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), "gallery").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    // ------------------------------------------------------------------ sample crews

    private val others =
        listOf(
            "Nikosh Budget" to "Rafiul",
            "Habit Garden" to "Nadia",
            "Tiny Tasks" to "Habib",
            "Pocket Recipes" to "Tanvir",
            "Calm Breath" to "Sumi",
            "Quick Notes" to "Arif",
            "Run Tracker" to "Mitu",
            "Word Daily" to "Jamal",
            "Plant Pal" to "Rina",
            "Study Timer" to "Sabbir",
            "Bill Split" to "Tania",
            "Mood Log" to "Kamal",
            "Weather Lite" to "Lima",
            "Photo Sort" to "Faisal",
        )

    private fun mine(
        crew: String,
        state: SeatState = SeatState.CONFIRMED,
        reports: Int = 0,
        reasons: List<String> = emptyList(),
        outReason: String = "",
        yes: Int = 0,
    ) = CrewSeat(
        id = "me-$crew",
        appName = "Time Lapse: Stopwatch & Timer",
        owner = "You",
        packageName = "com.eazyverse.timelapse",
        notes = "Username: demo@timelapse.app\nPassword: demo1234",
        crew = crew,
        state = state,
        mine = true,
        reports = reports,
        reasons = reasons,
        outReason = outReason,
        yes = yes,
    )

    private fun seat(
        crew: String,
        i: Int,
        state: SeatState = SeatState.CONFIRMED,
        reported: Boolean = false,
        notes: String = "",
        yes: Int = 0,
        myVote: Boolean? = null,
    ): CrewSeat {
        val (app, owner) = others[i]
        return CrewSeat(
            id = "$crew-$i",
            appName = app,
            owner = owner,
            packageName = "com.example.${app.lowercase().replace(" ", "")}",
            notes = notes,
            crew = crew,
            state = state,
            reported = reported,
            yes = yes,
            no = if (myVote == false) 1 else 0,
            myVote = myVote,
        )
    }

    private fun lobby(
        name: String,
        mySeat: CrewSeat,
        count: Int,
        filling: Boolean = true,
        startsIn: Long = 0L,
    ): Crew {
        val seats =
            listOf(mySeat) +
                (0 until count).map { i ->
                    when (i) {
                        2 -> seat(name, i, notes = "Code: TESTER-2026")
                        4 -> seat(name, i, SeatState.CHECKING, yes = 1)
                        6 -> seat(name, i, reported = true)
                        else -> seat(name, i)
                    }
                }
        val taken = seats.count { it.state != SeatState.REJECTED }
        return Crew(
            id = name,
            name = name,
            phase = CrewPhase.LOBBY,
            filling = filling,
            taken = taken,
            filled = seats.count { it.state == SeatState.CONFIRMED },
            seats = seats,
            startsIn = startsIn,
        )
    }

    private fun run(
        name: String,
        day: Int,
        doneCount: Int,
        mySeat: CrewSeat = mine(name),
        missedYesterday: Boolean = false,
        myMisses: Int = 0,
    ): Crew {
        val seats = listOf(mySeat) + others.indices.map { seat(name, it) }
        val apps =
            others.indices.map { i ->
                val (app, owner) = others[i]
                CrewApp(
                    id = "$name-$i",
                    label = app,
                    packageName = "com.example.${app.lowercase().replace(" ", "")}",
                    owner = owner,
                    crew = name,
                    done = i < doneCount,
                    reported = i == 13 && doneCount < 14,
                )
            }
        val members =
            listOf(
                CrewMember(
                    "me",
                    "You",
                    mySeat.appName,
                    day = day,
                    doneToday = doneCount,
                    owedToday = 14,
                    nightDone = doneCount >= 14,
                    missed = myMisses,
                    missedYesterday = missedYesterday,
                    mine = true,
                    out = mySeat.state == SeatState.REMOVED,
                ),
            ) +
                others.indices.map { i ->
                    val (app, owner) = others[i]
                    CrewMember(
                        "m$i",
                        owner,
                        app,
                        day = day - if (i % 5 == 0) 1 else 0,
                        doneToday = (i * 3) % 15,
                        owedToday = 14,
                        nightDone = i % 3 == 0,
                        missed = if (i == 7) 1 else 0,
                        missedYesterday = i == 7,
                        out = i == 11,
                        openedMineToday = i % 4 != 1 && i != 11,
                        openedMineAt = "2026-10-18T${18 + i % 5}:${10 + i * 3}:00+06:00",
                        openedMineDays =
                            if (i == 11) {
                                3
                            } else if (i == 7) {
                                day - 2
                            } else {
                                day - if (i % 4 == 1) 1 else 0
                            },
                    )
                }
        return Crew(
            id = name,
            name = name,
            phase = CrewPhase.RUN,
            taken = 15,
            filled = 15,
            seats = seats,
            members = members,
            apps = apps,
            day = day,
        )
    }

    private fun record(
        name: String,
        outCount: Int,
    ): Crew {
        val members =
            listOf(CrewMember("me", "You", "Time Lapse: Stopwatch & Timer", day = 14, mine = true, missed = 1)) +
                others.indices.map { i ->
                    val (app, owner) = others[i]
                    CrewMember(
                        "m$i",
                        owner,
                        app,
                        day = 14,
                        missed = if (i < outCount) 2 else i % 2,
                        out = i < outCount,
                        openedMineDays =
                            if (i < outCount) {
                                4 + i
                            } else if (i % 6 == 5) {
                                13
                            } else {
                                14
                            },
                    )
                }
        return Crew(
            id = name,
            name = name,
            phase = CrewPhase.RECORD,
            taken = 15,
            filled = 15,
            seats = listOf(mine(name)),
            members = members,
            finishedDays = 14,
        )
    }

    private fun open(
        taken: Int,
        name: String = "Crew 05",
    ) = Crew(id = name, name = name, phase = CrewPhase.OPEN, filling = true, taken = taken, filled = taken - 1)

    // ------------------------------------------------------------------ drawing

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private fun save(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Taps [button] on the screen showing, saves the dialog it opens, then closes it with [close]. */
    private fun dialog(
        name: String,
        button: String,
        close: String = "Cancel",
    ) {
        compose.onAllNodesWithText(button)[0].performClick()
        settle()
        // The whole screen: a dialog is a window of its own, which a capture of one node misses.
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onAllNodesWithText(close)[0].performClick()
        settle()
    }

    /** Taps [text], first swiping the list up until it is fully on screen. */
    private fun tap(text: String) {
        val screenHeight =
            compose
                .onRoot()
                .fetchSemanticsNode()
                .size.height
        repeat(6) {
            val found = compose.onAllNodesWithText(text).fetchSemanticsNodes()
            if (found.isNotEmpty() && found[0].boundsInRoot.bottom < screenHeight - 200) {
                compose.onAllNodesWithText(text)[0].performClick()
                settle()
                return
            }
            compose.onAllNodes(hasScrollToIndexAction())[0].performTouchInput { swipeUp(startY = centerY, endY = top + 50f) }
            settle()
        }
        error("$text never came on screen")
    }

    /** Taps a row, saves the sheet it opens, then closes it with Back. */
    private fun sheet(
        name: String,
        row: String,
    ) {
        tap(row)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Espresso.pressBack()
        settle()
    }

    /** Animations run out on the test clock, then the real screen is given time to draw them. */
    private fun settle() {
        repeat(4) {
            compose.mainClock.advanceTimeBy(500)
            Thread.sleep(250)
        }
    }

    private fun shot(
        name: String,
        scrolled: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        // Keyed by the shot, so each is a fresh screen rather than the last one scrolled.
        screen = { key(name) { TestTrackTheme { Backdrop(Scene.CREW) { content() } } } }
        compose.mainClock.advanceTimeBy(1_500)
        save(name)
        if (scrolled) {
            val list = compose.onAllNodes(hasScrollToIndexAction())
            if (list.fetchSemanticsNodes().isNotEmpty()) {
                repeat(6) { list[0].performTouchInput { swipeUp() } }
                compose.mainClock.advanceTimeBy(1_000)
                save("$name-end")
            }
        }
    }

    private fun tab(
        name: String,
        vm: CrewViewModel,
        scrolled: Boolean = false,
    ) = shot(name, scrolled) { CrewScreen(vm = vm) }

    private fun page(
        name: String,
        vm: CrewViewModel,
        id: String,
        scrolled: Boolean = false,
    ) = shot(name, scrolled) { CrewPage(vm, id, onBack = {}, onSignIn = {}, onAddToCrew = {}) }

    @Test
    fun draw_every_crew_state() {
        // The run screens tick every second, so the clock is moved by hand rather than waited out.
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }

        val filling = lobby("Crew 04", mine("Crew 04"), 8)
        val checking = lobby("Crew 06", mine("Crew 06", SeatState.CHECKING, yes = 1), 5)
        val reported = lobby("Crew 07", mine("Crew 07", reports = 1, reasons = listOf("The login screen says the password is wrong.")), 9)
        val full = lobby("Crew 08", mine("Crew 08"), 14, filling = false, startsIn = 5 * 3_600_000L + 12 * 60_000L)
        val sentBack =
            lobby(
                "Crew 09",
                mine(
                    "Crew 09",
                    SeatState.REJECTED,
                    reasons = listOf("It will not install: Play says it isn't available.", "Same here, the testers link shows nothing."),
                    outReason = "Two members said it would not install.",
                ),
                9,
            )
        val reportedOut =
            lobby(
                "Crew 12",
                mine(
                    "Crew 12",
                    SeatState.REJECTED,
                    reports = 2,
                    reasons = listOf("The login screen says the password is wrong.", "Stuck on a blank screen after sign in."),
                    outReason = "Two members reported it before the run.",
                ),
                9,
            )
        val playOut =
            lobby(
                "Crew 13",
                mine("Crew 13", SeatState.REJECTED, outReason = "Play stopped showing it to the testers group."),
                9,
            )
        val asking =
            filling.copy(
                id = "Crew 14",
                seats =
                    listOf(
                        mine("Crew 14"),
                        seat("Crew 14", 4, SeatState.CHECKING, yes = 1, notes = "Username: test@calmbreath.app\nPassword: breathe24"),
                        seat("Crew 14", 5, SeatState.CHECKING, yes = 1),
                        seat("Crew 14", 6, SeatState.CHECKING, yes = 2, myVote = true),
                        seat("Crew 14", 7, SeatState.CHECKING, yes = 1, myVote = false),
                        seat("Crew 14", 2, notes = "Code: TESTER-2026"),
                        seat("Crew 14", 3),
                        seat("Crew 14", 1),
                    ),
            )
        val running = run("Crew 02", day = 6, doneCount = 4)
        val risky = run("Crew 03", day = 9, doneCount = 11, missedYesterday = true, myMisses = 1)
        val doneTonight = run("Crew 10", day = 11, doneCount = 14)
        val removed =
            run("Crew 11", day = 8, doneCount = 0, mySeat = mine("Crew 11", SeatState.REMOVED, outReason = "It missed two days in a row."))
        val finished = record("Crew 01", outCount = 2)
        val short = record("Crew 00", outCount = 5)

        val vm = CrewViewModel()
        val pkg = { i: Int -> "com.example.${others[i].first.lowercase().replace(" ", "")}" }
        // In the lobby, Habit Garden is installed and so is one asked app; in a run, all but Weather Lite.
        val lobbyPhone = setOf(pkg(1), pkg(5), "com.eazyverse.timelapse")
        val runPhone = others.indices.map(pkg).toSet() - pkg(12) + "com.eazyverse.timelapse"

        // The Crew tab, each of its four, in each of their states.
        vm.preview(emptyList(), open = open(taken = 9))
        tab("01-open-tab", vm, scrolled = true)
        vm.preview(emptyList(), open = open(taken = 13))
        tab("02-open-tab-starts-soonest", vm)
        vm.preview(listOf(filling), open = open(taken = 9, name = "Crew 04"), tab = CrewPhase.OPEN)
        tab("03-open-tab-you-are-in", vm)
        vm.preview(emptyList(), open = null)
        tab("04-open-tab-none-filling", vm)
        vm.preview(listOf(filling), open = open(taken = 9), blocked = "One app waiting at a time. Put the next one in when Crew 04 starts.")
        tab("05-open-tab-blocked", vm)
        vm.preview(emptyList(), open = open(taken = 9), signedOut = true)
        tab("06-open-tab-signed-out", vm)
        vm.preview(listOf(filling, checking, full, sentBack), tab = CrewPhase.LOBBY)
        tab("07-lobby-tab", vm)
        vm.preview(emptyList(), tab = CrewPhase.LOBBY)
        tab("08-lobby-tab-empty", vm)
        vm.preview(listOf(running, risky), tab = CrewPhase.RUN)
        tab("09-run-tab", vm, scrolled = true)
        vm.preview(listOf(doneTonight), tab = CrewPhase.RUN)
        tab("10-run-tab-all-done", vm)
        vm.preview(emptyList(), tab = CrewPhase.RUN)
        tab("11-run-tab-empty", vm)
        vm.preview(listOf(finished, short), tab = CrewPhase.RECORD)
        tab("12-record-tab", vm)
        vm.preview(emptyList(), tab = CrewPhase.RECORD)
        tab("13-record-tab-empty", vm)
        vm.preview(emptyList(), signedOut = true, tab = CrewPhase.RUN)
        tab("14-run-tab-signed-out", vm)

        // A crew's own page, at every point a crew of yours can reach.
        val all =
            listOf(
                asking,
                filling,
                checking,
                reported,
                full,
                sentBack,
                reportedOut,
                playOut,
                running,
                risky,
                doneTonight,
                removed,
                finished,
                short,
            )
        vm.preview(all, tab = CrewPhase.LOBBY, installed = lobbyPhone)
        page("21-page-lobby-filling", vm, filling.id, scrolled = true)
        page("22-page-lobby-your-app-checking", vm, checking.id)
        page("23-page-lobby-your-app-reported", vm, reported.id)
        page("24-page-lobby-full-waiting", vm, full.id, scrolled = true)
        page("25-page-lobby-sent-back", vm, sentBack.id)
        page("25b-page-lobby-sent-back-reports", vm, reportedOut.id)
        page("25c-page-lobby-sent-back-play", vm, playOut.id)

        // The three questions a crew's page asks, over the page that asks them.
        page("40-page-lobby-asked-steps", vm, asking.id, scrolled = true)
        page("40-dialogs-base", vm, asking.id)
        sheet("44-sheet-with-sign-in", "Tiny Tasks")
        sheet("45-sheet-asked", "Calm Breath")
        tap("Calm Breath")
        dialog("42-dialog-it-didnt", "It won't install")
        tap("Pocket Recipes")
        dialog("41-dialog-report", "Report a problem")
        dialog("43-dialog-take-it-out", "Take it back out", close = "Keep it in")
        vm.preview(all, tab = CrewPhase.RUN, installed = runPhone)
        page("26-page-run", vm, running.id, scrolled = true)
        page("27-page-run-at-risk", vm, risky.id)
        page("28-page-run-tonight-done", vm, doneTonight.id)
        page("29-page-run-your-app-out", vm, removed.id)
        page("30-page-record", vm, finished.id, scrolled = true)
        page("31-page-record-below-twelve", vm, short.id)
        shot("32-members-run") { CrewMembersPage(vm, running.id, onBack = {}) }
        shot("33-members-record") { CrewMembersPage(vm, finished.id, onBack = {}) }
        page("34-sheet-tonight-base", vm, running.id)
        sheet("34-sheet-tonight", "Quick Notes")
    }

    /** The Members page from top to bottom, on its own so a long list cannot stall the rest. */
    @Test
    fun draw_the_members_page() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        val running = run("Crew 02", day = 6, doneCount = 4)
        val vm = CrewViewModel()
        vm.preview(listOf(running), tab = CrewPhase.RUN)
        screen = { key("m") { TestTrackTheme { Backdrop(Scene.CREW) { CrewMembersPage(vm, running.id, onBack = {}) } } } }
        settle()
        val shoot = { name: String ->
            val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        shoot("32-members-run")
        repeat(2) {
            compose.onAllNodes(hasScrollToIndexAction())[0].performTouchInput { swipeUp(startY = bottom - 100f, endY = top + 100f) }
            settle()
        }
        shoot("32-members-run-end")
    }
}
