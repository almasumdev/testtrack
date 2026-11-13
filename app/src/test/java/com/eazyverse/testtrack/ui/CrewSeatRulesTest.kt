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
    fun whatAnAppNeedsComesInOrder() {
        assertEquals(SeatNeed.GROUP, seatNeed(app(verifiedBy = null), both))
        assertEquals(SeatNeed.BOXES, seatNeed(app(), SeatBoxes(countries = true)))
        assertEquals(SeatNeed.BOXES, seatNeed(app(), SeatBoxes()))
        assertEquals(SeatNeed.NONE, seatNeed(app(), both))
        // Unverified wins over unticked: Play has to show it before the boxes mean anything.
        assertEquals(SeatNeed.GROUP, seatNeed(app(verifiedBy = null), SeatBoxes()))
    }

    @Test
    fun bothBoxesAreRequired() {
        assertFalse(SeatBoxes(countries = true).both)
        assertFalse(SeatBoxes(devices = true).both)
        assertTrue(both.both)
    }

    @Test
    fun theSeatDetailsAreAlwaysOneOfTheStepsLeft() {
        assertEquals(3, stepsLeft(verified = false, boxes = SeatBoxes()))
        assertEquals(2, stepsLeft(verified = true, boxes = SeatBoxes()))
        assertEquals(2, stepsLeft(verified = true, boxes = SeatBoxes(countries = true)))
        assertEquals(1, stepsLeft(verified = true, boxes = both))
        assertEquals("3 steps before it can go in", stepsLeftLine(3))
        assertEquals("1 step before it can go in", stepsLeftLine(1))
    }

    @Test
    fun theCheckLineSaysWhenAndWhat() {
        val now = 10_000_000L
        assertEquals("Not checked yet.", lastCheckLine(null, null, now))
        assertEquals(
            "Checked 4 min ago: Play doesn't show it to the group yet. A new rollout can take a few hours to show.",
            lastCheckLine(now - 4 * 60_000L, false, now),
        )
        assertEquals("Checked just now: Play shows it to the group.", lastCheckLine(now - 20_000L, true, now))
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
}
