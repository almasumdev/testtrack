package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Ask page's own rules: a level lifts the pay to its least, leaving detailed drops the ready
 * reviews, each ready review says what is wrong with it in place, and the hint names what stops
 * the request, the ready reviews before the balance.
 */
class AskViewModelTest {
    private val long = "a".repeat(FeedRules.Feedback.DETAILED.chars)
    private val other = "b".repeat(FeedRules.Feedback.DETAILED.chars)

    @Test
    fun `choosing a level lifts the pay to its least and never lowers it`() {
        val vm = AskViewModel()
        assertNull(vm.tix)
        vm.chooseFeedback(FeedRules.Feedback.WRITTEN)
        assertEquals(FeedRules.Feedback.WRITTEN, vm.feedback)
        assertEquals(35, vm.reward)
        vm.chooseFeedback(FeedRules.Feedback.DETAILED)
        assertEquals(50, vm.reward)
        vm.changeReward(120)
        vm.chooseFeedback(FeedRules.Feedback.STARS)
        assertEquals(120, vm.reward)
    }

    @Test
    fun `the pay stays between the level's least and the most`() {
        val vm = AskViewModel()
        vm.chooseFeedback(FeedRules.Feedback.WRITTEN)
        vm.changeReward(10)
        assertEquals(35, vm.reward)
        vm.changeReward(500)
        assertEquals(FeedRules.MAX_REWARD, vm.reward)
    }

    @Test
    fun `leaving detailed clears the ready reviews`() {
        val vm = AskViewModel()
        vm.chooseFeedback(FeedRules.Feedback.DETAILED)
        vm.addReady()
        vm.editReady(0, long)
        assertEquals(1, vm.readyTexts.size)
        vm.chooseFeedback(FeedRules.Feedback.WRITTEN)
        assertTrue(vm.readyTexts.isEmpty())
    }

    @Test
    fun `fewer reviews drops blank ready reviews down to the new count and keeps written ones`() {
        val vm = AskViewModel()
        vm.chooseFeedback(FeedRules.Feedback.DETAILED)
        vm.changeReviewCount(3)
        repeat(3) { vm.addReady() }
        vm.addReady()
        assertEquals(3, vm.readyTexts.size)
        vm.editReady(0, long)
        vm.changeReviewCount(1)
        assertEquals(listOf(long), vm.readyTexts.toList())
        vm.changeReviewCount(0)
        assertEquals(FeedRules.MIN_REVIEWS, vm.reviewCount)
        vm.changeReviewCount(99)
        assertEquals(FeedRules.MAX_REVIEWS, vm.reviewCount)
    }

    @Test
    fun `a ready review that is too short says how far it got`() {
        assertEquals(listOf("5 of at least 250 characters"), readyErrors(listOf("  hello  ")))
        assertEquals(listOf<String?>(null), readyErrors(listOf(long)))
    }

    @Test
    fun `a blank ready review has no error`() {
        assertEquals(listOf<String?>(null, null), readyErrors(listOf("", "   ")))
    }

    @Test
    fun `a ready review the same as an earlier one names it, ignoring case and the ends`() {
        val errors = readyErrors(listOf(long, other, "  ${long.uppercase()} ", other))
        assertEquals(listOf(null, null, "Same as ready review 1", "Same as ready review 2"), errors)
    }

    @Test
    fun `a short duplicate is told it is short first`() {
        assertEquals(listOf("2 of at least 250 characters", "2 of at least 250 characters"), readyErrors(listOf("hi", "HI")))
    }

    @Test
    fun `the view model reports each field's error in place`() {
        val vm = AskViewModel()
        vm.chooseFeedback(FeedRules.Feedback.DETAILED)
        repeat(2) { vm.addReady() }
        vm.editReady(0, long)
        vm.editReady(1, long)
        assertEquals(listOf(null, "Same as ready review 1"), vm.readyErrors)
    }

    @Test
    fun `nothing blocks while the balance is unknown`() {
        assertNull(askBlock(tix = null, cost = 500, readyOk = false))
        assertNull(AskViewModel().blockReason)
    }

    @Test
    fun `the ready reviews block before the balance`() {
        assertEquals(AskBlock.READY, askBlock(tix = 10, cost = 500, readyOk = false))
        assertEquals(AskBlock.SHORT, askBlock(tix = 10, cost = 500, readyOk = true))
        assertNull(askBlock(tix = 500, cost = 500, readyOk = true))
    }
}
