package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewSignInTest {
    @Test
    fun aSignInReadsBackAsItWasWritten() {
        val notes = signInNotes(" test@example.com ", "hunter2", "Code is 1234\nthen tap Skip")
        assertEquals(SignIn("test@example.com", "hunter2", "Code is 1234\nthen tap Skip"), readSignIn(notes))
    }

    @Test
    fun emptyPartsAreLeftOut() {
        assertEquals("Password: pw", signInNotes("", "pw", " "))
        assertEquals(SignIn("", "pw", ""), readSignIn("Password: pw"))
    }

    @Test
    fun theLongestSignInStillFitsTheSeatNote() {
        val notes = signInNotes("u".repeat(500), "p".repeat(500), "a".repeat(500))
        assertTrue(notes.length <= 280)
    }

    @Test
    fun aNoteWrittenAnyOtherWayIsNotReadAsASignIn() {
        assertNull(readSignIn("Test account: demo@example.com / demo"))
        assertNull(readSignIn("Also: only instructions"))
        assertNull(readSignIn("Username: a\nsomething else"))
    }
}
