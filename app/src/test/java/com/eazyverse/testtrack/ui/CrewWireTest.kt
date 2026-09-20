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
        greens: Int = 2,
        reds: Int = 0,
        verdict: Boolean? = null,
        opened: Boolean = false,
        missed: Int = 0,
        days: Int = 0,
    ) = ApiSeat(
        id = id,
        crewId = "c1",
        appName = "App $id",
        packageName = "com.example.$id",
        ownerName = owner,
        state = state,
        greens = greens,
        reds = reds,
        mine = mine,
        myVerdict = verdict,
        openedToday = opened,
        missed = missed,
        daysDone = days,
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
        taken = members.size,
        seats = 15,
        runDays = 14,
        day = day,
        startsInMs = startsIn,
        mySeatId = members.firstOrNull { it.mine }?.id,
        createdAt = "2026-09-20T00:00:00+00:00",
        members = members,
    )

    // ------------------------------------------------------------------ which screen

    @Test
    fun a_crew_still_taking_seats_is_open() {
        assertEquals(CrewPhase.OPEN, crewPhase(crew(state = "filling")))
    }

    @Test
    fun a_full_crew_before_your_own_midnight_is_a_lobby() {
        // The gap between filling and starting is the setup window, not the run.
        assertEquals(CrewPhase.LOBBY, crewPhase(crew(state = "full", day = 0)))
    }

    @Test
    fun the_same_crew_once_your_day_has_begun_is_a_run() {
        assertEquals(CrewPhase.RUN, crewPhase(crew(state = "full", day = 1)))
    }

    @Test
    fun a_finished_crew_is_a_record() {
        assertEquals(CrewPhase.RECORD, crewPhase(crew(state = "done", day = 14)))
    }

    @Test
    fun a_state_this_build_does_not_know_does_not_become_a_run() {
        // A server ahead of the app. Anything but "full" with a day on it must not put somebody
        // on the screen that asks them to start opening apps.
        assertEquals(CrewPhase.LOBBY, crewPhase(crew(state = "something_new", day = 0)))
    }

    // ------------------------------------------------------------------ tonight's list

    @Test
    fun tonight_is_everybody_elses_confirmed_apps() {
        val mine = seat(id = "mine", mine = true)
        val theirs = seat(id = "theirs")
        val unchecked = seat(id = "unchecked", state = "checking", greens = 1)

        val apps = crew(state = "full", day = 3, members = listOf(mine, theirs, unchecked)).toCrew().apps

        // Not your own, and not one two people could not install.
        assertEquals(listOf("theirs"), apps.map { it.id })
    }

    @Test
    fun a_crew_that_has_not_started_owes_nothing_tonight() {
        val apps = crew(state = "full", day = 0, members = listOf(seat(), seat(id = "s2"))).toCrew().apps
        assertTrue("A lobby is not an evening's work", apps.isEmpty())
    }

    @Test
    fun an_app_already_opened_tonight_is_marked_done() {
        val done = seat(id = "done", opened = true)
        val app = crew(state = "full", day = 2, members = listOf(done)).toCrew().apps.single()
        assertTrue(app.done)
        assertFalse(app.reported)
    }

    @Test
    fun an_app_you_reported_is_excused_rather_than_owed() {
        val broken = seat(id = "broken", verdict = false)
        val app = crew(state = "full", day = 2, members = listOf(broken)).toCrew().apps.single()
        assertTrue(app.reported)
    }

    // ------------------------------------------------------------------ seats and members

    @Test
    fun a_seat_you_have_answered_on_is_off_your_list() {
        assertTrue(seat(verdict = true).toSeat().judged)
        assertTrue("A red is an answer too", seat(verdict = false).toSeat().judged)
        assertFalse(seat(verdict = null).toSeat().judged)
    }

    @Test
    fun a_seats_state_follows_the_counts_the_server_sent() {
        assertEquals(SeatState.CONFIRMED, seat(greens = 2, reds = 0).toSeat().state)
        assertEquals(SeatState.REJECTED, seat(greens = 0, reds = 2).toSeat().state)
        assertEquals(SeatState.CHECKING, seat(greens = 1, reds = 1).toSeat().state)
    }

    @Test
    fun a_rejected_seat_shows_its_member_as_out() {
        assertTrue(seat(state = "rejected").toMember().out)
        assertFalse(seat(state = "confirmed").toMember().out)
    }

    @Test
    fun your_own_row_carries_your_days_and_your_misses() {
        val mine = seat(id = "mine", mine = true, days = 6, missed = 2, opened = true).toMember()
        assertTrue(mine.mine)
        assertEquals(6, mine.days)
        assertEquals(2, mine.missed)
        assertTrue("Opened on your own row means the night is finished", mine.today)
    }

    // ------------------------------------------------------------------ the whole crew

    @Test
    fun a_crew_carries_the_countdown_and_the_day_the_server_worked_out() {
        val c = crew(state = "full", day = 0, startsIn = 9 * 3_600_000L).toCrew()
        assertEquals(CrewPhase.LOBBY, c.phase)
        assertEquals(9 * 3_600_000L, c.startsIn)
        assertEquals(0, c.day)
    }

    @Test
    fun a_finished_crew_never_reports_more_days_than_the_fortnight_has() {
        // Long after the run, the server's day count keeps climbing. What it came to is capped.
        val c = crew(state = "done", day = 90).toCrew()
        assertEquals(CrewRules.RUN_DAYS, c.finishedDays)
    }

    @Test
    fun the_standing_line_says_what_the_crew_is_doing() {
        val open = crew(state = "filling", members = listOf(seat(), seat(id = "s2"))).toCrew()
        assertEquals("${CrewRules.SEATS - 2} seats left", open.standing)

        val running = crew(state = "full", day = 4).toCrew()
        assertEquals("Day 4 of ${CrewRules.RUN_DAYS}", running.standing)
    }

    @Test
    fun checkable_is_every_seat_across_every_filling_crew_you_have_not_answered_on() {
        val filling =
            crew(
                state = "filling",
                members =
                    listOf(
                        seat(id = "mine", mine = true, state = "checking"),
                        seat(id = "todo", state = "checking", greens = 0, verdict = null),
                        seat(id = "answered", state = "checking", greens = 1, verdict = true),
                    ),
            )
        val running = crew(state = "full", day = 2, members = listOf(seat(id = "elsewhere")))

        assertEquals(listOf("todo"), checkable(listOf(filling, running)).map { it.id })
    }
}

