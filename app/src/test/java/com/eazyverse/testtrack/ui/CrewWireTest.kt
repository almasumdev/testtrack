package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The server's crews, turned into what the screens draw.
 *
 * Pure arithmetic over what came back, which is why it runs without a device. The rule it guards
 * is that nothing here decides anything: the day, the counts and whether a crew has filled are
 * the server's answers, and this only chooses which screen they land on.
 *
 * The case worth having is the phase. The server has three states and the app has four, because
 * "full" is two different situations for the person reading it: a crew that has filled but whose
 * own midnight has not arrived is a lobby, and the same crew an hour later is a run. Getting that
 * wrong puts somebody on a screen asking them to open fourteen apps before their run has started.
 */
class CrewWireTest {
    private fun seat(
        id: String = "s1",
        owner: String = "Rafiul",
        state: String = "confirmed",
        mine: Boolean = false,
        reported: Boolean = false,
        opened: Boolean = false,
        day: Int = 0,
        done: Int = 0,
        owed: Int = 0,
        nightDone: Boolean = false,
        missed: Int = 0,
        missedYesterday: Boolean = false,
        outReason: String = "",
    ) = ApiSeat(
        id = id,
        crewId = "c1",
        appName = "App $id",
        packageName = "com.example.$id",
        ownerName = owner,
        state = state,
        mine = mine,
        reported = reported,
        outReason = outReason,
        openedToday = opened,
        day = day,
        doneToday = done,
        owedToday = owed,
        nightDone = nightDone,
        missed = missed,
        missedYesterday = missedYesterday,
        createdAt = "2026-09-20T00:00:00+00:00",
    )

    private fun crew(
        state: String = "filling",
        day: Int = 0,
        startsIn: Long = 0,
        members: List<ApiSeat> = emptyList(),
    ) = ApiCrew(
        id = "c1",
        number = 7,
        name = "Crew 07",
        state = state,
        filled = members.count { it.state == "confirmed" },
        taken = members.count { it.state == "confirmed" || it.state == "checking" },
        seats = 15,
        runDays = 14,
        day = day,
        startsInMs = startsIn,
        mySeatId = members.firstOrNull { it.mine }?.id,
        createdAt = "2026-09-20T00:00:00+00:00",
        members = members,
    )

    // ------------------------------------------------------------------------- phase

    @Test
    fun a_crew_of_yours_still_filling_is_a_lobby() {
        assertEquals(CrewPhase.LOBBY, crewPhase(crew(state = "filling")))
    }

    @Test
    fun the_open_crew_seen_from_outside_is_open_whatever_its_state() {
        assertEquals(CrewPhase.OPEN, crewPhase(crew(state = "filling"), member = false))
    }

    @Test
    fun a_full_crew_before_your_midnight_is_still_a_lobby() {
        assertEquals(CrewPhase.LOBBY, crewPhase(crew(state = "full", day = 0, startsIn = 3_600_000)))
    }

    @Test
    fun a_full_crew_after_your_midnight_is_a_run() {
        assertEquals(CrewPhase.RUN, crewPhase(crew(state = "full", day = 1)))
    }

    @Test
    fun a_finished_crew_is_a_record() {
        assertEquals(CrewPhase.RECORD, crewPhase(crew(state = "done", day = 15)))
    }

    @Test
    fun an_app_sent_back_stays_in_the_lobby_even_if_its_crew_started() {
        val back = crew(state = "full", day = 2, members = listOf(seat("me", state = "rejected", mine = true)))
        val mapped = back.toCrew()
        assertEquals(CrewPhase.LOBBY, mapped.phase)
        assertTrue(mapped.out)
        assertEquals(listOf("me"), sentBackIn(listOf(mapped)).map { it.id })
    }

    // ------------------------------------------------------------------------- seats

    @Test
    fun the_server_state_is_the_seat_state() {
        assertEquals(SeatState.CONFIRMED, SeatState.of("confirmed"))
        assertEquals(SeatState.CHECKING, SeatState.of("checking"))
        assertEquals(SeatState.REJECTED, SeatState.of("rejected"))
        assertEquals(SeatState.REMOVED, SeatState.of("removed"))
    }

