package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiAppeal
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
        assertEquals("Filling, 2 of 15", filling.standing)
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

    @Test
    fun a_record_counts_the_fortnight_not_how_long_ago_it_filled() {
        val done = crew(state = "done", day = 20, members = listOf(seat("me", mine = true))).toCrew()
        assertEquals(14, done.finishedDays)
    }

    @Test
    fun only_a_member_whose_app_is_in_may_answer_and_only_while_filling() {
        val waiting = seat("w", state = "checking")
        val inCrew = crew(state = "filling", members = listOf(seat("me", mine = true), waiting)).toCrew()
        assertTrue(inCrew.mayVote)
        val stillWaiting = crew(state = "filling", members = listOf(seat("me", state = "checking", mine = true), waiting)).toCrew()
        assertFalse(stillWaiting.mayVote)
        val full = crew(state = "full", members = listOf(seat("me", mine = true), waiting)).toCrew()
        assertFalse(full.mayVote)
    }

    @Test
    fun an_open_crew_carries_its_seats_icons_and_a_seat_its_own() {
        val open = crew(members = emptyList()).copy(taken = 2, filled = 1, icons = listOf("https://play/a", "")).toCrew(member = false)
        assertEquals(listOf("https://play/a", ""), open.icons)
        val seat = seat().copy(icon = "https://play/b").toSeat("Crew 07")
        assertEquals("https://play/b", seat.icon)
    }

    // ------------------------------------------------------------------------- joining part-way

    @Test
    fun an_app_put_in_part_way_is_not_owed_before_its_first_night() {
        val late = seat("late").copy(firstNight = 7)
        val tonight = crew(state = "full", day = 6, members = listOf(seat("me", mine = true), seat("a"), late)).toCrew()
        assertEquals(listOf("a"), tonight.apps.map { it.id })
        val next = crew(state = "full", day = 7, members = listOf(seat("me", mine = true), seat("a"), late)).toCrew()
        assertEquals(listOf("a", "late"), next.apps.map { it.id })
    }

    @Test
    fun a_late_joiner_counts_its_own_days_not_the_crews() {
        val running = crew(state = "full", day = 9, members = listOf(seat("me", mine = true))).copy(firstNight = 6).toCrew()
        assertEquals(9, running.ownNights)
        assertEquals(4, running.ownDay)
        assertEquals("Day 4 of 9", running.standing)
        val done = crew(state = "done", day = 20, members = listOf(seat("me", mine = true))).copy(firstNight = 6).toCrew()
        assertEquals(9, done.finishedDays)
    }

    @Test
    fun a_seat_from_the_start_is_unchanged() {
        val running = crew(state = "full", day = 3, members = listOf(seat("me", mine = true), seat("a"))).toCrew()
        assertEquals(1, running.firstNight)
        assertEquals("Day 3 of 14", running.standing)
        assertEquals(listOf("a"), running.apps.map { it.id })
    }

    // ------------------------------------------------------------------------- what came back

    @Test
    fun a_refund_says_what_really_came_back() {
        val paid = seat("me", mine = true).copy(charged = 100, refunded = 100).toSeat()
        assertEquals("Your 100 tix are back.", seatRefundLine(paid))
        val free = seat("me", mine = true).copy(charged = 0, refunded = 0).toSeat()
        assertTrue(seatRefundLine(free).contains("placed free"))
        assertTrue(seatOutLine(free).contains("placed free"))
        val kept = seat("me", mine = true).copy(charged = 100, refunded = 0).toSeat()
        assertTrue(seatOutLine(kept).contains("aren't refunded"))
        // An older server says nothing: the usual price, as before.
        assertEquals("Your 100 tix are back.", seatRefundLine(seat("me", mine = true).toSeat()))
    }

    @Test
    fun a_review_stopped_by_others_does_not_blame_its_reviewer() {
        assertTrue(reviewOutcome("abandoned").startsWith("You gave this one up"))
        assertTrue(reviewOutcome("abandoned", endedReason = "reported").contains("Nothing counts against you"))
        assertTrue(reviewOutcome("abandoned", endedReason = "request_deleted").contains("request was removed"))
        assertTrue(reviewOutcome("abandoned", endedReason = "team").contains("TestTrack team"))
    }

    // ------------------------------------------------------------------------- left the run

    @Test
    fun an_app_taken_out_stays_on_the_page_under_left_the_run_and_is_never_owed() {
        val out = seat("gone", state = "removed").copy(outAt = "2026-10-28T09:00:00Z")
        val crew =
            crew(state = "full", day = 7, members = listOf(seat("me", mine = true), seat("s2"), out)).toCrew()
        assertEquals(listOf("s2"), crew.apps.map { it.id })
        assertEquals(listOf("gone"), crew.left.map { it.id })
        assertEquals("2026-10-28T09:00:00Z", crew.left.single().outAt)
    }

    @Test
    fun uninstall_is_offered_only_a_whole_day_after_it_left() {
        val left = java.time.Instant.parse("2026-10-28T09:00:00Z")
        assertFalse(outLongEnough("2026-10-28T09:00:00Z", left.plusSeconds(23 * 3600)))
        assertTrue(outLongEnough("2026-10-28T09:00:00.123456Z", left.plusSeconds(25 * 3600)))
        assertFalse(outLongEnough("", left))
    }

    @Test
    fun a_waiting_appeal_holds_new_seats_and_names_the_app_and_crew() {
        val mine = seat("me", mine = true, state = "removed").copy(appeal = ApiAppeal("waiting", "Phone in repair."))
        val crew = crew(state = "full", day = 7, members = listOf(mine, seat("s2"))).toCrew()
        assertEquals("App me" to "Crew 07", appealWaitingIn(listOf(crew)))
        assertEquals("Phone in repair.", crew.mine?.appeal?.text)

        val refused = mine.copy(appeal = ApiAppeal("refused", "No.", "Visits stopped earlier too."))
        assertNull(appealWaitingIn(listOf(crew(state = "full", day = 7, members = listOf(refused)).toCrew())))
    }
}
