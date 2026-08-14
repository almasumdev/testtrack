package com.eazyverse.testtrack.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard that decides whether a membership verdict is ours to act on.
 *
 * Tested because this is where the group step told people the opposite of the truth, in both
 * directions, and because the failure is invisible: a verdict about the wrong Gmail is a perfectly
 * well formed answer, and nothing downstream can tell it apart from a right one.
 *
 * The asymmetry in the last two cases is the point. Rejecting is only ever safe when the service
 * named an address and it was somebody else's; a reply that names nobody has to be trusted,
 * because a version of the service that stopped echoing the address would otherwise turn the
 * whole gate off and nobody would ever be let in again.
 */
class GateResultTest {

    private val us = "tester@gmail.com"

    @Test
    fun `a verdict about the signed-in account is ours`() {
        assertFalse(GateResult.Member(us).isAboutSomeoneElse(us))
        assertFalse(GateResult.NotMember(us).isAboutSomeoneElse(us))
    }

    @Test
    fun `case is not a different person`() {
        // Google echoes the address as stored, which is not always how it was typed.
        assertFalse(GateResult.Member("Tester@Gmail.com").isAboutSomeoneElse(us))
    }

    @Test
    fun `nor is a dot, which is how this shipped broken`() {
        // The service folds dots before it looks anybody up, so it answers about the dotless
        // form while the phone holds whatever the account sheet reported. Comparing the two
        // strings told a real member, in the app, that Google had answered for somebody else.
        // Four of twelve members write their address with dots.
        assertFalse(GateResult.Member("mdbillamaruf@gmail.com").isAboutSomeoneElse("md.billamaruf@gmail.com"))
        assertFalse(GateResult.NotMember("md.billamaruf@gmail.com").isAboutSomeoneElse("mdbillamaruf@gmail.com"))
    }

    @Test
    fun `nor a plus suffix, nor googlemail`() {
        assertFalse(GateResult.Member("tester+play@gmail.com").isAboutSomeoneElse(us))
        assertFalse(GateResult.Member("tester@googlemail.com").isAboutSomeoneElse(us))
    }

    @Test
    fun `a dot still separates two people outside gmail`() {
        // Only Google promises dots are decoration. Everywhere else they are part of the name,
        // and folding them there would merge two genuinely different accounts.
        assertTrue(
            GateResult.Member("first.last@example.com").isAboutSomeoneElse("firstlast@example.com")
        )
    }

    @Test
    fun `a verdict about another account is rejected either way`() {
        // The one that let a non-member through: Credential Manager handed back the other Google
        // account on the phone, which really is in the group.
        assertTrue(GateResult.Member("someone.else@gmail.com").isAboutSomeoneElse(us))
        // And the one that shut a real member out.
        assertTrue(GateResult.NotMember("someone.else@gmail.com").isAboutSomeoneElse(us))
    }

    @Test
    fun `a failure is nobody's verdict, so there is nothing to reject`() {
        // Failed is already handled as "no answer" by every caller. It must not also be reported
        // as a mismatch, or the reason the service gave gets replaced by a wrong-account message.
        assertFalse(GateResult.Failed("HTTP 500").isAboutSomeoneElse(us))
    }

    @Test
    fun `an answer that names nobody is trusted rather than discarded`() {
        assertFalse(GateResult.Member("").isAboutSomeoneElse(us))
        assertFalse(GateResult.NotMember("").isAboutSomeoneElse(us))
    }

    @Test
    fun `with no signed-in address there is nothing to compare against`() {
        assertFalse(GateResult.Member("anyone@gmail.com").isAboutSomeoneElse(null))
        assertFalse(GateResult.Member("anyone@gmail.com").isAboutSomeoneElse(""))
    }
}
