package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiApp
import com.eazyverse.testtrack.viewmodel.toSeatFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the seat sheet decides: who can go in, what an app still needs, and what the check line says. */
class CrewSeatRulesTest {
    private fun app(
        live: Boolean = false,
        verifiedBy: String? = "play",
        canCrew: Boolean = true,
        crew: AppCrewStatus? = null,
    ) = AppEntry(
        id = "a",
        pkg = "com.example.app",
        name = "Sunrise Notes",
        live = live,
        verifiedBy = verifiedBy,
        canCrew = canCrew,
        crew = crew,
    )

    private val both = SeatBoxes(countries = true, devices = true)

    @Test
    fun anAppThatCanTakeASeatHasNoBlock() {
        assertNull(seatBlock(app(), null, null))
    }

    @Test
    fun aLiveAppNeverGoesIn() {
        assertEquals(SeatBlock(SeatBlockKind.LIVE), seatBlock(app(live = true, canCrew = false), null, null))
        assertEquals("Live on Play, so it can't go in", SeatBlock(SeatBlockKind.LIVE).line)
    }

    @Test
    fun anAppInACrewWaitsThere() {
        val block = seatBlock(app(canCrew = false, crew = AppCrewStatus("waiting", "c", "Crew 06")), null, null)
        assertEquals(SeatBlock(SeatBlockKind.WAITING, "Crew 06"), block)
        assertEquals("Waits in Crew 06", block?.line)
    }

    @Test
    fun anAppTakenOutWithAnAppealOpenSaysSo() {
        val block = seatBlock(app(crew = AppCrewStatus("removed", "c", "Crew 05")), null, "Crew 05")
        assertEquals("Taken out of Crew 05, appeal open", block?.line)
    }

    @Test
    fun theServersReasonWinsOverWhatTheAppSays() {
        val facts = SeatFacts(blockReason = "waiting", blockCrew = "Crew 09")
        assertEquals("Waits in Crew 09", seatBlock(app(), facts, null)?.line)
        assertEquals("Live on Play, so it can't go in", seatBlock(app(), SeatFacts(blockReason = "live"), null)?.line)
        assertEquals(
            "Taken out of Crew 05, appeal open",
            seatBlock(app(), SeatFacts(blockReason = "appeal", blockCrew = "Crew 05"), null)?.line,
        )
    }

    @Test
    fun anUnnamedCrewIsStillSaidInWords() {
        assertEquals("Waits in its crew", SeatBlock(SeatBlockKind.WAITING).line)
    }

    @Test
    fun anAppWhosGroupIsSetIsReadyAndTheBoxesAreNotAskedInThePicker() {
        assertEquals(SeatNeed.GROUP, seatNeed(app(verifiedBy = null)))
        assertEquals(SeatNeed.NONE, seatNeed(app()))
        assertEquals("Ready to go in", seatNeed(app()).line())
        assertEquals("Add the group to its closed track", seatNeed(app(verifiedBy = null)).line())
    }

    @Test
    fun bothBoxesAreRequired() {
        assertFalse(SeatBoxes(countries = true).both)
        assertFalse(SeatBoxes(devices = true).both)
        assertTrue(both.both)
    }

    @Test
    fun theCheckLineSaysWhenAndWhat() {
        val now = 10_000_000L
        assertNull(lastCheckLine(null, null, now))
        assertEquals(
            "Checked 4 min ago: Play doesn't show it to the group yet. A new rollout can take a few hours to show.",
            lastCheckLine(now - 4 * 60_000L, false, now),
        )
        assertNull(lastCheckLine(now - 20_000L, true, now))
    }

    @Test
    fun aRefusedSeatsOwnWordsReplaceTheStockLine() {
        assertEquals("Add the group first.", lastCheckLine(null, null, 0L, message = "Add the group first."))
    }

    @Test
    fun agoIsSaidInTheLargestWholeUnit() {
        assertEquals("just now", agoWords(30_000L))
        assertEquals("1 min ago", agoWords(60_000L))
        assertEquals("59 min ago", agoWords(59 * 60_000L))
        assertEquals("3 h ago", agoWords(3 * 3_600_000L))
        assertEquals("1 day ago", agoWords(30 * 3_600_000L))
        assertEquals("3 days ago", agoWords(72 * 3_600_000L))
        assertEquals("just now", agoWords(-5_000L))
    }

    @Test
    fun anOlderServerKeepsNoBoxesAndTheFieldsAreNull() {
        val facts = ApiApp(id = "a", packageName = "p", name = "n").toSeatFacts()
        assertNull(facts.boxes)
        assertNull(facts.checkedAt)
        assertNull(facts.blockReason)
    }

