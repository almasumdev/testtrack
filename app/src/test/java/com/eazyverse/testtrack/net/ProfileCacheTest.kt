package com.eazyverse.testtrack.net

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The You page's kept profile: shown for its own account only, and gone with the session. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ProfileCacheTest {
    private val alice =
        ApiProfile(
            id = "u1",
            email = "alice@example.com",
            displayName = "Alice",
            photoUrl = "https://api.example.com/photos/abc.jpg",
            timezone = "Asia/Dhaka",
            tix = 120,
            opens = 4,
            reviews = 2,
            points = 30,
            place = 7,
            crewsRunning = 1,
            crewsFinished = 0,
            joinedAt = "2026-09-01T00:00:00Z",
        )

    @Before fun signIn() {
        ApiSession.load(RuntimeEnvironment.getApplication())
        ApiSession.clear()
        ApiSession.remember(Tokens("access", "refresh", 900))
    }

    @After fun cleanUp() = ApiSession.clear()

    @Test fun keptProfileIsReadBackForItsAccount() {
        ProfileCache.keep(alice, ApiSession.generation)
        assertEquals(alice, ProfileCache.read("alice@example.com"))
        assertEquals(alice, ProfileCache.read("Alice@Example.com"))
        assertEquals(alice, ProfileCache.read(null))
    }

    @Test fun anotherAccountNeverSeesIt() {
        ProfileCache.keep(alice, ApiSession.generation)
        assertNull(ProfileCache.read("bob@example.com"))
    }

    @Test fun signOutClearsIt() {
        ProfileCache.keep(alice, ApiSession.generation)
        ApiSession.clear()
        assertNull(ProfileCache.read("alice@example.com"))
        // Signed in again, as anybody: the file itself is gone, not just hidden.
        ApiSession.remember(Tokens("next", "next", 900))
        assertNull(ProfileCache.read(null))
        assertNull(ApiCache.read(Api.PROFILE))
    }

    @Test fun aWriteFromAnEndedSessionIsRefused() {
        val old = ApiSession.generation
        ApiSession.clear()
        ApiSession.remember(Tokens("next", "next", 900))
        ProfileCache.keep(alice, old)
        assertNull(ProfileCache.read(null))
    }
}
