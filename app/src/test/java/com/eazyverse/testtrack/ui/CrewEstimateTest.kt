package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CrewEstimateTest {
    private val utc = ZoneId.of("UTC")

    // A Friday, ten in the morning. The coming midnight is the start of the 14th.
    private val now = Instant.parse("2026-11-13T10:00:00Z")

    private fun line(
        full: String?,
        basis: String? = "pace",
        at: Instant = now,
        zone: ZoneId = utc,
    ) = startEstimate(full, basis, at, zone)?.text

    @Test
    fun `full before the coming midnight starts tonight`() {
        assertEquals("Likely starts tonight", line("2026-11-13T15:00:00Z"))
        assertEquals("Likely starts tonight", line("2026-11-13T23:59:59Z"))
    }

    @Test
    fun `full the day after starts tomorrow night`() {
        assertEquals("Likely starts tomorrow night", line("2026-11-14T00:00:00Z"))
        assertEquals("Likely starts tomorrow night", line("2026-11-14T01:00:00Z"))
    }

    @Test
    fun `already past its time still starts at the coming midnight`() {
        assertEquals("Likely starts tonight", line("2026-11-10T08:00:00Z"))
    }

    @Test
    fun `two to thirteen days is counted in days and rounded`() {
        // The 17th at midnight is 3 days and 14 hours away, so about 4.
        assertEquals("Likely starts in about 4 days", line("2026-11-16T12:00:00Z"))
        // The 16th at midnight is 2 days and 14 hours: about 3.
        assertEquals("Likely starts in about 3 days", line("2026-11-15T12:00:00Z"))
        // The 26th at midnight is 12 days and 14 hours: 13.
        assertEquals("Likely starts in about 13 days", line("2026-11-25T12:00:00Z"))
    }

    @Test
    fun `the third night out rounds to two when it is close`() {
        // From 23:00 the 14th midnight is an hour away and the 16th is 2 days and an hour: 2.
        assertEquals("Likely starts in about 2 days", line("2026-11-15T12:00:00Z", at = Instant.parse("2026-11-13T23:00:00Z")))
    }

    @Test
    fun `fourteen to thirty four days is counted in weeks`() {
        // The 27th at midnight is 13 days 14 hours: rounds to 14, two weeks.
        assertEquals("Likely starts in about 2 weeks", line("2026-11-26T05:00:00Z"))
        // The 8th of December is 24 days 14 hours, 25 rounded: 3.6 weeks, so 4.
        assertEquals("Likely starts in about 4 weeks", line("2026-12-07T05:00:00Z"))
        // The 17th of December at midnight is 33 days 14 hours, 34 rounded: 4.9 weeks, so 5.
        assertEquals("Likely starts in about 5 weeks", line("2026-12-16T05:00:00Z"))
    }

    @Test
    fun `thirty five days and more is over a month`() {
        assertEquals("Likely starts in over a month", line("2026-12-17T05:00:00Z"))
        assertEquals("Likely starts in over a month", line("2027-03-01T00:00:00Z"))
    }

    @Test
    fun `a new crew with no history has no estimate yet, quietly`() {
        val estimate = startEstimate("2026-11-20T00:00:00Z", "new", now, utc)
        assertEquals("No start estimate yet", estimate?.text)
        assertFalse(estimate!!.known)
        assertEquals("No start estimate yet", line(null, "new"))
    }

    @Test
    fun `an estimate is known and carries the clock`() {
        assertTrue(startEstimate("2026-11-16T12:00:00Z", "pace", now, utc)!!.known)
        // An older server's missing basis does not stop a time from being used.
        assertNotNull(startEstimate("2026-11-16T12:00:00Z", null, now, utc))
    }

    @Test
    fun `nothing from the server says nothing`() {
        assertNull(line(null, null))
        assertNull(line(null, "pace"))
        assertNull(line("soon", "pace"))
        assertNull(line("", null))
    }

    @Test
    fun `a time exactly at local midnight starts at the midnight after`() {
        assertEquals("Likely starts tomorrow night", line("2026-11-14T00:00:00Z"))
    }

    @Test
    fun `the day is the phones day, not the servers`() {
        // 19:00 UTC on the 13th is 01:00 on the 14th in Dhaka (+6): the run starts the 15th at midnight there.
        val dhaka = ZoneId.of("Asia/Dhaka")
        assertEquals("Likely starts tomorrow night", line("2026-11-13T19:00:00Z", zone = dhaka))
        // The same instant in UTC is still the 13th, so it starts tonight.
        assertEquals("Likely starts tonight", line("2026-11-13T19:00:00Z", zone = utc))
    }

    @Test
    fun `a zone behind utc moves the day the other way`() {
        // 03:00 UTC on the 14th is 22:00 on the 13th in New York (-5): the coming midnight.
        val newYork = ZoneId.of("America/New_York")
        assertEquals("Likely starts tonight", line("2026-11-14T03:00:00Z", zone = newYork))
        // 06:00 UTC is 01:00 on the 14th there: a night later.
        assertEquals("Likely starts tomorrow night", line("2026-11-14T06:00:00Z", zone = newYork))
    }

    @Test
    fun `near midnight on the phone the coming midnight is minutes away`() {
        val at = Instant.parse("2026-11-13T23:30:00Z")
        assertEquals("Likely starts tonight", line("2026-11-13T23:45:00Z", at = at))
        // Just after midnight the coming one is a whole day off.
        val after = Instant.parse("2026-11-14T00:10:00Z")
        assertEquals("Likely starts tonight", line("2026-11-14T23:00:00Z", at = after))
        assertEquals("Likely starts tomorrow night", line("2026-11-15T01:00:00Z", at = after))
    }

    @Test
    fun `a clock change inside the wait does not shift the count`() {
        val newYork = ZoneId.of("America/New_York")
        // Saturday noon, the night before the clocks go forward on the 8th.
        val saturday = Instant.parse("2026-03-07T17:00:00Z")
        assertEquals("Likely starts tomorrow night", startEstimate("2026-03-08T16:00:00Z", "pace", saturday, newYork)?.text)
        // 131 real hours to the 13th at midnight, an hour short of 5.5 days: 5.
        assertEquals("Likely starts in about 5 days", startEstimate("2026-03-12T14:00:00Z", "pace", saturday, newYork)?.text)
    }

    @Test
    fun `a zone whose midnight does not exist still gives a start`() {
        // Sao Paulo skipped midnight on 4 November 2018: the day began at 01:00.
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        val at = Instant.parse("2018-11-03T15:00:00Z")
        assertEquals("Likely starts tonight", startEstimate("2018-11-03T23:00:00Z", "pace", at, saoPaulo)?.text)
    }

    @Test
    fun `seats left reads as a count, or one seat, or none`() {
        assertEquals("9 of 15 seats left", seatsLeftText(9))
        assertEquals("15 of 15 seats left", seatsLeftText(15))
        assertEquals("1 seat left", seatsLeftText(1))
        assertEquals("No seats left", seatsLeftText(0))
    }
}
