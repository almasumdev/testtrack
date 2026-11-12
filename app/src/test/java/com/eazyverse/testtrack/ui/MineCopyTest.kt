package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

/** The words on a My reviews ticket: short enough to sit beside the app's mark without wrapping. */
class MineCopyTest {
    private val now = Instant.parse("2026-11-12T10:00:00Z")
    private val zone = ZoneOffset.UTC
    private val english = Locale.ENGLISH
    private val row = MyReview(id = "r", postId = "p", appName = "Cherish", state = "claimed", reward = 20, reason = "")

    @Test
    fun every_next_step_fits_in_24_characters() {
        val live = row.copy(isLive = true)
        val lines =
            listOf(
                mineNext(row, now),
                mineNext(row.copy(state = "installed"), now),
                mineNext(row.copy(state = "used"), now),
                mineNext(row.copy(state = "used", isLive = true), now),
                mineNext(row.copy(state = "checking"), now),
            ) +
                listOf(
                    live.copy(state = "installed"),
                    live.copy(state = "installed", daysUsed = 1, usedEnough = true),
                    live.copy(state = "installed", daysUsed = 2),
                    live.copy(state = "installed", daysUsed = 2, usedEnough = true),
                    live.copy(state = "installed", daysUsed = 2, usedEnough = true, opensAt = "2026-11-12T20:45:00Z"),
                    live.copy(state = "installed", daysUsed = 2, usedEnough = true, opensAt = "2026-11-13T12:30:00Z"),
                    live.copy(state = "installed", daysUsed = 2, usedEnough = true, opensAt = "2026-11-14T12:30:00Z"),
                ).map { mineNext(it, now) }
        lines.forEach { assertTrue("'$it' is ${it.length} characters", it.length <= 24) }
    }

    @Test
    fun the_closed_steps_say_what_to_do() {
        assertEquals("Install from Play", mineNext(row, now))
        assertEquals("Use it for 2 minutes", mineNext(row.copy(state = "installed"), now))
        assertEquals("Send feedback on Play", mineNext(row.copy(state = "used"), now))
        assertEquals("Review it on Play", mineNext(row.copy(state = "used", isLive = true), now))
        assertEquals("Retake your screenshot", mineNext(row.copy(state = "checking"), now))
    }

    @Test
    fun the_time_left_is_short() {
        val hour = 3_600_000L
        assertEquals("2d left", shortLeft(60 * hour, 0))
        assertEquals("23h left", shortLeft(23 * hour + 5, 0))
        assertEquals("40m left", shortLeft(40 * 60_000L, 0))
        assertEquals("Due now", shortLeft(0, 5))
    }

    @Test
    fun only_checking_has_a_deadline() {
        val by = "2026-11-13T08:00:00Z"
        assertEquals(Instant.parse(by), mineDeadline(row.copy(state = "checking", checkBy = by)))
        assertEquals(null, mineDeadline(row.copy(state = "used", checkBy = by)))
    }

    @Test
    fun a_decided_review_says_how_it_ended_and_when() {
        val done = row.copy(decidedAt = "2026-11-09T07:00:00Z")
        assertEquals("Paid on 9 Nov", mineHistory(done.copy(state = "approved"), zone, english))
        assertEquals("Ran out on 9 Nov", mineHistory(done.copy(state = "expired"), zone, english))
        assertEquals("Given up on 9 Nov", mineHistory(done.copy(state = "abandoned"), zone, english))
        assertEquals("Refused. Wrong app.", mineHistory(done.copy(state = "rejected", reason = "Wrong app."), zone, english))
        assertEquals("Refused", mineHistory(done.copy(state = "rejected"), zone, english))
    }

    @Test
    fun a_sent_review_says_how_it_was_sent() {
        val sent = row.copy(state = "submitted", submittedAt = "2026-11-09T07:00:00Z")
        assertEquals("Sent on 9 Nov", mineWaitNote(sent, zone, english))
        assertEquals("Sent here, so it pays half", mineWaitNote(sent.copy(noBox = true), zone, english))
        assertEquals("You asked for a second look", mineWaitNote(sent.copy(state = "disputed"), zone, english))
        assertEquals("With the developer", mineWaitingWith(sent))
        assertEquals("Members are deciding", mineWaitingWith(sent.copy(state = "disputed")))
    }
}
