package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewJoinStepsTest {
    private val ready = CrewJoinState(closedApp = true, tix = 240)

    @Test
    fun nothing_missing_lists_no_steps() {
        assertTrue(ready.missing.isEmpty())
    }

    @Test
    fun signed_out_lists_only_sign_in() {
        val state = CrewJoinState(signedOut = true, member = false, closedApp = false, tix = 0)
        assertEquals(listOf(JoinStep.SIGN_IN), state.missing)
    }

    @Test
    fun steps_come_in_the_order_they_are_done() {
        val state = CrewJoinState(member = false, closedApp = false, tix = 40)
        assertEquals(listOf(JoinStep.GROUP, JoinStep.APP, JoinStep.TIX), state.missing)
    }

    @Test
    fun an_answer_that_has_not_arrived_is_not_a_step() {
        val state = CrewJoinState(member = true, closedApp = null, tix = null)
        assertTrue(state.missing.isEmpty())
    }

    @Test
    fun the_seat_price_itself_is_enough() {
        assertTrue(ready.copy(tix = CrewRules.SEAT_COST).missing.isEmpty())
        assertEquals(listOf(JoinStep.TIX), ready.copy(tix = CrewRules.SEAT_COST - 1).missing)
    }

    @Test
    fun only_the_group_missing_is_one_step() {
        assertEquals(listOf(JoinStep.GROUP), ready.copy(member = false).missing)
        assertEquals("1 step before you can join", joinHeading(1, signedOut = false))
    }

    @Test
    fun the_heading_counts_what_is_left() {
        assertEquals("3 steps before you can join", joinHeading(3, signedOut = false))
        assertEquals("Before you can join", joinHeading(1, signedOut = true))
    }

    @Test
    fun the_permissions_row_is_not_a_step() {
        assertTrue(ready.copy(permissions = false).missing.isEmpty())
    }
}
