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

        /** What the page shows, read from the store the way the view model's showProof does. */
        var shown: String? = "original.jpg"

        fun show() {
            shown = kept
        }
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

    @Test
    fun aCaptureTakenOverAfterAnEditIsWhatThePageShowsAndWhatGoes() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore()
            val hidden = CompletableDeferred<Unit>()
            val editing = launch { gate.replace(store::show) { hidden.await().also { store.kept = "hidden.jpg" } } }
            yield()
            val capture =
                launch {
                    gate.replace(store::show) {
                        assertEquals("the edit was shown inside its own turn", "hidden.jpg", store.shown)
                        store.kept = "capture.jpg"
                    }
                }
            yield()

            hidden.complete(Unit)
            editing.join()
            capture.join()
            assertEquals("capture.jpg", store.kept)
            assertEquals("the page shows the new capture, not the hidden one it replaced", "capture.jpg", store.shown)
            gate.send({ store.kept }) { file -> store.uploaded += file }
            assertEquals(listOf(store.shown), store.uploaded)
        }

    @Test
    fun aFailedReplacementShowsWhatIsStillKept() =
        runBlocking {
            val gate = ProofGate()
            val store = FakeStore().apply { shown = null }
            assertTrue(runCatching { gate.replace<Unit>(store::show) { error("could not be kept") } }.isFailure)
            assertEquals("original.jpg", store.shown)
        }
}
