package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The words the three new tabs put on screen.
 *
 * Small arithmetic, but the kind that is read by everybody and noticed the moment it is wrong:
 * an ordinal that says "11st", or a line under an app that claims fourteen testers on a crew
 * that has not filled.
 */
class StandingTest {
    @Test
    fun the_ordinary_ordinals_read_properly() {
        assertEquals("1st", ordinal(1))
        assertEquals("2nd", ordinal(2))
        assertEquals("3rd", ordinal(3))
        assertEquals("4th", ordinal(4))
        assertEquals("21st", ordinal(21))
        assertEquals("42nd", ordinal(42))
    }

    @Test
    fun eleven_to_thirteen_are_the_ones_every_naive_version_gets_wrong() {
        // And they are exactly the places a board of fifty is full of.
        assertEquals("11th", ordinal(11))
        assertEquals("12th", ordinal(12))
        assertEquals("13th", ordinal(13))
        assertEquals("111th", ordinal(111))
        assertEquals("112th", ordinal(112))
    }

    @Test
    fun nobody_is_ranked_before_they_have_done_anything() {
        assertEquals("unranked", ordinal(0))
        assertEquals("unranked", ordinal(-1))
    }
}
