package com.eazyverse.testtrack.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiSessionTest {
    @Test fun headerCacheIsScopedToAccount() {
        ApiSession.remember(Tokens("access", "refresh", 900))
        val snapshot = HeaderSnapshot("alice", 4, 120, 200, 240)
        assertTrue(ApiSession.rememberHeader(snapshot, ApiSession.generation))
        assertEquals(snapshot, ApiSession.cachedHeader("alice"))
        assertEquals(null, ApiSession.cachedHeader("bob"))
        assertEquals(null, ApiSession.cachedHeader(null))
    }

    @Test fun signOutErasesCachedHeaderAndRejectsLateProfile() {
        ApiSession.remember(Tokens("access", "refresh", 900))
        val snapshot = HeaderSnapshot("alice", 4, 120, 200, 240)
        val generation = ApiSession.generation
        ApiSession.rememberHeader(snapshot, generation)
        ApiSession.clear()
        ApiSession.remember(Tokens("new", "new", 900))
        assertFalse(ApiSession.rememberHeader(snapshot, generation))
        assertEquals(null, ApiSession.cachedHeader("alice"))
    }

    @Before fun reset() = ApiSession.clear()

    @After fun cleanUp() = ApiSession.clear()

    @Test fun readyStateAndCredentialsChangeTogether() {
        assertFalse(ApiSession.signedInState.value)
        ApiSession.remember(Tokens("access", "refresh", 900))
        assertTrue(ApiSession.signedInState.value)
        assertTrue(ApiSession.signedIn)
        ApiSession.clear()
        assertFalse(ApiSession.signedInState.value)
        assertEquals(null, ApiSession.access)
        assertEquals(null, ApiSession.refresh)
    }

    @Test fun lateResponseCannotUndoSignOut() {
        val generation = ApiSession.generation
        ApiSession.clear()
        assertFalse(ApiSession.remember(Tokens("stale", "stale", 900), generation))
        assertFalse(ApiSession.signedInState.value)
    }

    @Test fun oldAccountResponseCannotReplaceNewSession() {
        val oldGeneration = ApiSession.generation
        ApiSession.clear()
        ApiSession.remember(Tokens("new", "new", 900))
        assertFalse(ApiSession.remember(Tokens("old", "old", 900), oldGeneration))
        assertEquals("new", ApiSession.access)
    }
}
