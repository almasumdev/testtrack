package com.eazyverse.testtrack.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's every-night grid draws a night the tester did not owe as neutral, the way the server
 * judges it: reported, let off for them or for the app, or covered by a quiet skip. Only a night
 * they owed and left unopened is a miss.
 */
class MemberNightsTest {
    private fun tester(
        excused: Boolean = false,
        free: Set<Int> = emptySet(),
        skipped: Set<Int> = emptySet(),
    ) = MemberNights("s1", "Ana", day = 8, out = false, nights = emptyList(), excused = excused, freeDays = free, skippedDays = skipped)

    @Test
    fun `a night a quiet skip covered is not owed`() {
        assertTrue(tester(skipped = setOf(3)).notOwed(3, emptySet()))
        assertFalse(tester(skipped = setOf(3)).notOwed(4, emptySet()))
    }

    @Test
    fun `nights let off for the tester or for the app are not owed`() {
        assertTrue(tester(free = setOf(5)).notOwed(5, emptySet()))
        assertTrue(tester().notOwed(6, setOf(6)))
    }

    @Test
    fun `an app reported before the run is not owed on any night`() {
        assertTrue(tester(excused = true).notOwed(2, emptySet()))
    }

    @Test
    fun `any other night is owed`() {
        assertFalse(tester().notOwed(2, emptySet()))
    }
}