    @Test
    fun aNewerServerAnswersWithItsOwn() {
        val facts =
            ApiApp(
                id = "a",
                packageName = "p",
                name = "n",
                allCountries = true,
                allDevices = false,
                lastCheckAt = "2026-11-13T10:00:00Z",
                lastCheckOk = false,
                crewBlockReason = "waiting",
                crewBlockCrew = "Crew 06",
            ).toSeatFacts()
        assertEquals(SeatBoxes(countries = true, devices = false), facts.boxes)
        assertNotNull(facts.checkedAt)
        assertEquals(false, facts.checkedOk)
        assertEquals("waiting", facts.blockReason)
        assertEquals("Crew 06", facts.blockCrew)
    }

    @Test
    fun anAppealWaitingInACrewIsFoundByPackage() {
        val seat =
            CrewSeat(
                id = "s",
                appName = "Sunrise Notes",
                owner = "me",
                packageName = "com.example.app",
                mine = true,
                appeal = SeatAppeal("waiting"),
            )
        val crew = Crew(id = "c", name = "Crew 05", phase = CrewPhase.RUN, seats = listOf(seat))
        assertEquals("Crew 05", appealCrewFor(listOf(crew), "com.example.app"))
        assertNull(appealCrewFor(listOf(crew), "com.example.other"))
    }

    /** Everything done: the baseline each hint test changes one thing in. */
    private fun hint(
        groupReady: Boolean = true,
        groupFailed: Boolean = false,
        name: String = "Sunrise Notes",
        signInNeeded: Boolean = false,
        user: String = "",
        pass: String = "",
        also: String = "",
        tix: Int? = 240,
        shortServer: Boolean = false,
        held: Boolean = false,
        boxesReady: Boolean = true,
        busy: Boolean = false,
    ) = seatHint(groupReady, groupFailed, name, signInNeeded, user, pass, also, tix, shortServer, held, boxesReady, busy, 100)

    @Test
    fun theHintSaysWhatTheSeatTakesWhenNothingIsMissing() {
        assertEquals(SeatHint(SeatHintKind.READY, "100 tix come off your 240", null), hint())
        assertEquals("100 tix come off your balance", hint(tix = null).text)
    }

    @Test
    fun theGroupComesFirstAndAFailedCheckIsAWarning() {
        val first = hint(groupReady = false, name = "", boxesReady = false, tix = 10)
        assertEquals(SeatHint(SeatHintKind.TODO, "Verify the testers group first", SeatSpot.GROUP), first)
        val failed = hint(groupReady = false, groupFailed = true)
        assertEquals(SeatHint(SeatHintKind.WARN, "Play doesn't show it to the group yet", SeatSpot.GROUP), failed)
    }

    @Test
    fun theNameThenTheSignInAreAskedInOrder() {
        assertEquals(SeatHint(SeatHintKind.TODO, "Give it a name the crew sees", SeatSpot.DETAILS), hint(name = "  ", tix = 10))
        assertEquals("Add the test account's username", hint(signInNeeded = true, pass = "x").text)
        assertEquals("Add the test account's password", hint(signInNeeded = true, user = "u").text)
        // A free note stands in for both, as the button's own rule allows.
        assertEquals(SeatHintKind.READY, hint(signInNeeded = true, also = "skip the intro").kind)
        // A sign-in that is not needed is never asked for.
        assertEquals(SeatHintKind.READY, hint(signInNeeded = false).kind)
    }

    @Test
    fun shortTixWarnsWithHowManyOrWithTheServersWord() {
        assertEquals(SeatHint(SeatHintKind.WARN, "You need 40 more tix", SeatSpot.DETAILS), hint(tix = 60, boxesReady = false))
        assertEquals(SeatHint(SeatHintKind.WARN, "Not enough tix for a seat", SeatSpot.DETAILS), hint(shortServer = true))
        assertEquals(SeatHintKind.READY, hint(tix = 100).kind)
    }

    @Test
    fun anAppealHoldsTheSeatBeforeTheBoxesAreAsked() {
        assertEquals(
            SeatHint(SeatHintKind.WARN, "Wait for the team to answer your appeal", SeatSpot.DETAILS),
            hint(held = true, boxesReady = false),
        )
        assertEquals(SeatHint(SeatHintKind.TODO, "Tick both Play Console boxes", SeatSpot.BOXES), hint(boxesReady = false))
    }

    @Test
    fun whileTheSeatIsBeingCheckedTheHintOnlySaysSo() {
        val busy = hint(busy = true)
        assertEquals(SeatHintKind.BUSY, busy.kind)
        assertEquals("Checking it on Play the way the crew will see it", busy.text)
        assertNull(busy.spot)
    }
}
