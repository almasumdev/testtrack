package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** What an app on My apps says about itself on its page. The tile's own line is in AppTileStatusTest. */
class AppModelsTest {
    private fun app(
        crew: AppCrewStatus? = null,
        feedback: AppFeedbackStatus? = null,
        verifiedBy: String? = "play",
        live: Boolean = false,
    ) = AppEntry(id = "a", pkg = "com.example.app", name = "Lapse", crew = crew, feedback = feedback, verifiedBy = verifiedBy, live = live)

    @Test
    fun the_page_says_what_each_part_is_doing() {
        assertEquals("Not in a crew yet", crewStatus(app()))
        assertEquals("Live apps take feedback, not crews", crewStatus(app(live = true)))
        assertEquals("Crew 01 sent it back", crewStatus(app(crew = AppCrewStatus("rejected", "c", "Crew 01"))))
        assertEquals("Day 6 of 14 in Crew 01", crewStatus(app(crew = AppCrewStatus("testing", "c", "Crew 01", day = 6, runDays = 14))))
        assertEquals("No feedback asked for yet", feedbackStatus(app()))
        assertEquals("All 3 reviews in", feedbackStatus(app(feedback = AppFeedbackStatus("done", "p", 3, 3))))
        assertEquals("Verified by its crew", verifiedChip(app(verifiedBy = "crew")))
        assertEquals("Not verified", verifiedChip(app(verifiedBy = null)))
    }

    private fun feedback(
        done: Int = 0,
        bought: Int = 5,
        toCheck: Int = 0,
        state: String = "collecting",
        unlisted: Boolean = false,
    ) = AppFeedbackStatus(state, "p", done, bought, toCheck, unlisted)

    @Test
    fun the_card_draws_one_face_per_review_bought_in_a_fixed_order() {
        val faces = feedback(done = 1, toCheck = 2, bought = 5).faces()
        assertEquals(
            listOf(ReviewFace.ACCEPTED, ReviewFace.TO_CHECK, ReviewFace.TO_CHECK, ReviewFace.OPEN, ReviewFace.OPEN),
            faces.faces,
        )
        assertEquals(0, faces.more)
        assertEquals("1 accepted, 2 to check, 2 open", feedback(done = 1, toCheck = 2, bought = 5).facesSpoken())
        assertEquals("5 accepted", feedback(done = 5, bought = 5, state = "done").facesSpoken())
    }

    @Test
    fun counts_that_do_not_add_up_never_draw_more_faces_than_were_bought() {
        assertEquals(3, feedback(done = 9, toCheck = 4, bought = 3).faces().faces.size)
        val mixed = feedback(done = 1, toCheck = 1, bought = 3).faces().faces
        assertEquals(listOf(ReviewFace.ACCEPTED, ReviewFace.TO_CHECK, ReviewFace.OPEN), mixed)
        assertEquals(emptyList<ReviewFace>(), feedback(bought = 0).faces().faces)
    }

    @Test
    fun a_long_request_keeps_the_reviews_waiting_for_the_owner_and_counts_the_rest() {
        val shown = feedback(done = 30, toCheck = 4, bought = 150).faces()
        assertEquals(10, shown.faces.size)
        assertEquals(4, shown.faces.count { it == ReviewFace.TO_CHECK })
        assertEquals(6, shown.faces.count { it == ReviewFace.ACCEPTED })
        assertEquals(140, shown.more)
        assertEquals(10, feedback(toCheck = 40, bought = 50).faces().faces.count { it == ReviewFace.TO_CHECK })
    }

    @Test
    fun the_card_says_what_needs_the_owner_first() {
        assertEquals(FeedbackCardKind.TO_CHECK, feedback(toCheck = 1).cardKind())
        assertEquals(FeedbackCardKind.TO_CHECK, feedback(toCheck = 1, unlisted = true).cardKind())
        assertEquals(FeedbackCardKind.OFF_CIRCLE, feedback(unlisted = true).cardKind())
        assertEquals(FeedbackCardKind.WAITING, feedback(done = 2).cardKind())
        assertEquals(FeedbackCardKind.ALL_IN, feedback(done = 5, state = "done").cardKind())
        assertEquals(FeedbackCardKind.CLOSED, feedback(done = 1, state = "closed", unlisted = true).cardKind())
    }

    @Test
    fun the_start_lines_name_no_number_of_days() {
        assertEquals("Testing days Google asks for", CREW_START_LINE)
        assertEquals("Real people try it, then review", FEEDBACK_START_LINE)
        assertEquals("Before production", CREW_WHEN)
        assertEquals("Any time", FEEDBACK_WHEN)
    }
}
