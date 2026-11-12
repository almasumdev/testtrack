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
import java.util.concurrent.ConcurrentHashMap
import android.graphics.Color as AndroidColor

/*
 * App icons come in two kinds. A full-square one (Play's usual 512 square, an adaptive icon drawn
 * unmasked) wants our rounded corner. One that is already round or shaped has transparent corners,
 * and clipping it again cuts into its own edge. So we look at the corners and clip only the square.
 */

/** How far in from each edge the corner pixels are read, as a share of the width. */
private const val CORNER_INSET = 0.03f

/** A corner pixel this opaque or more counts as filled. */
private const val FILLED_ALPHA = 200

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

/**
 * Reads the corners of [bitmap]. A hardware bitmap has no readable pixels, so a copy is made first;
 * if even that fails the icon is treated as a square, which is the old behaviour.
 */
internal fun isFullSquare(bitmap: Bitmap): Boolean {
    val readable =
        if (bitmap.config == Bitmap.Config.HARDWARE) {
            runCatching { bitmap.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return true
        } else {
            bitmap
        }
    return try {
        isFullSquare(readable.width, readable.height) { x, y -> AndroidColor.alpha(readable.getPixel(x, y)) }
    } finally {
        if (readable !== bitmap) readable.recycle()
    }
}

/** What we already worked out per icon address, so a list does not redo it or flicker on scroll. */
internal object IconShapes {
    private val fullSquare = ConcurrentHashMap<String, Boolean>()

    /** Null until the icon has loaded once. */
    fun known(model: String): Boolean? = fullSquare[model]

    fun remember(
        model: String,
        isSquare: Boolean,
    ) {
        fullSquare[model] = isSquare
    }
}

/**
 * An icon fetched by address, rounded only when it is a full square. Clipped until it is known,
 * and from the first frame when a known icon is shaped already. The loading and error tiles are
 * the caller's and are always drawn clipped by themselves.
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
                .build()
        }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        modifier = if (square) Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)) else Modifier.size(size),
        loading = { loading() },
        error = { error() },
        onSuccess = { state ->
            val bitmap = runCatching { state.result.drawable.toBitmap() }.getOrNull()
            if (bitmap != null) {
                val found = isFullSquare(bitmap)
                IconShapes.remember(model, found)
                square = found
            }
        },
    )
}
