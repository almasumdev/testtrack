package com.eazyverse.testtrack.ui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.sqrt

/** Normalized outer edge, computed on image loading rather than on each frame. */
internal data class IconContour(
    val points: List<Offset>,
)

/** Only a complete circular perimeter gets a circular frame; irregular logos get a clean square. */
internal fun IconContour.isRoundIcon(): Boolean =
    points.size >= 8 &&
        points.all {
            val x = it.x - 0.5f
            val y = it.y - 0.5f
            abs(sqrt(x * x + y * y) - 0.5f) <= 0.06f
        }

internal fun iconContour(
    width: Int,
    height: Int,
    alphaAt: (Int, Int) -> Int,
): IconContour? {
    if (width <= 0 || height <= 0) return null
    val left = ArrayList<Offset>()
    val right = ArrayList<Offset>()
    for (y in 0 until height) {
        var first = 0
        while (first < width && alphaAt(first, y) <= 128) first++
        if (first == width) continue
        var last = width - 1
        while (last > first && alphaAt(last, y) <= 128) last--
        left.add(Offset(first.toFloat() / width, y.toFloat() / height))
        left.add(Offset(first.toFloat() / width, (y + 1f) / height))
        right.add(Offset((last + 1f) / width, y.toFloat() / height))
        right.add(Offset((last + 1f) / width, (y + 1f) / height))
    }
    return if (left.isEmpty()) null else IconContour(left + right.asReversed())
}
