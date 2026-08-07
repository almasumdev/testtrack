package com.eazyverse.testtrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic behind an automatic removal.
 *
 * Worth testing on its own because it is duplicated: `firstJudged` and `missedTwo` in
 * firestore.rules compute the same thing in another language, and the two have to agree or the
 * client asks for evictions the database refuses. The cases here mirror the ones in
 * firestore-tests/rules.test.mjs one for one.
 */
class ComplianceTest {

    private val day = 86_400_000L
    private val start = 1_700_000_000_000L

    @Test
    fun `today is never judged`() {
        // Day 0: nothing has finished, so there is nothing to have missed.
        assertEquals(null, Compliance.lastCompletedDay(0))
        assertEquals(0, Compliance.lastCompletedDay(1))
        assertEquals(2, Compliance.lastCompletedDay(3))
    }

    @Test
    fun `founding members are held to the whole run`() {
        // Placed before the clock started, which is what founding means.
        assertEquals(0, Compliance.firstJudgedDay(start - 3600_000L, start))
        assertEquals(0, Compliance.firstJudgedDay(start, start))
    }

    @Test
    fun `an app with no placement date is not judged from day zero by accident`() {
        // Zero means unknown, and unknown must not read as "placed at the epoch". Enforcement
        // skips these outright; this only pins the arithmetic that would otherwise say day 0.
        assertEquals(0, Compliance.firstJudgedDay(0L, start))
        assertEquals(0, Compliance.firstJudgedDay(1234L, 0L))
    }

    @Test
    fun `a mid-run joiner is judged from the day after they arrived`() {
        // Arriving part way through day 2 does not make day 2 theirs to have missed.
        assertEquals(3, Compliance.firstJudgedDay(start + 2 * day + 1000L, start))
        assertEquals(10, Compliance.firstJudgedDay(start + 9 * day, start))
    }

    @Test
    fun `nobody can be removed before day three`() {
        val founder = start - 1000L
        // Day 1 has one finished day behind it, day 2 has two.
        assertTrue(Compliance.judgedDays(1, start, founder, founder, 2).size < 2)
        assertEquals(listOf(1, 0), Compliance.judgedDays(2, start, founder, founder, 2))
    }

    @Test
    fun `the later of the two arrivals bounds the days owed`() {
        val founder = start - 1000L
        val joinedDayTwo = start + 2 * day + 1000L

        // Day 3: the pair would be days 2 and 1, but this member only owes from day 3.
        assertEquals(emptyList<Int>(), Compliance.judgedDays(3, start, joinedDayTwo, founder, 2))

        // The same bound applies when it is the *app* that arrived late — there was nothing
        // there to open before it was placed.
        assertEquals(emptyList<Int>(), Compliance.judgedDays(3, start, founder, joinedDayTwo, 2))

        // By day 5 both days of the pair are on their side of the line.
        assertEquals(listOf(4, 3), Compliance.judgedDays(5, start, joinedDayTwo, founder, 2))
    }

    @Test
    fun `a finished run judges nothing`() {
        // dayIndex is null past the fourteenth day, so judgedDays is never reached — but a run
        // still going on its last day judges the pair before it.
        assertEquals(listOf(12, 11), Compliance.judgedDays(13, start, start - 1000L, start - 1000L, 2))
    }
}
