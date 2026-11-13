package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The line and the problem mark on a My apps tile: which one wins, and what each says. */
class AppTileStatusTest {
    private fun app(
        crew: AppCrewStatus? = null,
        feedback: AppFeedbackStatus? = null,
        verifiedBy: String? = "play",
        live: Boolean = false,
        crewsDone: Int = 0,
    ) = AppEntry(
        id = "a",
        pkg = "com.example.app",
        name = "Lapse",
        crew = crew,
        feedback = feedback,
        verifiedBy = verifiedBy,
        live = live,
        crewsDone = crewsDone,
    )

    private fun crew(
        state: String,
        day: Int = 0,
        runDays: Int = 14,
    ) = AppCrewStatus(state, "c", "Crew 07", day = day, runDays = runDays)

    private fun feedback(
        state: String,
        done: Int = 0,
        bought: Int = 5,
        toCheck: Int = 0,
        unlisted: Boolean = false,
    ) = AppFeedbackStatus(state, "p", done, bought, toCheck, unlisted)

    private fun line(app: AppEntry) = app.statusLine().let { it.text + it.crew }

    @Test
    fun reviews_to_check_win_over_everything() {
        val busy = app(crew = crew("testing", day = 3), feedback = feedback("collecting", done = 2, toCheck = 3))
        assertEquals("3 reviews to check", line(busy))
        assertEquals(StatusTone.ACCENT, busy.statusLine().tone)
        assertEquals("1 review to check", line(app(feedback = feedback("done", toCheck = 1))))
    }

    @Test
    fun a_hundred_reviews_to_check_read_as_99_plus() {
        assertEquals("99 reviews to check", line(app(feedback = feedback("collecting", toCheck = 99))))
        assertEquals("99+ reviews to check", line(app(feedback = feedback("collecting", toCheck = 100))))
        assertEquals("99", cappedCount(99))
        assertEquals("99+", cappedCount(250))
    }

    @Test
    fun a_crew_run_names_the_day_and_the_servers_length() {
        val status = app(crew = crew("testing", day = 6, runDays = 30)).statusLine()
        assertEquals("Day 6 of 30 in ", status.text)
        assertEquals("Crew 07", status.crew)
        assertEquals("All 14 days done", line(app(crew = crew("testing", day = 15))))
        assertEquals("All 30 days done", line(app(crew = crew("testing", day = 31, runDays = 30))))
    }

    @Test
    fun a_crew_before_its_run_says_where_it_is() {
        assertEquals("Starts tonight in Crew 07", line(app(crew = crew("starting"))))
        assertEquals("Being checked in Crew 07", line(app(crew = crew("checking"))))
        assertEquals("Waiting in Crew 07", line(app(crew = crew("waiting"))))
    }

    @Test
    fun a_crew_beats_feedback_and_feedback_beats_done_things() {
        assertEquals("Day 2 of 14 in Crew 07", line(app(crew = crew("testing", day = 2), feedback = feedback("collecting", done = 1))))
        assertEquals("2 of 5 reviews in", line(app(crew = crew("finished"), feedback = feedback("collecting", done = 2))))
        assertEquals("Collecting feedback", line(app(feedback = feedback("collecting", done = 0))))
    }

    @Test
    fun done_things_are_quiet_with_a_tick() {
        val one = app(crew = crew("finished"), crewsDone = 1).statusLine()
        assertEquals("Crew test done", one.text)
        assertEquals(StatusMark.DONE, one.mark)
        assertEquals("3 crew tests done", line(app(crew = crew("finished"), crewsDone = 3)))
        val reviews = app(feedback = feedback("done", done = 5, bought = 5)).statusLine()
        assertEquals("All 5 reviews in", reviews.text)
        assertEquals(StatusMark.DONE, reviews.mark)
        assertEquals("Feedback closed", line(app(feedback = feedback("closed", done = 1))))
        assertEquals(StatusTone.FAINT, app(feedback = feedback("closed")).statusLine().tone)
    }

    @Test
    fun the_last_lines_are_for_apps_with_nothing_running() {
        val waiting = app(live = true, verifiedBy = null).statusLine()
        assertEquals("Waiting for Play", waiting.text)
        assertEquals(StatusMark.WAITING, waiting.mark)
        assertEquals("Not in a crew", line(app(crew = crew("rejected"))))
        assertEquals("Not in a crew", line(app(crew = crew("removed"))))
        assertEquals("Ready for feedback", line(app(live = true)))
        assertEquals("Ready for a crew", line(app()))
    }

    @Test
    fun problems_are_never_written_in_the_line() {
        val cases =
            listOf(
                app(feedback = feedback("collecting", done = 1, unlisted = true)),
                app(verifiedBy = null),
                app(crew = crew("rejected")),
                app(crew = crew("removed")),
            )
        for (broken in cases) {
            assertTrue(broken.problems().isNotEmpty())
            val said = line(broken).lowercase()
            assertFalse(said, "install" in said || "verified" in said || "sent back" in said || "taken out" in said)
        }
        assertEquals("Add the group to its track", line(app(verifiedBy = null)))
        assertEquals("Collecting feedback", line(app(feedback = feedback("collecting", unlisted = true))))
    }

    @Test
    fun each_problem_is_found_in_the_order_the_tooltip_lists_them() {
        assertEquals(emptyList<AppProblem>(), app().problems())
        assertEquals(listOf(AppProblem.NOT_VERIFIED), app(verifiedBy = null).problems())
        assertEquals(emptyList<AppProblem>(), app(live = true, verifiedBy = null).problems())
        assertEquals(listOf(AppProblem.SENT_BACK), app(crew = crew("rejected")).problems())
        assertEquals(listOf(AppProblem.TAKEN_OUT), app(crew = crew("removed")).problems())
        val all = app(crew = crew("removed"), feedback = feedback("collecting", unlisted = true), verifiedBy = null)
        assertEquals(listOf(AppProblem.UNLISTED, AppProblem.NOT_VERIFIED, AppProblem.TAKEN_OUT), all.problems())
    }

    @Test
    fun the_callout_says_a_title_and_one_plain_sentence_for_each() {
        val sent = app(crew = crew("rejected"))
        assertEquals("Sent back by Crew 07", AppProblem.SENT_BACK.title(sent))
        assertEquals("Fix what the crew found, then put it in again.", AppProblem.SENT_BACK.sentence(sent))
        assertEquals("Taken out of Crew 07", AppProblem.TAKEN_OUT.title(sent))
        assertEquals("It's no longer in this run.", AppProblem.TAKEN_OUT.sentence(sent))
        assertEquals("Not verified yet", AppProblem.NOT_VERIFIED.title(sent))
        assertEquals("Add the testers group to your closed test, then tap Verify.", AppProblem.NOT_VERIFIED.sentence(sent))
        assertEquals("Can't be installed", AppProblem.UNLISTED.title(sent))
        assertEquals("Testers couldn't install it, so it's off Circle for now.", AppProblem.UNLISTED.sentence(sent))
        assertEquals("Sent back by its crew", AppProblem.SENT_BACK.title(app()))
    }
}
