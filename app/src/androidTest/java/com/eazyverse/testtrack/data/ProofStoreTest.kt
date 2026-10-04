package com.eazyverse.testtrack.data

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The proof step's work kept across leaving the page: backing out and coming back used to ask
 * for the screenshot, the note and the trip to Play all over again.
 */
@RunWith(AndroidJUnit4::class)
class ProofStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val id = "test-review-proof"

    @After
    fun clean() = ProofStore.forget(context, id)

    private fun picture(
        name: String,
        bytes: ByteArray,
    ) = File(context.cacheDir, name).apply { writeBytes(bytes) }

    @Test
    fun a_picked_screenshot_is_copied_in_and_found_again() {
        val source = picture("picked.png", byteArrayOf(1, 2, 3))
        ProofStore.keep(context, id, Uri.fromFile(source))
        source.delete() // the picker's original is gone; the kept copy is not

        val kept = ProofStore.proof(context, id)
        assertArrayEquals(byteArrayOf(1, 2, 3), kept!!.readBytes())
    }

    @Test
    fun a_capture_moves_out_of_the_cache_and_a_new_one_replaces_the_old() {
        ProofStore.adopt(context, id, picture("one.jpg", byteArrayOf(1)))
        val second = picture("two.jpg", byteArrayOf(2, 2))
        ProofStore.adopt(context, id, second)

        assertFalse(second.exists())
        assertArrayEquals(byteArrayOf(2, 2), ProofStore.proof(context, id)!!.readBytes())
        assertEquals("image/jpeg", ProofStore.mime(context, id))
    }

    @Test
    fun the_note_and_the_trip_to_play_are_kept_until_sent() {
        ProofStore.saveNote(context, id, "Crashes on rotate")
        ProofStore.markLeft(context, id)
        ProofStore.adopt(context, id, picture("c.jpg", byteArrayOf(9)))

        assertEquals("Crashes on rotate", ProofStore.note(context, id))
        assertTrue(ProofStore.left(context, id))

        ProofStore.forget(context, id)
        assertNull(ProofStore.proof(context, id))
        assertEquals("", ProofStore.note(context, id))
        assertFalse(ProofStore.left(context, id))
    }
}
