package com.eazyverse.testtrack.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class CompleteSignInTest {
    @Test
    fun successWaitsForBackendSession() =
        runBlocking {
            val backend = CompletableDeferred<Unit>()
            val events = mutableListOf<String>()
            val job =
                launch {
                    completeSignIn(
                        authenticate = {
                            events.add("google")
                            "account"
                        },
                        establishSession = {
                            backend.await()
                            events.add("backend")
                        },
                        publish = { events.add("success") },
                    )
                }
            yield()
            assertEquals(listOf("google"), events)
            backend.complete(Unit)
            job.join()
            assertEquals(listOf("google", "backend", "success"), events)
        }

    @Test
    fun backendFailureNeverPublishesSuccess() =
        runBlocking {
            var published = false
            val result =
                runCatching {
                    completeSignIn(
                        authenticate = { "account" },
                        establishSession = { throw IOException("offline") },
                        publish = { published = true },
                    )
                }
            assertFalse(result.isSuccess)
            assertFalse(published)
        }

    @Test
    fun cancelledSignInNeverResumesThePendingAction() =
        runBlocking {
            var published = false
            val backend = CompletableDeferred<Unit>()
            val job =
                launch {
                    completeSignIn(
                        authenticate = { "account" },
                        establishSession = { backend.await() },
                        publish = { published = true },
                    )
                }
            yield()
            job.cancelAndJoin()
            backend.complete(Unit)
            assertFalse(published)
        }
}
