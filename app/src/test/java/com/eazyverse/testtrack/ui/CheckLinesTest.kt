package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.firstName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 20 November 2026: the lines under a reviewer's message on the check and case pages. */
class CheckLinesTest {
    @Test
    fun `a text says how long it is and whose words it is in`() {
        assertEquals("5 characters, changed your ready review", textMeta("hello", 100, "changed"))
        assertEquals("5 characters", textMeta("hello", 100, null))
        // Stars only: the length is nobody's business, only whose words they are.
        assertEquals("In their own words", textMeta("hello", 0, "own"))
        assertEquals("", textMeta("hello", 0, null))
    }

    @Test
    fun `what was asked is one line with its figure`() {
        assertEquals("You asked for written feedback, 100+ characters." to "100+ characters", askedLine("written", 100, owner = true))
        assertEquals(
            "The request asked for detailed feedback, 250+ characters." to "250+ characters",
            askedLine("detailed", 250, owner = false),
        )
        // Sent without a review box: text was needed though the request asked for stars.
        assertEquals("It needed 100+ characters of text." to "100+ characters", askedLine("stars", 100, owner = true))
        assertNull(askedLine("stars", 0, owner = true))
    }

    @Test
    fun `the bar's subtitle says time left and pay, amber when it is close`() {
        val now = 1_000_000_000L
        val hour = 3_600_000L
        assertEquals("1d 4h left, pays 20 tix" to BarTone.NORMAL, checkSubtitle(20, now + 28 * hour, null, now))
        assertEquals("7h left, pays 10 tix" to BarTone.WARN, checkSubtitle(10, now + 7 * hour, null, now))
        assertEquals("Pays 20 tix" to BarTone.NORMAL, checkSubtitle(20, null, null, now))
        assertEquals("Accepted, paid 20 tix" to BarTone.OK, checkSubtitle(20, null, SettledReview(accepted = true), now))
        assertEquals("Refused, members deciding" to BarTone.WARN, checkSubtitle(20, null, SettledReview(accepted = false), now))
    }

    @Test
    fun `the line after accepting names the reviewer by their first name`() {
        assertEquals("Tania", Waiting(id = "r1", appName = "Budget Buddy", reviewerName = "Tania Ahmed").firstName)
        assertEquals("The reviewer", Waiting(id = "r1", appName = "Budget Buddy").firstName)
    }
}
