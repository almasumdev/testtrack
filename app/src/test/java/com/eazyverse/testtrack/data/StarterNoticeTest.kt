package com.eazyverse.testtrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The "this phone already had its starter tix" notice goes only to the account it is about.
 *
 * One account's session refreshed in the background answers too; when another account then signed
 * in, it could be told it starts at 0 although its own sign-in said nothing of the kind.
 */
class StarterNoticeTest {
    @Test
    fun `a phone already used is told once per account`() {
        assertEquals("a", StarterNotice.pendingAfter("a", "phone_used", shown = false))
        assertNull(StarterNotice.pendingAfter("a", "phone_used", shown = true))
    }

    @Test
    fun `an account that got its tix, or none to give, hears nothing`() {
        assertNull(StarterNotice.pendingAfter("b", "given", shown = false))
        assertNull(StarterNotice.pendingAfter("b", "none", shown = false))
    }

    @Test
    fun `a newer answer replaces an older account's`() {
        // A's refresh said phone_used; B then signs in and its own answer is all that counts.
        var pending = StarterNotice.pendingAfter("a", "phone_used", shown = false)
        pending = StarterNotice.pendingAfter("b", "given", shown = false)
        assertFalse(StarterNotice.isFor(pending, "b"))
    }

    @Test
    fun `the notice is shown only to the account it is for`() {
        assertTrue(StarterNotice.isFor("a", "a"))
        assertFalse(StarterNotice.isFor("a", "b"))
        assertFalse(StarterNotice.isFor(null, "a"))
        assertFalse(StarterNotice.isFor("a", null))
    }
}
