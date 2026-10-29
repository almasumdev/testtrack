package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A crew's length is its own, and a member past it is finished there.
 *
 * The crew stays running until everyone's last night is over, so a member ahead in time zone used
 * to see "Day 15 of 14", apps to open and Open all, with every open refused. And 14 is only the
 * usual length: a crew the team extends runs 16 nights or more, so nothing may assume 14.
 */
class CrewRunDaysTest {
    private fun seat(
        id: String,
        mine: Boolean = false,
        state: String = "confirmed",
        missedYesterday: Boolean = false,
    ) = ApiSeat(
        id = id,
        crewId = "c1",
        appName = "App $id",
        packageName = "com.example.$id",
        ownerName = "Owner $id",
        state = state,
        mine = mine,
        missedYesterday = missedYesterday,
        createdAt = "2026-09-20T00:00:00+00:00",
    )

    private fun crew(
        day: Int,
        runDays: Int = 14,
        members: List<ApiSeat> = listOf(seat("me", mine = true), seat("a"), seat("b", state = "removed")),
    ) = ApiCrew(
        id = "c1",
        number = 7,
        name = "Crew 07",
        state = "running",
        filled = 15,
        taken = 15,
        seats = 15,
        runDays = runDays,
        day = day,
        createdAt = "2026-09-20T00:00:00+00:00",
        members = members,
    ).toCrew()

    @Test
    fun `an extended crew counts to its own length`() {
        val running = crew(day = 15, runDays = 16)
        assertFalse(running.nightsDone)
        assertEquals(16, running.ownNights)
        assertEquals("Day 15 of 16", running.standing)
        assertEquals(listOf("a"), running.apps.map { it.id })
    }

    @Test
    fun `past its last night a member has nothing to open there`() {
        val done = crew(day = 15)
        assertEquals(CrewPhase.RUN, done.phase)
        assertTrue(done.nightsDone)
        assertTrue(done.apps.isEmpty())
        assertTrue(done.left.isEmpty())
        assertTrue(tonightIn(listOf(done)).isEmpty())
        assertEquals("Your nights are done", done.standing)
    }

    @Test
    fun `the last night itself is still a night to open`() {
        val last = crew(day = 14)
        assertFalse(last.nightsDone)
        assertEquals(1, last.owed)
    }

    @Test
    fun `nights done are never at risk`() {
        val done = crew(day = 15, members = listOf(seat("me", mine = true, missedYesterday = true), seat("a")))
        assertFalse(done.atRisk)
    }

    @Test
    fun `an older server that sends no length is read as the usual fortnight`() {
        assertEquals(CrewRules.RUN_DAYS, crew(day = 3, runDays = 0).runDays)
    }

    @Test
    fun `the app list says a member's nights are done`() {
        val status = AppCrewStatus("testing", "c1", "Crew 07", day = 17, runDays = 16)
        assertEquals("Your nights in Crew 07 are done", crewBadge(status).text)
    }
}
