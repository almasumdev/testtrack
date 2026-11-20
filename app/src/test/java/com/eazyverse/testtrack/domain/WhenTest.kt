package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Times said one way: read from the server, a day, a time of day, and how long is left. */
class WhenTest {
    private val utc = ZoneOffset.UTC
    private val hour = 3_600_000L
    private val now = 1_000_000_000L

    @Test
    fun reads_a_time_with_or_without_an_offset() {
        assertEquals(Instant.parse("2026-11-09T10:00:00Z"), instantOf("2026-11-09T10:00:00Z"))
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), instantOf("2026-11-09T10:00:00+02:00"))
        assertEquals(Instant.parse("2026-11-09T10:00:00.250Z"), instantOf("2026-11-09T10:00:00.250Z"))
    }

    @Test
    fun blank_or_broken_is_no_time() {
        assertNull(instantOf(null))
        assertNull(instantOf(""))
        assertNull(instantOf("  "))
        assertNull(instantOf("next tuesday"))
    }

    @Test
    fun a_day_is_month_first_with_no_year() {
        assertEquals("Nov 9", shortDate(Instant.parse("2026-11-09T10:00:00Z"), utc))
        assertEquals("Jan 21", shortDate(Instant.parse("2027-01-21T23:30:00Z"), utc))
    }

    @Test
    fun a_day_is_in_the_given_zone() {
        val late = Instant.parse("2026-11-09T23:30:00Z")
        assertEquals("Nov 10", shortDate(late, ZoneId.of("Asia/Dhaka")))
        assertEquals("Nov 9", shortDate(late.toEpochMilli(), utc))
    }

    @Test
    fun the_clock_follows_the_phones_setting() {
        val evening = Instant.parse("2026-11-09T20:05:00Z")
        assertEquals("8:05 PM", clockTime(evening, hour24 = false, zone = utc))
        assertEquals("20:05", clockTime(evening, hour24 = true, zone = utc))
        val morning = Instant.parse("2026-11-09T07:30:00Z")
        assertEquals("7:30 AM", clockTime(morning, hour24 = false, zone = utc))
        assertEquals("07:30", clockTime(morning, hour24 = true, zone = utc))
    }

    @Test
    fun time_left_with_a_suffix_reads_as_the_chip_does() {
        assertEquals("1d 20h left", timeLeft(now + 44 * hour + 59 * 60_000, now, " left"))
        assertEquals("2d left", timeLeft(now + 48 * hour, now, " left"))
        assertEquals("6h left", timeLeft(now + 6 * hour + 30 * 60_000, now, " left"))
        assertEquals("1m left", timeLeft(now + 10_000, now, " left"))
    }

    @Test
    fun time_left_matches_the_unsuffixed_chip() {
        listOf(44 * hour + 59 * 60_000, 48 * hour, 6 * hour + 30 * 60_000, 25 * 60_000L, 10_000L, -hour).forEach { left ->
            assertEquals(timeLeft(now + left, now), timeLeft(now + left, now, ""))
        }
    }

    @Test
    fun days_only_drops_the_hours() {
        assertEquals("2d left", timeLeft(now + 60 * hour, now, " left", daysOnly = true))
        assertEquals("23h left", timeLeft(now + 23 * hour + 5, now, " left", daysOnly = true))
        assertEquals("40m left", timeLeft(now + 40 * 60_000L, now, " left", daysOnly = true))
    }

    @Test
    fun past_the_deadline_is_due_with_no_suffix() {
        assertEquals("Due now", timeLeft(now, now + 5, " left"))
        assertEquals("Due now", timeLeft(now - hour, now, " left", daysOnly = true))
    }

    @Test
    fun one_is_singular_and_every_other_count_is_plural() {
        assertEquals("night", pluralOf(1, "night"))
        assertEquals("nights", pluralOf(0, "night"))
        assertEquals("nights", pluralOf(2, "night"))
    }
}
