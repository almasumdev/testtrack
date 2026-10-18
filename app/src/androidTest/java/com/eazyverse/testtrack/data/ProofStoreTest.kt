package com.eazyverse.testtrack.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    /**
     * A real picture of one colour. The store decodes and re-encodes what it keeps, to drop the
     * source's metadata, so made-up bytes are refused and the copy is never byte for byte the same.
     */
    private fun picture(
        name: String,
        color: Int,
    ) = File(context.cacheDir, name).apply {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** The colour in the middle of a kept file, which survives JPEG's rounding close enough. */
    private fun colorOf(file: File): Int {
        val pixel = BitmapFactory.decodeFile(file.path).getPixel(4, 4)
        return if (Color.red(pixel) > 200 && Color.green(pixel) < 60) {
            Color.RED
        } else if (Color.blue(pixel) > 200) {
            Color.BLUE
        } else {
            pixel
        }
    }

    @Test
    fun a_picked_screenshot_is_copied_in_and_found_again() {
        val source = picture("picked.png", Color.RED)
        ProofStore.keep(context, id, Uri.fromFile(source))
        source.delete() // the picker's original is gone; the kept copy is not

        val kept = ProofStore.proof(context, id)
        assertEquals(Color.RED, colorOf(kept!!))
    }

    @Test
    fun a_capture_moves_out_of_the_cache_and_a_new_one_replaces_the_old() {
        ProofStore.adopt(context, id, picture("one.jpg", Color.RED))
        val second = picture("two.jpg", Color.BLUE)

        val first = ProofStore.proof(context, id)!!
        ProofStore.adopt(context, id, second)
        val now = ProofStore.proof(context, id)!!

        assertFalse(second.exists())
        assertEquals(Color.BLUE, colorOf(now))
        // A different file, so the page sees a different picture rather than the one it had.
        assertNotEquals(first.path, now.path)
        assertFalse(first.exists())
        assertEquals("image/jpeg", ProofStore.mime(context, id))
    }

    @Test
    fun the_note_and_the_trip_to_play_are_kept_until_sent() {
        ProofStore.saveNote(context, id, "Crashes on rotate")
        ProofStore.markLeft(context, id)
        ProofStore.adopt(context, id, picture("c.jpg", Color.RED))

        assertEquals("Crashes on rotate", ProofStore.note(context, id))
        assertTrue(ProofStore.left(context, id))

        ProofStore.forget(context, id)
        assertNull(ProofStore.proof(context, id))
        assertEquals("", ProofStore.note(context, id))
        assertFalse(ProofStore.left(context, id))
    }
}
