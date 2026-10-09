package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiMyApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words the three new tabs put on screen.
 *
 * Small arithmetic, but the kind that is read by everybody and noticed the moment it is wrong:
 * an ordinal that says "11st", or a line under an app that claims fourteen testers on a crew
 * that has not filled.
 */
class StandingTest {
    private fun app(
        state: String,
        kind: String = "crew",
        where: String = "Crew 07",
        day: Int = 0,
        runDays: Int = 14,
        testers: Int = 0,
        greens: Int = 0,
        bought: Int = 3,
    ) = ApiMyApp(
        id = "a1",
        kind = kind,
        appName = "Nikosh Budget",
        packageName = "com.example.nikosh",
        state = state,
        where = where,
        day = day,
        runDays = runDays,
        testers = testers,
        greens = greens,
        reviewsBought = bought,
        createdAt = "2026-09-20T00:00:00+00:00",
    )

    // ------------------------------------------------------------------ ordinals

    @Test
    fun the_ordinary_ordinals_read_properly() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("21st", ordinal(21))
        assertEquals("42nd", ordinal(42))
    }

    @Test
    fun eleven_to_thirteen_are_the_ones_every_naive_version_gets_wrong() {
        // And they are exactly the places a board of fifty is full of.
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("111th", ordinal(111))
        assertEquals("112th", ordinal(112))
    }

    @Test
    fun nobody_is_ranked_before_they_have_done_anything() {
        assertEquals("unranked", ordinal(0))
        assertEquals("unranked", ordinal(-1))
    }

    // ------------------------------------------------------------------ what an app says

    @Test
    fun a_running_app_says_how_far_through_and_how_many_are_on_it() {
        val line = appStanding(app("testing", day = 6, testers = 14))
        assertTrue(line, line.contains("day 6 of 14"))
        assertTrue(line, line.contains("14 testing"))
        assertEquals("Testing", appLabel("testing"))
    }

    @Test
    fun a_rejected_app_says_what_to_do_rather_than_only_that_it_failed() {
        // The owner can fix this and send it back to the same crew, so the row says so.
        val line = appStanding(app("rejected"))
        assertTrue(line, line.contains("Fix it"))
        assertEquals("Fix it", appLabel("rejected"))
    }

    @Test
    fun a_post_counts_reviews_rather_than_days() {
        val line = appStanding(app("collecting", kind = "post", where = "Circle", testers = 2))
        assertEquals("Circle · 2 of 3 reviews in", line)
        assertEquals("Open", appLabel("collecting"))
    }

    @Test
    fun a_waiting_crew_seat_says_it_is_waiting_on_the_crew_and_not_on_you() {
        val line = appStanding(app("waiting"))
        assertTrue(line, line.contains("waiting for it to fill"))
    }

    @Test
    fun a_state_this_build_does_not_know_still_says_something_sensible() {
        // A server ahead of the app. The row has to stay readable rather than go blank.
        assertEquals("Crew 07", appStanding(app("some_new_state")))
        assertEquals("Up", appLabel("some_new_state"))
    }

    @Test
    fun a_full_crew_waiting_for_midnight_says_so_rather_than_filling() {
        assertEquals("Starting", appLabel("starting"))
        assertTrue(appStanding(app("starting")).contains("midnight"))
        assertEquals("Crew full", stageOf(app("starting"))?.title)
    }

    @Test
    fun an_app_waiting_on_members_shows_their_answers() {
        val line = appStanding(app("checking", greens = 1))
        assertTrue(line, line.contains("1 Yes"))
        assertEquals("Members are trying it", stageOf(app("checking", greens = 1))?.title)
    }

    @Test
    fun a_request_closed_early_is_over_and_says_how_far_it_got() {
        assertEquals("Closed", appLabel("closed"))
        val line = appStanding(app("closed", kind = "post", where = "Circle", testers = 1))
        assertTrue(line, line.contains("1 of 3"))
    }
}