/**
 * What the tab is made of, across several crews at once.
 *
 * The case that matters is a person in more than one: two runs is twenty-eight apps in one
 * evening, and the screens are built around that being ordinary rather than extreme. A test with
 * a single crew in it would let every one of these pass while merged lists stayed broken.
 */
class CrewTabArithmeticTest {
    private fun app(
        id: String,
        done: Boolean = false,
        reported: Boolean = false,
    ) = CrewApp(id = id, label = "App $id", owner = "Rafiul", crew = "Crew 07", done = done, reported = reported)

    private fun seat(
        id: String,
        mine: Boolean = false,
        judged: Boolean = false,
        greens: Int = 0,
        reds: Int = 0,
    ) = CrewSeat(id = id, appName = "App $id", owner = "Rafiul", greens = greens, reds = reds, mine = mine, judged = judged)

    private fun crew(
        id: String,
        phase: CrewPhase,
        apps: List<CrewApp> = emptyList(),
        seats: List<CrewSeat> = emptyList(),
        name: String = "Crew $id",
    ) = Crew(id = id, name = name, phase = phase, apps = apps, seats = seats)

    @Test
    fun tonight_merges_every_running_crew_into_one_evening() {
        val a = crew("a", CrewPhase.RUN, apps = listOf(app("a1"), app("a2", done = true)))
        val b = crew("b", CrewPhase.RUN, apps = listOf(app("b1")))
        val lobby = crew("c", CrewPhase.LOBBY, apps = listOf(app("c1")))

        assertEquals(listOf("a1", "b1"), tonightIn(listOf(a, b, lobby)).map { it.id })
    }

    @Test
    fun an_app_already_opened_or_reported_is_not_still_owed() {
        val c = crew("a", CrewPhase.RUN, apps = listOf(app("x", done = true), app("y", reported = true), app("z")))
        assertEquals(listOf("z"), tonightIn(listOf(c)).map { it.id })
        assertEquals(2, doneTonightIn(listOf(c)))
    }

    @Test
    fun waiting_is_everybody_elses_unanswered_seats_before_a_run_starts() {
        val lobby =
            crew(
                "a",
                CrewPhase.LOBBY,
                seats = listOf(seat("mine", mine = true), seat("todo"), seat("answered", judged = true)),
            )
        // A decided seat is not waiting on anybody, whoever answered.
        val settled = crew("b", CrewPhase.OPEN, seats = listOf(seat("in", greens = CrewRules.VERDICTS)))
        val running = crew("c", CrewPhase.RUN, seats = listOf(seat("elsewhere")))

        assertEquals(listOf("todo"), waitingIn(listOf(lobby, settled, running)).map { it.id })
    }

    @Test
    fun holding_names_the_crew_with_your_seat_that_has_not_started() {
        val running = crew("a", CrewPhase.RUN, seats = listOf(seat("mine", mine = true)), name = "Crew 01")
        val lobby = crew("b", CrewPhase.LOBBY, seats = listOf(seat("also-mine", mine = true)), name = "Crew 02")

        // A seat in a run is not holding anybody back; the one in the lobby is.
        assertEquals("Crew 02", holdingIn(listOf(running, lobby)))
        assertNull(holdingIn(listOf(running)))
    }

    @Test
    fun a_red_needs_a_green_of_your_own_in_that_same_crew_first() {
        val nothingYet = crew("a", CrewPhase.LOBBY, seats = listOf(seat("theirs")))
        assertFalse(mayJudgeIn(listOf(nothingYet), "a"))

        val oneWorking = crew("a", CrewPhase.LOBBY, seats = listOf(seat("theirs", judged = true)))
        assertTrue(mayJudgeIn(listOf(oneWorking), "a"))

        // Your own seat being settled proves nothing about your setup.
        val onlyMine = crew("a", CrewPhase.LOBBY, seats = listOf(seat("mine", mine = true, judged = true)))
        assertFalse(mayJudgeIn(listOf(onlyMine), "a"))
    }

    @Test
    fun a_crew_you_are_not_in_is_not_a_crew_you_may_judge() {
        assertFalse(mayJudgeIn(emptyList(), "nowhere"))
    }
}
