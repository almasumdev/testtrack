package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The My reviews row of a live review on its Use step: it says where the reviewer really is with
 * their two days, and never asks again for a day already counted.
 */
class LiveUseLineTest {
    private val now = Instant.parse("2026-10-29T22:34:00Z")
    private val row = MyReview(id = "r", postId = "p", appName = "Cherish", state = "installed", reward = 20, reason = "", isLive = true)

    @Test
    fun nothing_used_yet_asks_for_two_days() {
        assertEquals("Use it on two days", liveUseLine(row, now))
    }

    @Test
    fun one_day_used_asks_for_another() {
        assertEquals("Open it again on another day", liveUseLine(row.copy(daysUsed = 1, usedEnough = true), now))
    }

    @Test
    fun two_days_used_says_when_it_opens() {
        val waiting = row.copy(daysUsed = 2, usedEnough = true, opensAt = "2026-10-30T02:19:32Z")
        assertTrue(liveUseLine(waiting, now).startsWith("Opens "))
    }

    @Test
    fun two_days_used_and_its_time_passed_is_ready() {
        val ready = row.copy(daysUsed = 2, usedEnough = true, opensAt = "2026-10-29T20:00:00Z")
        assertEquals("Ready for your review", liveUseLine(ready, now))
    }
}
