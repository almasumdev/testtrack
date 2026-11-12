package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

/*
 * App icons come in two kinds. A full-square one (Play's usual 512 square, an adaptive icon drawn
 * unmasked) wants our rounded corner. One that is already round or shaped has transparent corners,
 * and clipping it again cuts into its own edge. So we look at the corners and clip only the square.
 *
 * Some shaped icons also sit inside a wide transparent border, so the artwork draws small next to
 * its neighbours. Those are cropped to their visible pixels and drawn to fill the slot.
 */

/** How far in from each edge the corner pixels are read, as a share of the width. */
private const val CORNER_INSET = 0.03f

/** A corner pixel this opaque or more counts as filled. */
private const val FILLED_ALPHA = 200

/** A pixel more opaque than this is part of the artwork; fainter ones are shadow or fringe. */
private const val VISIBLE_ALPHA = 24

/** Margins up to this share of the size are left alone. */
private const val MARGIN_LIMIT = 0.04f

/** Network icons are decoded this big, so a cropped one still has pixels once scaled up. */
private const val DECODE_SIZE = 320

/**
 * True when all four corners are filled, so the icon is a full square and should be clipped.
 * The pixels are read through [alphaAt] (x, y) so this stays testable without a Bitmap.
 */
internal fun isFullSquare(
    width: Int,
    height: Int,
    alphaAt: (Int, Int) -> Int,
): Boolean {
    if (width <= 0 || height <= 0) return true
    val dx = (width * CORNER_INSET).toInt().coerceAtLeast(1).coerceAtMost(width - 1)
    val dy = (height * CORNER_INSET).toInt().coerceAtLeast(1).coerceAtMost(height - 1)
    val xs = intArrayOf(dx, width - 1 - dx)
    val ys = intArrayOf(dy, height - 1 - dy)
    return xs.all { x -> ys.all { y -> alphaAt(x, y) > FILLED_ALPHA } }
}

/** The part of an icon worth drawing, as fractions (0 to 1) of its width and height. */
internal data class CropBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** What we know about one icon: whether it fills its square, and where to crop it (null for none). */
internal data class IconFit(
    val fullSquare: Boolean,
    val crop: CropBox?,
)

/**
 * The square around the visible pixels of an icon, or null when it needs no crop: nothing visible,
 * or no side has more than [MARGIN_LIMIT] of margin. The box takes the larger side of the content,
 * is centred on it and is kept inside the image. The pixels are read through [alphaAt] (x, y).
 */
internal fun cropBox(
    width: Int,
    height: Int,
    alphaAt: (Int, Int) -> Int,
): CropBox? {
    if (width <= 0 || height <= 0) return null
    var minX = width
    var maxX = -1
    var minY = height
    var maxY = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (alphaAt(x, y) > VISIBLE_ALPHA) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return null
    val margin =
        maxOf(
            minX.toFloat() / width,
            (width - 1 - maxX).toFloat() / width,
            minY.toFloat() / height,
            (height - 1 - maxY).toFloat() / height,
        )
    if (margin <= MARGIN_LIMIT) return null
    val limit = minOf(width, height)
    val side = minOf(maxOf(maxX - minX + 1, maxY - minY + 1), limit).toFloat()
    if (side >= limit * (1f - MARGIN_LIMIT)) return null
    val left = ((minX + maxX + 1) / 2f - side / 2f).coerceIn(0f, width - side)
    val top = ((minY + maxY + 1) / 2f - side / 2f).coerceIn(0f, height - side)
    return CropBox(left / width, top / height, (left + side) / width, (top + side) / height)
}

/** A copy of [bitmap] that pixels can be read from; null when a hardware bitmap cannot be copied. */
private fun readableCopy(bitmap: Bitmap): Bitmap? =
    if (bitmap.config == Bitmap.Config.HARDWARE) {
        runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull()
    } else {
        bitmap
    }

