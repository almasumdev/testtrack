package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * A picture that zooms where it sits on the page. Two fingers pinch it from 1x to 4x around the
 * fingers, one finger moves it while it is zoomed, and a double tap goes to 2.5x at the tap and back.
 * At 1x a single finger is left alone, so the page around it still scrolls.
 */

private const val MAX_ZOOM = 4f
private const val TAP_ZOOM = 2.5f

/** [painter] filling its box, zoomable in place. [onZoomed] is called the first time it goes past 1x. */
@Composable
internal fun ZoomableImage(
    painter: Painter,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    onZoomed: () -> Unit = {},
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    var anim by remember { mutableStateOf<Job?>(null) }
    val zoomed by rememberUpdatedState(onZoomed)

    fun set(
        s: Float,
        o: Offset,
    ) {
        scale = s
        offset = o
        if (s > 1f) zoomed()
    }

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        val fromS = scale
                        val fromO = offset
                        val toS = if (fromS > 1f) 1f else TAP_ZOOM
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val toO = if (toS > 1f) clampPan((centre - at) * (toS - 1f), toS, size) else Offset.Zero
                        anim?.cancel()
                        anim =
                            scope.launch {
                                animate(0f, 1f) { p, _ ->
                                    set(fromS + (toS - fromS) * p, fromO + (toO - fromO) * p)
                                }
                            }
                    },
                )
            }.pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        // At 1x one finger belongs to the page, so it can scroll past the picture.
                        if (fingers > 1 || scale > 1f) {
                            anim?.cancel()
                            val next = (scale * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val pan = event.calculatePan()
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val at = event.calculateCentroid(useCurrent = false)
                            val from = if (at.isSpecified) at - centre else Offset.Zero
                            // Keep the point under the fingers where it is while zooming, then follow them.
                            val wanted = from - (from - offset) * (next / scale) + pan
                            val kept = clampPan(wanted, next, size)
                            set(next, kept)
                            // One finger pushing up or down past the picture's edge scrolls the page instead.
                            val atEdge = fingers == 1 && abs(wanted.y - kept.y) > 0.5f && abs(pan.y) > abs(pan.x)
                            if (!atEdge) event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        Image(
            painter,
            contentDescription,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.FillBounds,
        )
    }
}

/**
 * [pan] kept so the zoomed picture never leaves a gap at an edge. The picture fills [box] and is
 * grown by [scale] about its centre.
 */
internal fun clampPan(
    pan: Offset,
    scale: Float,
    box: IntSize,
): Offset {
    val maxX = (box.width * (scale - 1f) / 2f).coerceAtLeast(0f)
    val maxY = (box.height * (scale - 1f) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}
