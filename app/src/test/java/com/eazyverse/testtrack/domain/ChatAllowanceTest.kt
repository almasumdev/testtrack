package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the composer shows and allows, from the day's allowance the server sent. */
class ChatAllowanceTest {
    private val limits = ChatLimits(maxLength = 10, maxMentions = 2)

    @Test
    fun `the count warns at two and is bad at none`() {
        assertEquals(Left.PLENTY, ChatAllowance.level(7))
        assertEquals(Left.PLENTY, ChatAllowance.level(3))
        assertEquals(Left.LOW, ChatAllowance.level(2))
        assertEquals(Left.LOW, ChatAllowance.level(1))
        assertEquals(Left.NONE, ChatAllowance.level(0))
        assertEquals(Left.NONE, ChatAllowance.level(-1))
    }

    @Test
    fun `pictures only speak up at none`() {
        assertEquals(Left.PLENTY, ChatAllowance.pictureLevel(1))
        assertEquals(Left.NONE, ChatAllowance.pictureLevel(0))
        assertEquals("picture", ChatAllowance.pictureWord(1))
        assertEquals("pictures", ChatAllowance.pictureWord(2))
    }

    @Test
    fun `send needs words, a message left and nothing wrong`() {
        val draft = ChatDraft("Hi all")
        assertTrue(ChatAllowance.canSend(draft, DraftProblem.NONE, 1, canPost = true))
        assertFalse(ChatAllowance.canSend(draft, DraftProblem.NONE, 0, canPost = true))
        assertFalse(ChatAllowance.canSend(ChatDraft("   "), DraftProblem.NONE, 5, canPost = true))
        assertFalse(ChatAllowance.canSend(draft, DraftProblem.LINK, 5, canPost = true))
        assertFalse(ChatAllowance.canSend(draft, DraftProblem.NONE, 5, canPost = false))
    }

    @Test
    fun `a picture needs a message and a picture left`() {
        assertTrue(ChatAllowance.canPickPicture(DraftProblem.NONE, 1, 1, canPost = true))
        assertFalse(ChatAllowance.canPickPicture(DraftProblem.NONE, 0, 3, canPost = true))
        assertFalse(ChatAllowance.canPickPicture(DraftProblem.NONE, 4, 0, canPost = true))
        assertFalse(ChatAllowance.canPickPicture(DraftProblem.LINK, 4, 2, canPost = true))
    }

    @Test
    fun `the problem is the link first, then length, then mentions`() {
        assertEquals(DraftProblem.LINK, ChatAllowance.problem(ChatDraft("see a.com now and more"), limits, emptySet()))
        assertEquals(DraftProblem.TOO_LONG, ChatAllowance.problem(ChatDraft("eleven char"), limits, emptySet()))
        assertEquals(DraftProblem.NONE, ChatAllowance.problem(ChatDraft("ten chars!"), limits, emptySet()))
        val three =
            ChatDraft(
                "@A @B @C",
                listOf(MentionSpan(0, 2, false, "a"), MentionSpan(3, 5, false, "b"), MentionSpan(6, 8, true, "c")),
            )
        assertEquals(DraftProblem.TOO_MANY_MENTIONS, ChatAllowance.problem(three, limits, emptySet()))
    }

    @Test
    fun `a mention counts as one character`() {
        val draft = ChatDraft("@Amir Haddad hi", listOf(MentionSpan(0, 12, false, "s1")))
        assertEquals(4, draft.visibleLength())
        assertEquals(DraftProblem.NONE, ChatAllowance.problem(draft, limits, emptySet()))
    }

    @Test
    fun `length is counted in code points`() {
        assertEquals(2, ChatDraft("\uD83D\uDE00\uD83D\uDC4D").visibleLength())
        assertEquals(2, ChatDraft("  hi  ").visibleLength())
    }

    @Test
    fun `polling slows down while quiet and is capped`() {
        assertEquals(5_000L, ChatPolling.nextDelayMs(5, 30, 0, 0))
        assertEquals(7_500L, ChatPolling.nextDelayMs(5, 30, 1, 0))
        assertEquals(11_250L, ChatPolling.nextDelayMs(5, 30, 0, 2))
        assertEquals(30_000L, ChatPolling.nextDelayMs(5, 30, 9, 0))
        assertEquals(5_000L, ChatPolling.nextDelayMs(5, 2, 4, 0))
    }
}
