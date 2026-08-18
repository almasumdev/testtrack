package com.eazyverse.testtrack.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * A profile picture small enough to live inside a Firestore document.
 *
 * There is no file storage in this project and adding one for an avatar would be the largest piece
 * of infrastructure in the app, so the picture is base64 on `users/{uid}` beside the name it
 * belongs to. That is a real constraint rather than a shortcut: the document it sits on is read
 * thirty at a time whenever a cohort turns uids into names, so every byte here is paid for thirty
 * times by somebody on mobile data.
 *
 * Hence [BUDGET]. The picture is squeezed until it fits, not compressed once and hoped about, and
 * the last resort is a smaller picture rather than a bigger document. A face at 96 pixels is still
 * a face at the sizes it is ever drawn at.
 */
object Photo {

    /**
     * The widest a stored avatar is allowed to be, in base64 characters.
     *
     * Six kilobytes, which is roughly what a 96 pixel face costs as a middling JPEG. Thirty of
     * them is under 200 KB for a whole cohort, which is the number that actually matters — a
     * document limit of one megabyte would let this be a hundred times larger and the grid would
     * be unusable long before Firestore complained.
     */
    const val BUDGET = 6_000

    /** Rendered at 40dp at the largest, so this is already generous on a three times screen. */
    private const val SIZE = 96
    private const val FLOOR = 72

    private val QUALITIES = intArrayOf(70, 60, 50, 40, 30)

    /**
     * Reads the picture somebody chose and returns it as base64, or null if it cannot be read.
     *
     * Two passes over the stream, because the first one only asks how big the image is. Decoding a
     * twelve megapixel photograph in full to throw away all but ninety six pixels of it is how an
     * avatar picker runs a phone out of memory, so `inSampleSize` gets it down to roughly the right
     * order of magnitude before a single pixel is allocated.
     */
    fun encode(context: Context, uri: Uri): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // The stream is what is checked for null, not the decode. With `inJustDecodeBounds` the
        // decode returns null by design and writes its answer into `bounds`, so treating that null
        // as a failure rejects every picture ever chosen.
        val header = context.contentResolver.openInputStream(uri) ?: return null
        header.use { BitmapFactory.decodeStream(it, null, bounds) }

        val smallest = min(bounds.outWidth, bounds.outHeight)
        if (smallest <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = max(1, smallest / SIZE)
        }
        val source = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        // Squared before it is scaled, so a portrait photograph becomes a face rather than a face
        // with two thirds of a room under it.
        val square = crop(source)
        if (square !== source) source.recycle()

        var edge = SIZE
        while (true) {
            val scaled = Bitmap.createScaledBitmap(square, edge, edge, true)
            val fitted = squeeze(scaled)
            if (scaled !== square) scaled.recycle()
            if (fitted != null || edge <= FLOOR) {
                square.recycle()
                return fitted
            }
            edge = FLOOR
        }
    }

    /** The largest centred square the picture contains. */
    private fun crop(source: Bitmap): Bitmap {
        val edge = min(source.width, source.height)
        if (source.width == source.height) return source
        return Bitmap.createBitmap(
            source, (source.width - edge) / 2, (source.height - edge) / 2, edge, edge
        )
    }

    /** JPEG at descending quality until it fits [BUDGET], or null if even the worst does not. */
    private fun squeeze(bitmap: Bitmap): String? {
        for (quality in QUALITIES) {
            val bytes = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes)
            val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= BUDGET) return encoded
        }
        return null
    }

    /**
     * Decoded faces, kept for the life of the process.
     *
     * A profile picture is the same bytes every time it is drawn, and the same bytes for every row
     * showing the same person. Without this, scrolling a cohort ran a JPEG decode per row per
     * frame — `remember` is per composable instance, so it survives recomposition and nothing
     * else, and a list that recycles its rows defeats it exactly when it matters.
     *
     * Sized in bytes rather than entries, because the entries are bitmaps and a count would be
     * a budget in a unit nobody can convert. Two megabytes is around fifty faces at [SIZE], which
     * is three cohorts' worth and more than any screen has ever asked for at once.
     */
    private val decoded = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /**
     * Back to a bitmap for drawing, or null for anything that is not a picture.
     *
     * Null rather than an exception on purpose. This decodes a string that arrived over the
     * network, and the caller's fallback is the tester's initial — which is the right answer for a
     * truncated field, a bad write, and an account that never set one, without telling the three
     * of them apart.
     *
     * A failure is not cached. It costs one decode of a few kilobytes to find out again, and
     * remembering it would mean a field repaired on the server stays broken on the phone until the
     * app is killed.
     */
    fun decode(encoded: String): Bitmap? {
        if (encoded.isBlank()) return null
        decoded[encoded]?.let { return it }
        val bitmap = runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull() ?: return null
        decoded.put(encoded, bitmap)
        return bitmap
    }

    /** Signing out must not leave the next account looking at the last one's face. */
    fun clear() = decoded.evictAll()
}
