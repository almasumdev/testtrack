package com.eazyverse.testtrack.net

import android.app.Application
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** The kept answers and sign-out: a write caught in the middle of one never outlives it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ApiCacheTest {
    @Before fun signIn() {
        ApiSession.load(RuntimeEnvironment.getApplication())
        ApiSession.clear()
        ApiSession.remember(Tokens("access", "refresh", 900))
    }

    @After fun cleanUp() {
        ApiCache.afterCheckForTest = null
        ApiSession.clear()
    }

    @Test fun aWriteInTheSameSessionIsKept() {
        ApiCache.write("/crews", "{}", ApiSession.generation)
        assertEquals("{}", ApiCache.read("/crews"))
    }

    @Test fun aWritePastItsCheckCannotOutliveSignOut() {
        val checked = CountDownLatch(1)
        val go = CountDownLatch(1)
        val before = ApiSession.generation
        // The write has passed its session check and stops there, before the file.
        ApiCache.afterCheckForTest = {
            checked.countDown()
            go.await(5, TimeUnit.SECONDS)
        }
        val writer = thread { ApiCache.write("/crews", "{\"old\":true}", before) }
        assertTrue(checked.await(5, TimeUnit.SECONDS))

        // Sign-out starts while the write is held. It may move the generation but must not finish
        // wiping the files before the write is done, or the write would land after the wipe.
        val signOut = thread { ApiSession.clear() }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (ApiSession.generation == before && System.nanoTime() < deadline) Thread.sleep(1)
        signOut.join(300)
        go.countDown()
        writer.join(5_000)
        signOut.join(5_000)

        assertNull(ApiCache.read("/crews"))
    }
}
