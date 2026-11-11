package com.eazyverse.testtrack.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LateSignInTest {
    @Test
    fun aLateSuccessOfTheLatestAttemptIsUndoneWhileSignedOut() {
        assertTrue(shouldUndoLateSignIn(attempt = 3, latestAttempt = 3, apiSignedIn = false))
    }

    @Test
    fun aNewerAttemptIsNotSignedOutByAnOlderOne() {
        assertFalse(shouldUndoLateSignIn(attempt = 2, latestAttempt = 3, apiSignedIn = false))
    }

    @Test
    fun aCompletedSignInIsLeftAlone() {
        assertFalse(shouldUndoLateSignIn(attempt = 3, latestAttempt = 3, apiSignedIn = true))
    }
}
