package com.eazyverse.testtrack.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pictures waiting to send: one per app per night, and a refusal that never holds up the rest.
 *
 * Night 5's picture still waiting used to be deleted when the same app was opened on night 6, so
 * night 5 could never count; and one picture refused for good stopped every picture after it.
 */
class PictureQueueTest {
    private fun pic(
        seat: String,
        day: Int,
        path: String = "$seat-$day.jpg",
        takenAt: Long = 0L,
    ) = VisitPictures.Waiting(seatId = seat, label = seat, path = path, takenAt = takenAt, day = day)

    @Test
    fun `a picture from another night is kept beside the new one`() {
        val (queue, replaced) = PictureQueue.adding(listOf(pic("a", 5)), pic("a", 6))
        assertEquals(listOf(5, 6), queue.map { it.day })
        assertTrue(replaced.isEmpty())
    }

    @Test
    fun `a newer picture of the same app and night replaces the older`() {
        val old = pic("a", 6, path = "old.jpg")
        val (queue, replaced) = PictureQueue.adding(listOf(pic("b", 6), old), pic("a", 6, path = "new.jpg"))
        assertEquals(listOf("b-6.jpg", "new.jpg"), queue.map { it.path })
        assertEquals(listOf(old), replaced)
    }

    @Test
    fun `only tonight's picture waiting counts as sending tonight`() {
        val queue = listOf(pic("a", 5))
        assertFalse(PictureQueue.waitingTonight(queue, "a", 6))
        assertTrue(PictureQueue.waitingTonight(queue, "a", 5))
        assertFalse(PictureQueue.waitingTonight(queue, "b", 5))
    }

    @Test
    fun `a refusal for good is dropped, a server or network failure is tried again`() {
        listOf(400, 403, 404, 409, 422).forEach { assertTrue("$it", PictureQueue.refusedForGood(it)) }
        listOf(0, 401, 429, 500, 502, 503).forEach { assertFalse("$it", PictureQueue.refusedForGood(it)) }
    }

    @Test
    fun `a pass carries on past a picture refused for good`() =
        runBlocking {
            val tried = mutableListOf<String>()
            val why =
                PictureQueue.pass(listOf(pic("x", 3), pic("y", 3))) {
                    tried += it.seatId
                    PictureQueue.Step.Gone
                }
            assertNull(why)
            assertEquals(listOf("x", "y"), tried)
        }

    @Test
    fun `a pass stops at the first failure worth another try`() =
        runBlocking {
            val tried = mutableListOf<String>()
            val why =
                PictureQueue.pass(listOf(pic("x", 3), pic("y", 3), pic("z", 3))) {
                    tried += it.seatId
                    if (it.seatId == "y") PictureQueue.Step.Stop("offline") else PictureQueue.Step.Gone
                }
            assertEquals("offline", why)
            assertEquals(listOf("x", "y"), tried)
        }

    @Test
    fun `a picture waiting more than three hours is late`() {
        val taken = 1_000_000L
        assertFalse(pic("a", 1, takenAt = taken).late(taken + VisitPictures.LATE_MS))
        assertTrue(pic("a", 1, takenAt = taken).late(taken + VisitPictures.LATE_MS + 1))
    }
}
