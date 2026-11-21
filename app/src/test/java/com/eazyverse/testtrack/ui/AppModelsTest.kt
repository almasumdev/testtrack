package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.shortDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

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
        assertEquals("Verified by its crew", verifiedChip(app(verifiedBy = "crew")))
        assertEquals("Not verified", verifiedChip(app(verifiedBy = null)))
    }

    private fun request(
        bought: Int = 5,
        done: Int = 0,
        states: List<String> = emptyList(),
        closed: Boolean = false,
        unlisted: Boolean = false,
        createdAt: Long? = null,
        refunded: Int? = null,
        id: String = "p",
    ) = Request(
        id = id,
        appName = "Lapse",
        bought = bought,
        done = done,
        closed = closed,
        unlisted = unlisted,
        reviews = states.mapIndexed { i, s -> RequestReview("r$i", s) },
        createdAt = createdAt,
        refunded = refunded,
    )

    private fun title(r: Request) = requestTitle(r.cardState(), r.reviewCounts())

    @Test
    fun the_counts_come_from_the_reviews_and_open_is_never_below_zero() {
        val counts =
            request(
                bought = 5,
                done = 1,
                states = listOf("approved", "submitted", "claimed", "checking", "rejected"),
            ).reviewCounts()
        assertEquals(ReviewCounts(done = 1, toCheck = 1, inProgress = 2, open = 1), counts)
        assertEquals(0, request(bought = 2, done = 1, states = listOf("submitted", "used", "installed")).reviewCounts().open)
        assertEquals(
            listOf(ReviewFace.DONE, ReviewFace.TO_CHECK, ReviewFace.IN_PROGRESS, ReviewFace.IN_PROGRESS, ReviewFace.OPEN),
            counts.faces(),
        )
        assertEquals("1 done, 1 to check, 2 under way, 1 open", counts.spoken())
    }

    @Test
    fun the_title_counts_what_is_in() {
        assertEquals("No reviews in yet", title(request(bought = 3)))
        assertEquals("1 of 1 review in", title(request(bought = 1, states = listOf("submitted"))))
        assertEquals("The review is in", title(request(bought = 1, done = 1)))
        assertEquals("3 of 5 reviews in", title(request(bought = 5, done = 2, states = listOf("submitted"))))
        assertEquals("All 5 reviews in", title(request(bought = 5, done = 5)))
        assertEquals("2 of 4 reviews in", title(request(bought = 4, done = 2, closed = true)))
    }

    @Test
    fun the_state_says_filled_first_then_closed_then_off_circle() {
        assertEquals(RequestState.ON_CIRCLE, request().cardState())
        assertEquals(RequestState.OFF_CIRCLE, request(unlisted = true).cardState())
        assertEquals(RequestState.CLOSED, request(closed = true, unlisted = true).cardState())
        assertEquals(RequestState.DONE, request(done = 5, closed = true).cardState())
        assertEquals(listOf("On Circle", "Off Circle", "Done", "Closed"), RequestState.entries.map { it.label })
    }

    @Test
    fun more_than_ten_reviews_switch_to_the_summary() {
        assertEquals(false, request(bought = 10).reviewCounts().summarised)
        assertEquals(true, request(bought = 11).reviewCounts().summarised)
        assertEquals(true, request(bought = 50).reviewCounts().summarised)
    }

    @Test
    fun the_summary_keeps_the_order_and_leaves_out_kinds_with_none() {
        val all = ReviewCounts(done = 31, toCheck = 4, inProgress = 6, open = 9).summary()
        assertEquals(listOf(ReviewFace.DONE, ReviewFace.TO_CHECK, ReviewFace.IN_PROGRESS, ReviewFace.OPEN), all.map { it.face })
        assertEquals(listOf("done", "to check", "under way", "open"), all.map { it.word })
        val some = ReviewCounts(done = 12, open = 3).summary()
        assertEquals(listOf(FaceGroup(ReviewFace.DONE, 12, "done"), FaceGroup(ReviewFace.OPEN, 3, "open")), some)
    }

    @Test
    fun the_face_grid_fills_each_width_exactly_with_at_least_the_gap() {
        for ((width, perRow) in listOf(344f to 9, 292f to 7, 252f to 6)) {
            val fit = fitFaces(width, minFace = 30f, maxFace = 36f, minGap = 8f)
            assertEquals(perRow, fit.perRow)
            assertTrue("gap ${fit.gap} at $width", fit.gap >= 8f - 1e-3f)
            assertTrue(fit.face in 30f..36f)
            assertEquals(width, fit.perRow * fit.face + (fit.perRow - 1) * fit.gap, 1e-3f)
        }
        val one = fitFaces(20f, 30f, 36f, 8f)
        assertEquals(FaceFit(1, 20f, 0f), one)
    }

    @Test
    fun a_mark_never_reaches_the_next_face() {
        assertEquals(4.5f, markOverhang(36f, 8f), 1e-3f)
        assertEquals(2f, markOverhang(36f, 4f), 1e-3f)
        assertEquals(0f, markOverhang(36f, 1f), 1e-3f)
    }

    @Test
    fun the_open_request_leads_and_the_rest_follow_newest_first() {
        val old = request(id = "old", done = 5, createdAt = 1)
        val open = request(id = "open", createdAt = 2)
        val newer = request(id = "newer", done = 1, closed = true, createdAt = 3)
        val (lead, earlier) = listOf(old, newer, open).splitForPage()
        assertEquals("open", lead?.id)
        assertEquals(listOf("newer", "old"), earlier.map { it.id })
        val (finishedLead, rest) = listOf(old, newer).splitForPage()
        assertEquals("newer", finishedLead?.id)
        assertEquals(listOf("old"), rest.map { it.id })
    }

    @Test
    fun the_foot_says_what_needs_the_owner_first() {
        assertEquals(RequestFoot.OFF_CIRCLE, requestFoot(request(unlisted = true, states = listOf("submitted")), false, false))
        assertEquals(RequestFoot.TO_CHECK, requestFoot(request(states = listOf("submitted", "submitted")), false, false))
        assertEquals(RequestFoot.ASK_AGAIN, requestFoot(request(done = 5), false, newestFinished = true))
        assertEquals(RequestFoot.TIX_BACK, requestFoot(request(closed = true, refunded = 70), true, false))
        assertEquals(RequestFoot.NONE, requestFoot(request(closed = true), true, false))
        assertEquals(RequestFoot.ON_IT, requestFoot(request(), false, false))
        assertEquals(RequestFoot.NONE, requestFoot(request(done = 5), true, false))
    }

    @Test
    fun an_earlier_card_names_the_day_it_ended() {
        assertEquals("Nov 2", shortDate(1_762_041_600_000L, ZoneId.of("UTC")))
    }

    @Test
    fun the_start_lines_name_no_number_of_days() {
        assertEquals("Testing days Google asks for", CREW_START_LINE)
        assertEquals("Real people try it, then review", FEEDBACK_START_LINE)
        assertEquals("Before production", CREW_WHEN)
        assertEquals("Any time", FEEDBACK_WHEN)
    }
}