/**
 * Works out the corner check and the crop for [bitmap] with one read of its pixels. A full square
 * has nothing to crop. Unreadable pixels count as a square, which is the old behaviour.
 */
internal fun iconFit(bitmap: Bitmap): IconFit {
    val readable = readableCopy(bitmap) ?: return IconFit(true, null)
    return try {
        val w = readable.width
        val h = readable.height
        val pixels = IntArray(w * h)
        readable.getPixels(pixels, 0, w, 0, 0, w, h)
        val alphaAt = { x: Int, y: Int -> AndroidColor.alpha(pixels[y * w + x]) }
        if (isFullSquare(w, h, alphaAt)) IconFit(true, null) else IconFit(false, cropBox(w, h, alphaAt))
    } finally {
        if (readable !== bitmap) readable.recycle()
    }
}

/** [bitmap] cut down to [box]. */
internal fun cropIcon(
    bitmap: Bitmap,
    box: CropBox,
): Bitmap {
    val source = readableCopy(bitmap) ?: return bitmap
    val w = source.width
    val h = source.height
    val x = (box.left * w).roundToInt().coerceIn(0, w - 1)
    val y = (box.top * h).roundToInt().coerceIn(0, h - 1)
    val cw = ((box.right - box.left) * w).roundToInt().coerceIn(1, w - x)
    val ch = ((box.bottom - box.top) * h).roundToInt().coerceIn(1, h - y)
    val cut = Bitmap.createBitmap(source, x, y, cw, ch)
    if (source !== bitmap) source.recycle()
    return cut
}

/** What we already worked out per icon address, so a list does not redo it or flicker on scroll. */
internal object IconShapes {
    private val fits = ConcurrentHashMap<String, IconFit>()

    /** Null until the icon has loaded once. */
    fun fit(model: String): IconFit? = fits[model]

    /** Null until the icon has loaded once. */
    fun known(model: String): Boolean? = fits[model]?.fullSquare

    fun remember(
        model: String,
        fit: IconFit,
    ) {
        fits[model] = fit
    }

    fun remember(
        model: String,
        isSquare: Boolean,
    ) {
        fits[model] = IconFit(isSquare, null)
    }
}

/**
 * Looks at the icon as Coil decodes it: records the fit for [model] and hands back the bitmap
 * cropped to its artwork when it has a wide transparent border. The key is constant because the
 * request's own key already carries the address.
 */
private class IconFitTransformation(
    private val model: String,
) : Transformation {
    override val cacheKey: String = "icon-fit-1"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val fit = iconFit(input)
        IconShapes.remember(model, fit)
        val box = fit.crop ?: return input
        return cropIcon(input, box)
    }
}

/**
 * An icon fetched by address, rounded only when it is a full square, and cropped to its artwork
 * when it has a wide transparent border. Clipped until it is known, and from the first frame when
 * a known icon is shaped already. The loading and error tiles are the caller's and are always
 * drawn clipped by themselves.
 */
@Composable
internal fun ShapedIconImage(
    model: String,
    size: Dp,
    loading: @Composable () -> Unit,
    error: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var square by remember(model) { mutableStateOf(IconShapes.known(model) ?: true) }
    // Hardware bitmaps cannot be read, so this request opts out of them.
    val request =
        remember(model) {
            ImageRequest
                .Builder(context)
                .data(model)
                .allowHardware(false)
                .size(DECODE_SIZE)
                .transformations(IconFitTransformation(model))
                .build()
        }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = if (square) Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)) else Modifier.size(size),
        loading = { loading() },
        error = { error() },
        onSuccess = { state ->
            // The transformation records the fit while decoding. If it did not run, read the bitmap.
            val known = IconShapes.known(model)
            if (known != null) {
                square = known
            } else {
                runCatching { state.result.drawable.toBitmap() }.getOrNull()?.let { bitmap ->
                    val fit = iconFit(bitmap)
                    IconShapes.remember(model, fit)
                    square = fit.fullSquare
                }
            }
        },
    )
}
