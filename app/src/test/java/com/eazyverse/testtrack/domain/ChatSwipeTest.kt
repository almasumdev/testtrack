package com.eazyverse.testtrack.domain

import com.eazyverse.testtrack.domain.ChatSwipe.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The swipe-to-reply arithmetic: when a drag is a swipe, how the message follows it, when it replies. */
class ChatSwipeTest {
    private val slop = 8f

    @Test
    fun `a clearly sideways drag toward the middle starts the swipe`() {
        assertEquals(Start.SWIPE, ChatSwipe.start(dx = 12f, dy = 2f, slop = slop, mine = false))
        assertEquals(Start.SWIPE, ChatSwipe.start(dx = -12f, dy = 2f, slop = slop, mine = true))
    }

    @Test
    fun `a little movement waits`() {
        assertEquals(Start.WAIT, ChatSwipe.start(dx = 4f, dy = 1f, slop = slop, mine = false))
        // Sideways but not yet clearly: neither the list's scroll nor a swipe has won.
        assertEquals(Start.WAIT, ChatSwipe.start(dx = 9f, dy = 6f, slop = slop, mine = false))
    }

    @Test
    fun `scrolling up and down is left to the list`() {
        assertEquals(Start.SKIP, ChatSwipe.start(dx = 3f, dy = 14f, slop = slop, mine = false))
        assertEquals(Start.SKIP, ChatSwipe.start(dx = 12f, dy = 20f, slop = slop, mine = false))
        assertEquals(Start.SKIP, ChatSwipe.start(dx = -12f, dy = -20f, slop = slop, mine = true))
    }

    @Test
    fun `a drag away from the middle is not ours`() {
        assertEquals(Start.SKIP, ChatSwipe.start(dx = -12f, dy = 1f, slop = slop, mine = false))
        assertEquals(Start.SKIP, ChatSwipe.start(dx = 12f, dy = 1f, slop = slop, mine = true))
    }

    @Test
    fun `the message follows the finger with growing resistance and a ceiling`() {
        assertEquals(0f, ChatSwipe.travel(0f), 0.001f)
        assertEquals(0f, ChatSwipe.travel(-30f), 0.001f)
        // Close to one to one at the start.
        assertEquals(10f, ChatSwipe.travel(10f), 0.6f)
        val a = ChatSwipe.travel(40f)
        val b = ChatSwipe.travel(80f)
        val c = ChatSwipe.travel(400f)
        assertTrue(a in 0f..40f)
        assertTrue(b - a < 40f)
        assertTrue(b > a && c > b)
        assertTrue(c < ChatSwipe.TRAVEL)
    }

    @Test
    fun `the reply counts from the threshold`() {
        assertFalse(ChatSwipe.passed(ChatSwipe.THRESHOLD - 1f))
        assertTrue(ChatSwipe.passed(ChatSwipe.THRESHOLD))
        assertEquals(0f, ChatSwipe.progress(0f), 0.001f)
        assertEquals(0.5f, ChatSwipe.progress(ChatSwipe.THRESHOLD / 2), 0.001f)
        assertEquals(1f, ChatSwipe.progress(500f), 0.001f)
    }
}
