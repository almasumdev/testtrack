package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an app on My apps says about itself: its badges, and the sentences on its page. */
class AppModelsTest {
    private fun app(
        crew: AppCrewStatus? = null,
        feedback: AppFeedbackStatus? = null,
        verifiedBy: String? = "play",
        live: Boolean = false,
    ) = AppEntry(id = "a", pkg = "com.example.app", name = "Lapse", crew = crew, feedback = feedback, verifiedBy = verifiedBy, live = live)

    @Test
    fun an_app_nothing_is_running_on_has_no_badges() {
        assertEquals(emptyList<AppBadge>(), app().badges())
    }

    @Test
    fun a_crew_and_a_request_both_show_crew_first() {
        val badges =
            app(
                crew = AppCrewStatus("testing", "c", "Crew 01", day = 6, runDays = 14),
                feedback = AppFeedbackStatus("collecting", "p", done = 2, bought = 3),
            ).badges()
        assertEquals(listOf("Day 6 of 14 · Crew 01", "Feedback 2 of 3"), badges.map { it.text })
        assertEquals(listOf(BadgeKind.CREW, BadgeKind.FEEDBACK), badges.map { it.kind })
    }

    @Test
    fun done_things_read_as_done_and_problems_as_problems() {
        assertEquals(BadgeTone.DONE, crewBadge(AppCrewStatus("finished", "c", "Crew 01")).tone)
        assertEquals(BadgeTone.DONE, feedbackBadge(AppFeedbackStatus("done", "p", 3, 3)).tone)
        assertEquals(BadgeTone.PROBLEM, crewBadge(AppCrewStatus("rejected", "c", "Crew 01")).tone)
        assertEquals(BadgeTone.ATTENTION, crewBadge(AppCrewStatus("checking", "c", "Crew 01")).tone)
    }

    @Test
    fun an_unverified_app_says_so_last() {
        val badges = app(feedback = AppFeedbackStatus("collecting", "p", 0, 3), verifiedBy = null).badges()
        assertEquals("Not verified", badges.last().text)
        assertEquals(BadgeKind.VERIFY, badges.last().kind)
    }

    @Test
    fun the_page_says_what_each_part_is_doing() {
        assertTrue(crewLine(app()).contains("Not in a crew yet"))
        assertTrue(crewLine(app(live = true)).contains("Live apps take feedback"))
        assertTrue(crewLine(app(crew = AppCrewStatus("rejected", "c", "Crew 01"))).contains("sent it back"))
        assertEquals("No feedback asked for yet.", feedbackLine(app()))
        assertTrue(feedbackLine(app(feedback = AppFeedbackStatus("done", "p", 3, 3))).contains("You can ask again"))
        assertTrue(verifiedLine(app(verifiedBy = "crew")).contains("crew members"))
        assertTrue(verifiedLine(app(verifiedBy = null)).startsWith("Not verified yet"))
    }
}
