package com.eazyverse.testtrack.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Android's screen-share ask must never be shown twice at once. */
class OneAtATimeTest {
    @Test
    fun the_first_goes_ahead() = assertTrue(OneAtATime().begin())

    @Test
    fun a_second_is_turned_away_until_the_first_ends() {
        val gate = OneAtATime()
        assertTrue(gate.begin())
        assertFalse(gate.begin())
        gate.end()
        assertTrue(gate.begin())
    }
}