    @Test
    fun the_counts_come_from_the_server_not_from_the_list() {
        // The open crew carries no members at all; its counts are all the screen has.
        val open = crew(state = "filling").copy(filled = 9, taken = 11).toCrew(member = false)
        assertEquals(9, open.filled)
        assertEquals(11, open.taken)
        assertEquals("4 seats left", open.standing)
    }

    @Test
    fun a_lobby_says_how_many_more_or_how_long_to_midnight() {
        val filling = crew(state = "filling", members = listOf(seat("a"), seat("b", mine = true))).toCrew()
        assertEquals("Filling · 2 of 15", filling.standing)
        val waiting = crew(state = "full", startsIn = 2 * 3_600_000L + 5 * 60_000L).toCrew()
        assertEquals("Starts in 2h 5m", waiting.standing)
    }

    // ------------------------------------------------------------------------- the run

    @Test
    fun only_other_peoples_apps_still_in_the_run_are_owed() {
        val running =
            crew(
                state = "full",
                day = 3,
                members =
                    listOf(
                        seat("me", mine = true),
                        seat("a"),
                        seat("b", state = "removed"),
                        seat("c", opened = true),
                    ),
            ).toCrew()
        assertEquals(listOf("a", "c"), running.apps.map { it.id })
        assertEquals(1, running.owed)
        assertEquals(listOf("a"), tonightIn(listOf(running)).map { it.id })
        assertEquals(1, doneTonightIn(listOf(running)))
    }

    @Test
    fun a_reported_app_is_excused_not_owed() {
        val running =
            crew(state = "full", day = 3, members = listOf(seat("me", mine = true), seat("a", reported = true))).toCrew()
        assertTrue(running.apps.single().reported)
        assertEquals(0, running.owed)
        assertTrue(tonightIn(listOf(running)).isEmpty())
    }

    @Test
    fun once_your_app_is_out_nothing_is_owed_by_you() {
        val out =
            crew(state = "full", day = 5, members = listOf(seat("me", state = "removed", mine = true), seat("a"))).toCrew()
        assertTrue(out.out)
        assertTrue(out.apps.isEmpty())
        assertEquals("Your app is out", out.standing)
    }

    @Test
    fun each_member_row_is_that_members_own_day() {
        val running =
            crew(
                state = "full",
                day = 4,
                members =
                    listOf(
                        seat("me", mine = true, day = 4, done = 14, owed = 14, nightDone = true),
                        // Three hours behind: still on the day before, partway through.
                        seat("k", owner = "Kenji", day = 3, done = 5, owed = 14),
                    ),
            ).toCrew()
        val kenji = running.members.first { it.name == "Kenji" }
        assertEquals(3, kenji.day)
        assertEquals(5, kenji.doneToday)
        assertFalse(kenji.nightDone)
        assertTrue(running.members.first { it.mine }.nightDone)
    }

    @Test
    fun at_risk_means_yesterday_was_missed_not_any_miss_ever() {
        val once =
            crew(state = "full", day = 6, members = listOf(seat("me", mine = true, missed = 1))).toCrew()
        assertFalse(once.atRisk)
        val yesterday =
            crew(state = "full", day = 6, members = listOf(seat("me", mine = true, missed = 1, missedYesterday = true)))
                .toCrew()
        assertTrue(yesterday.atRisk)
    }

    @Test
    fun a_member_taken_out_is_kept_and_marked_out() {
        val running =
            crew(state = "full", day = 6, members = listOf(seat("me", mine = true), seat("x", state = "removed"))).toCrew()
        assertTrue(running.members.first { it.id == "x" }.out)
        assertEquals(1, running.stayed)
    }

    // ------------------------------------------------------------------------- holding

    @Test
    fun the_waiting_app_is_named_and_a_running_crew_holds_nothing() {
        val filling = crew(state = "filling", members = listOf(seat("me", mine = true))).toCrew()
        val running = crew(state = "full", day = 2, members = listOf(seat("me2", mine = true))).toCrew()
        assertEquals("Crew 07", holdingIn(listOf(filling, running)))
        assertNull(holdingIn(listOf(running)))
    }

    @Test
    fun a_record_counts_the_fortnight_not_how_long_ago_it_filled() {
        val done = crew(state = "done", day = 20, members = listOf(seat("me", mine = true))).toCrew()
        assertEquals(14, done.finishedDays)
    }
}
