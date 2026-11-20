package com.eazyverse.testtrack.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeping a version with details hidden and sending the review take turns: a send that comes while
 * the hidden version is on its way waits for it and sends it, never the one it replaces. The store
 * here is a fake: a name for the kept file, and the uploads that went.
 */
class ProofGateTest {
    private class FakeStore {
        var kept: String? = "original.jpg"
        val uploaded = mutableListOf<String>()
    }

    @Test
    fun aSendDuringAReplacementWaitsAndSendsTheHiddenVersion() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore()
            val written = CompletableDeferred<Unit>()
            val replacing = launch { gate.replace { written.await().also { store.kept = "hidden.jpg" } } }
            yield()
            assertTrue(gate.replacing)

            val sending = async { gate.send({ store.kept }) { file -> store.uploaded += file } }
            yield()
            assertTrue("nothing goes while the hidden version is written", store.uploaded.isEmpty())

            written.complete(Unit)
            replacing.join()
            sending.await()
            assertEquals(listOf("hidden.jpg"), store.uploaded)
            assertFalse(gate.replacing)
        }

    @Test
    fun aReplacementDuringASendWaitsForTheSendToFinish() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore()
            val uploading = CompletableDeferred<Unit>()
            val sending = launch { gate.send({ store.kept }) { file -> uploading.await().also { store.uploaded += file } } }
            yield()
            val replacing = launch { gate.replace { store.kept = "hidden.jpg" } }
            yield()
            assertEquals("the kept file is not swapped under an upload", "original.jpg", store.kept)

            uploading.complete(Unit)
            sending.join()
            replacing.join()
            assertEquals(listOf("original.jpg"), store.uploaded)
            assertEquals("hidden.jpg", store.kept)
        }

    @Test
    fun aFailedReplacementLeavesTheOldOneAndSaysSo() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore()
            val failure = runCatching { gate.replace<Unit> { error("could not be kept") } }
            assertTrue(failure.isFailure)
            assertFalse(gate.replacing)
            assertEquals("original.jpg", store.kept)
        }

    @Test
    fun nothingKeptSendsNothing() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore().apply { kept = null }
            assertNull(gate.send({ store.kept }) { file -> store.uploaded += file })
            assertTrue(store.uploaded.isEmpty())
        }
}
