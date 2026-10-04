package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chip on a review waiting for its owner: how long before it accepts itself. */
class TimeLeftTest {
    private val hour = 3_600_000L
    private val now = 1_000_000_000L

    @Test
    fun days_and_hours() = assertEquals("1d 20h", timeLeft(now + 44 * hour + 59 * 60_000, now))

    @Test
    fun a_whole_number_of_days_drops_the_zero_hours() = assertEquals("2d", timeLeft(now + 48 * hour, now))

    @Test
    fun under_a_day_is_hours() = assertEquals("6h", timeLeft(now + 6 * hour + 30 * 60_000, now))

    @Test
    fun under_an_hour_is_minutes_and_never_zero() {
        assertEquals("25m", timeLeft(now + 25 * 60_000, now))
        assertEquals("1m", timeLeft(now + 10_000, now))
    }

    @Test
    fun past_the_deadline_says_due_rather_than_counting_negative() = assertEquals("Due now", timeLeft(now - hour, now))

    @Test
    fun the_last_twelve_hours_are_close() {
        assertTrue(closeTo(now + 11 * hour, now))
        assertFalse(closeTo(now + 13 * hour, now))
    }
}
