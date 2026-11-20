package com.eazyverse.testtrack.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import kotlin.math.max

/** Bound reads/decodes, apply EXIF orientation, and export pixels without source metadata. */
object ProofImages {
    /**
     * [uri] as a bitmap no longer than 1600 on its long side, turned upright.
     *
     * The picture editor opens its picture through here, so the shrink happens before anybody sees
     * it or draws a box. The order after that, kept on purpose: boxes are burned into this bitmap's
     * own pixels, whole pixels rounded outwards (burnHides), before any turn, crop or scaling, and
     * nothing later reads the pixels from before the burn. A turn or crop that resamples, the JPEG,
     * and ProofStore's copy (the same size again, so not scaled) can only mix a box's ink with
     * pixels that were outside it and already on show; what was under a box is in none of them.
     */
    fun decode(
        context: Context,
        uri: Uri,
    ): Bitmap {
        val bytes =
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(16384)
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    require(output.size() + read <= 16 * 1024 * 1024) { "Choose an image under 16 MB." }
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            }
                ?: error("Image unavailable")
        require(bytes.size <= 16 * 1024 * 1024) { "Choose an image under 16 MB." }
        if (Build.VERSION.SDK_INT < 28) return legacy(bytes)
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            require(width.toLong() * height <= 40_000_000) { "Choose a smaller image." }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val scale = (1600.0 / max(width, height)).coerceAtMost(1.0)
            decoder.setTargetSize(max(1, (width * scale).toInt()), max(1, (height * scale).toInt()))
        }
    }

    private fun legacy(bytes: ByteArray): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        require(options.outWidth > 0 && options.outHeight > 0)
        require(options.outWidth.toLong() * options.outHeight <= 40_000_000)
        options.inJustDecodeBounds = false
        options.inSampleSize = 1
        while (max(options.outWidth, options.outHeight) / options.inSampleSize > 1600) options.inSampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: error("Image unavailable")
        val orientation =
            runCatching {
                ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            2 -> matrix.setScale(-1f, 1f)
            3 -> matrix.setRotate(180f)
            4 -> matrix.setScale(1f, -1f)
            5 -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            6 -> matrix.setRotate(90f)
            7 -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            8 -> matrix.setRotate(270f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    fun jpeg(bitmap: Bitmap): ByteArray {
        val output = ByteArrayOutputStream()
        for (quality in listOf(90, 80, 65, 45)) {
            output.reset()
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
            if (output.size() <= 2 * 1024 * 1024) return output.toByteArray()
        }
        error("Choose a smaller image.")
    }

    fun bytes(
        context: Context,
        uri: Uri,
    ): ByteArray {
        val bitmap = decode(context, uri)
        return try {
            jpeg(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    /** The widest a night's picture of an app is sent: what the server keeps, so nothing more is sent. */
    const val VISIT_WIDTH = 720
    private const val VISIT_QUALITY = 70

    /** The size a night's picture is sent at: no wider than [VISIT_WIDTH], the same shape, never enlarged. */
    fun visitSize(
        width: Int,
        height: Int,
    ): Pair<Int, Int> =
        if (width <= VISIT_WIDTH) {
            width to height
        } else {
            VISIT_WIDTH to max(1, (height.toLong() * VISIT_WIDTH / width).toInt())
        }

    /** A night's picture of an app as it is sent: shrunk to [visitSize], WebP. [bitmap] is left alone. */
    fun visitWebp(
        bitmap: Bitmap,
        out: OutputStream,
    ) {
        val (width, height) = visitSize(bitmap.width, bitmap.height)
        val sized = if (width == bitmap.width) bitmap else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val format =
            if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
        try {
            check(sized.compress(format, VISIT_QUALITY, out))
        } finally {
            if (sized !== bitmap) sized.recycle()
        }
    }

    /** A picture picked from the phone for a night's visit, as [visitWebp] sends it. */
    fun visit(
        context: Context,
        uri: Uri,
    ): ByteArray {
        val bitmap = decode(context, uri)
        return try {
            ByteArrayOutputStream().also { visitWebp(bitmap, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
