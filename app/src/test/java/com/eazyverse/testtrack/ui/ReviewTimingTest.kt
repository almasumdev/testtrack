package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.UsageSessions
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Timing 2 of a live review, as the screen reads it from the server's times: three minutes, three
 * more no sooner than 24 hours after, and the review opening 48 hours after the first three. A
 * review from an older server says nothing of timing and keeps the calendar-day rules.
 */
class ReviewTimingTest {
    private val post =
        ApiPost(
            id = "p1",
            appName = "Nikosh Budget",
            packageName = "com.example.nikosh",
            ask = "Is the total readable?",
            isLive = true,
            reward = 40,
            reviewsBought = 3,
            reviewsDone = 0,
            ownerName = "Rafiul",
            mine = false,
            createdAt = "2026-11-20T00:00:00Z",
        )

    private val done = Instant.parse("2026-11-20T10:00:00Z")

    private fun paced(
        step2: Long = 0,
        state: String = "installed",
    ) = ApiReview(
        id = "r1",
        postId = "p1",
        state = state,
        usedMs = 180_000,
        neededMs = 180_000,
        proofRef = "",
        written = "",
        reason = "",
        createdAt = "2026-11-20T09:00:00Z",
        isLive = true,
        timing = 2,
        step1DoneAt = done.toString(),
        useAgainFrom = done.plusSeconds(24 * 3600).toString(),
        reviewFrom = done.plusSeconds(48 * 3600).toString(),
        step2Ms = step2,
        step2NeededMs = 180_000,
        // Timing 1's own figure, which timing 2 must not read.
        returnsAt = done.plusSeconds(20 * 3600).toString(),
    )

    @Test
    fun the_first_visit_done_waits_for_the_second_by_the_clock_not_the_calendar() {
        val job = ReviewJob(post, paced())
        assertTrue(job.paced)
        assertTrue(job.waitingForDay)
        assertFalse(job.usedOnTwoDays)
        assertEquals(done.plusSeconds(24 * 3600), job.opensAt())
        // The next calendar day is not enough: 23 hours on, still waiting.
        assertFalse(job.secondDayNow(done.plusSeconds(23 * 3600)))
        assertTrue(job.secondDayNow(done.plusSeconds(24 * 3600)))
    }

    @Test
    fun the_second_visit_done_waits_for_forty_eight_hours_from_the_first() {
        val job = ReviewJob(post, paced(step2 = 180_000))
        assertTrue(job.secondVisitDone)
        assertTrue(job.usedOnTwoDays)
        assertEquals(done.plusSeconds(48 * 3600), job.opensAt())
        assertFalse(job.secondDayNow(done.plusSeconds(30 * 3600)))
    }

    @Test
    fun part_of_the_second_visit_counts_down_what_is_left() {
        val job = ReviewJob(post, paced(step2 = 100_000))
        assertFalse(job.secondVisitDone)
        assertEquals(80, job.secondVisitSecondsLeft)
    }

    @Test
    fun an_older_server_keeps_the_calendar_day_rules() {
        val old =
            paced().copy(timing = 1, step1DoneAt = null, useAgainFrom = null, reviewFrom = null, step2NeededMs = 0, daysUsed = 1)
        val job = ReviewJob(post, old)
        assertFalse(job.paced)
        assertTrue(job.waitingForDay)
        assertFalse(job.usedOnTwoDays)
        assertEquals(Instant.parse(old.returnsAt), job.opensAt())
    }

    @Test
    fun a_live_preview_shows_timing_2_and_a_closed_one_does_not() {
        assertTrue(ReviewJob(post, previewOf(post)).paced)
        val closed = post.copy(isLive = false)
        assertFalse(ReviewJob(closed, previewOf(closed)).paced)
    }

    @Test
    fun running_out_after_the_first_visit_says_four_days() {
        assertTrue(reviewOutcome("expired", live = true, afterFirstVisit = true).contains("4 days of your first visit"))
        assertTrue(reviewOutcome("expired", live = true).contains("three days"))
    }

    @Test
    fun the_my_reviews_row_follows_both_visits() {
        val now = done.plusSeconds(3600)
        val row =
            MyReview(
                id = "r",
                postId = "p",
                appName = "Cherish",
                state = "installed",
                reward = 20,
                reason = "",
                isLive = true,
                timing = 2,
                useAgainFrom = done.plusSeconds(24 * 3600).toString(),
                reviewFrom = done.plusSeconds(48 * 3600).toString(),
            )
        assertEquals("Use it for 3 minutes", liveUseLine(row, now))
        val first = row.copy(firstVisitDone = true)
        assertTrue(liveUseLine(first, now).startsWith("Back "))
        assertTrue(liveUseLine(first, now).length <= 24)
        assertEquals("Use it 3 more minutes", liveUseLine(first, done.plusSeconds(25 * 3600)))
        val both = first.copy(secondVisitDone = true)
        assertTrue(liveUseLine(both, done.plusSeconds(30 * 3600)).startsWith("Opens "))
        assertEquals("Ready for your review", liveUseLine(both, done.plusSeconds(49 * 3600)))
    }

    @Test
    fun the_first_visit_is_reached_inside_the_sitting_that_completes_it() {
        val app = "com.example.app"
        val events =
            listOf(
                UsageSessions.Event(UsageSessions.Kind.RESUMED, app, "Main", 10_000),
                UsageSessions.Event(UsageSessions.Kind.PAUSED, app, "Main", 70_000),
                UsageSessions.Event(UsageSessions.Kind.RESUMED, app, "Main", 100_000),
                UsageSessions.Event(UsageSessions.Kind.PAUSED, app, "Main", 400_000),
            )
        // 60 s in the first sitting, so 180 s is reached 120 s into the second.
        assertEquals(220_000L, UsageSessions.reachedAt(events, app, 0, 500_000, 180_000))
        assertNull(UsageSessions.reachedAt(events, app, 0, 500_000, 1_000_000))
    }
}
