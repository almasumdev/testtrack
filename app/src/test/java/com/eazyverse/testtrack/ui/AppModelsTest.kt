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
}
