package com.eazyverse.testtrack.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.viewmodel.ProofEditing
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Boxes that hide details are burned into the written picture's pixels, at full size, through a
 * turn and a crop, from a screenshot too big to open at full size as well, and the saved file
 * carries no metadata. Real Skia drawing, so these are pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class PictureHideTest {
    private val ink = Color.BLACK
    private val secret = Color.RED
    private val shadow = Color.argb(128, 0, 0, 0)

    /** A white 1200 by 1600 screenshot with a red "email" at x 100 to 400, y 200 to 300. */
    private fun screenshot(): Bitmap =
        Bitmap.createBitmap(1200, 1600, Bitmap.Config.ARGB_8888).also {
            val canvas = Canvas(it)
            canvas.drawColor(Color.WHITE)
            canvas.drawRect(100f, 200f, 400f, 300f, Paint().apply { color = secret })
        }

    /** A box over the email with fractional edges, as a finger leaves them. */
    private val overEmail = Rect(100.4f, 200.6f, 399.7f, 299.2f)

    private fun Bitmap.count(test: (Int) -> Boolean): Int {
        val pixels = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }
        return pixels.count(test)
    }

    private fun reddish(pixel: Int) = Color.red(pixel) > 128 && Color.green(pixel) < 100 && Color.blue(pixel) < 100

    @Test
    fun aBoxIsSolidOpaqueInkOverEveryPixelItTouchesAtFullSize() {
        val source = screenshot()
        val out = flattenPicture(source, PictureDoc(1200, 1600, hides = listOf(overEmail)), shadow, ink)
        assertEquals(1200, out.width)
        assertEquals(1600, out.height)
        for (y in 200 until 300) {
            for (x in 100 until 400) assertEquals("pixel $x,$y", ink, out.getPixel(x, y))
        }
        assertEquals(0, out.count(::reddish))
        assertEquals(Color.WHITE, out.getPixel(50, 50))
        assertEquals(Color.WHITE, out.getPixel(600, 800))
        // The picture being edited is never painted on: only the copy written out is.
        assertEquals(secret, source.getPixel(150, 250))
    }

    @Test
    fun aBoxStaysBurnedInThroughATurnAndACrop() {
        val source = screenshot()
        // Turned a quarter, the picture is 1600 by 1200 and the email lands at x 1300 to 1400, y 100 to 400.
        val turned = PictureDoc(1200, 1600).turned().copy(crop = Rect(0.5f, 0f, 1f, 0.5f))
        val plain = flattenPicture(source, turned, shadow, ink)
        assertEquals(800, plain.width)
        assertEquals(600, plain.height)
        assertTrue("the email shows without a box", plain.count(::reddish) > 20_000)

        val hidden = flattenPicture(source, turned.copy(hides = listOf(overEmail)), shadow, ink)
        assertEquals(800, hidden.width)
        assertEquals(600, hidden.height)
        assertEquals(0, hidden.count(::reddish))
        assertEquals(ink, hidden.getPixel(550, 250))
    }

    @Test
    fun theSavedScreenshotKeepsTheBoxAndCarriesNoMetadata() {
        val context = RuntimeEnvironment.getApplication()
        val flat = flattenPicture(screenshot(), PictureDoc(1200, 1600, hides = listOf(overEmail)), shadow, ink)
        val uri = runBlocking { ProofEditing.save(context, flat) }
        val bytes = File(requireNotNull(uri.path)).readBytes()
        val saved = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        assertEquals(1200, saved.width)
        assertEquals(1600, saved.height)
        assertEquals(0, saved.count(::reddish))
        for (y in 205 until 295 step 5) {
            for (x in 105 until 395 step 5) {
                val p = saved.getPixel(x, y)
                assertTrue("pixel $x,$y", Color.red(p) < 24 && Color.green(p) < 24 && Color.blue(p) < 24)
            }
        }
        assertFalse("an EXIF block was written", 0xE1 in jpegMarkers(bytes))
    }

    /**
     * A 2400 by 3200 screenshot, over the 1600 the editor opens at, with a red name against the
     * right edge (x 2200 to 2400, y 40 to 300) and a red mark left on show (x 800 to 1000, y 400
     * to 600). It goes the whole way: opened as the editor opens it, a box over the name, a crop
     * that lands between pixels, saved, and kept by the review.
     */
    @Test
    fun aLargeScreenshotKeepsNoRedWithinABoxNearTheEdgePlusAPixel() {
        val context = RuntimeEnvironment.getApplication()
        val big =
            Bitmap.createBitmap(2400, 3200, Bitmap.Config.ARGB_8888).also {
                val canvas = Canvas(it)
                canvas.drawColor(Color.WHITE)
                canvas.drawRect(2200f, 40f, 2400f, 300f, Paint().apply { color = secret })
                canvas.drawRect(800f, 400f, 1000f, 600f, Paint().apply { color = secret })
            }
        val file = File(context.cacheDir, "big_screenshot.png")
        file.outputStream().use { big.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val opened = runBlocking { ProofEditing.load(context, Uri.fromFile(file)) }
        assertEquals(1200, opened.width)
        assertEquals(1600, opened.height)

        // The name at the editor's size is x 1100 to 1200, y 20 to 150: the box runs to the edge.
        val box = Rect(1099.6f, 19.4f, 1200f, 150.3f)
        val doc = PictureDoc(1200, 1600, crop = Rect(0.2501f, 0f, 1f, 0.5f))
        val plain = flattenPicture(opened, doc, shadow, ink)
        val plainBox = around(box, doc, plain)
        assertTrue("the name shows without a box", plain.count(plainBox, ::reddish) > 1000)

        val flat = flattenPicture(opened, doc.copy(hides = listOf(box)), shadow, ink)
        val saved = runBlocking { ProofEditing.save(context, flat) }
        val kept = requireNotNull(ProofStore.keep(context, "big", saved))
        val out = requireNotNull(BitmapFactory.decodeFile(kept.path))
        assertEquals(flat.width, out.width)
        assertEquals(flat.height, out.height)
        assertEquals(0, out.count(around(box, doc, out), ::reddish))
        assertTrue("the mark left on show is still there", out.count(::reddish) > 1000)
    }

    /** [box] (the editor's pixels) where it lands on [out], one pixel bigger all round, kept on it. */
    private fun around(
        box: Rect,
        doc: PictureDoc,
        out: Bitmap,
    ): android.graphics.Rect {
        val r = RectF(box.left, box.top, box.right, box.bottom)
        doc.matrix(out.width.toFloat(), out.height.toFloat()).mapRect(r)
        return android.graphics.Rect(
            (floor(r.left).toInt() - 1).coerceAtLeast(0),
            (floor(r.top).toInt() - 1).coerceAtLeast(0),
            (ceil(r.right).toInt() + 1).coerceAtMost(out.width),
            (ceil(r.bottom).toInt() + 1).coerceAtMost(out.height),
        )
    }

    private fun Bitmap.count(
        area: android.graphics.Rect,
        test: (Int) -> Boolean,
    ): Int {
        var found = 0
        for (y in area.top until area.bottom) {
            for (x in area.left until area.right) if (test(getPixel(x, y))) found++
        }
        return found
    }

    /** The JPEG's header segments before the picture data, by their marker's second byte. */
    private fun jpegMarkers(bytes: ByteArray): Set<Int> {
        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
        val found = mutableSetOf<Int>()
        var i = 2
        while (i + 3 < bytes.size && (bytes[i].toInt() and 0xFF) == 0xFF) {
            val marker = bytes[i + 1].toInt() and 0xFF
            found += marker
            if (marker == 0xDA) break
            val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
            i += 2 + length
        }
        return found
    }
}
