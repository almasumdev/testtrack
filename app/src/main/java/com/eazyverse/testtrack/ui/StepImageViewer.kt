package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.composables.icons.lucide.ZoomIn
import com.eazyverse.testtrack.ui.theme.ViewerBlack
import com.eazyverse.testtrack.ui.theme.ViewerInk

/*
 * The Play Console pictures large, on black: swipe between the steps, pinch or double-tap to zoom,
 * and drag while zoomed. A page is back to its whole picture when another is swiped to.
 */

private const val MAX_ZOOM = 4f
private const val TAP_ZOOM = 2.5f

private val CountStyle = tileStyle(15f, 20f, FontWeight.SemiBold)
private val CaptionStyle = tileStyle(15f, 20f, FontWeight.SemiBold)
private val HintStyle = tileStyle(12.5f, 16f, FontWeight.SemiBold)

/** The viewer, opened at step [start]. [showHint] puts the pinch hint up until the first zoom. */
@Composable
internal fun StepImageViewer(
    start: Int,
    showHint: Boolean,
    onDismiss: () -> Unit,
) {
    val pager = rememberPagerState(initialPage = start) { PlaySteps.size }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var hint by remember { mutableStateOf(showHint) }
    LaunchedEffect(pager.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }
    LaunchedEffect(scale > 1f) { if (scale > 1f) hint = false }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(ViewerBlack)) {
            HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = scale == 1f) { page ->
                val current = page == pager.currentPage
                ZoomablePicture(
                    PlaySteps[page],
                    scale = if (current) scale else 1f,
                    offset = if (current) offset else Offset.Zero,
                ) { s, o ->
                    if (current) {
                        scale = s
                        offset = o
                    }
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pager.currentPage + 1} / ${PlaySteps.size}",
                    Modifier.weight(1f),
                    style = CountStyle,
                    color = ViewerInk.copy(alpha = 0.85f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Lucide.X, "Close", Modifier.size(24.dp), tint = ViewerInk)
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (hint) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(ViewerInk.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Lucide.ZoomIn, null, Modifier.size(14.dp), tint = ViewerInk.copy(alpha = 0.9f))
                        Spacer(Modifier.width(6.dp))
                        Text("Pinch to zoom, drag to move", style = HintStyle, color = ViewerInk.copy(alpha = 0.9f))
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(PlaySteps.size) { index ->
                        val on = index == pager.currentPage
                        Box(
                            Modifier
                                .size(width = if (on) 16.dp else 6.dp, height = 6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (on) ViewerInk else ViewerInk.copy(alpha = 0.35f)),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${pager.currentPage + 1}. ${PlaySteps[pager.currentPage].text}",
                    Modifier.fillMaxWidth(),
                    style = CaptionStyle,
                    color = ViewerInk,
                )
            }
        }
    }
}

/**
 * One picture fitted to the screen and zoomed by [scale] about its centre, moved by [offset]. A
 * single finger only moves it while it is zoomed, so at 1x a swipe goes to the pager.
 */
@Composable
private fun ZoomablePicture(
    step: PlayStep,
    scale: Float,
    offset: Offset,
    onChange: (Float, Offset) -> Unit,
) {
    val painter = painterResource(step.picture)
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.isSpecified && intrinsic.height > 0f) intrinsic.width / intrinsic.height else 16f / 10f
    val nowScale by rememberUpdatedState(scale)
    val nowOffset by rememberUpdatedState(offset)
    val change by rememberUpdatedState(onChange)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(ratio) {
                detectTapGestures(
                    onDoubleTap = { at ->
                        if (nowScale > 1f) {
                            change(1f, Offset.Zero)
                        } else {
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            change(TAP_ZOOM, clampPan((centre - at) * (TAP_ZOOM - 1f), TAP_ZOOM, size, ratio))
                        }
                    },
                )
            }.pointerInput(ratio) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var s = nowScale
                    var o = nowOffset
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (fingers > 1 || s > 1f) {
                            val next = (s * event.calculateZoom()).coerceIn(1f, MAX_ZOOM)
                            val z = next / s
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val at = event.calculateCentroid(useCurrent = false)
                            val from = if (at.isSpecified) at - centre else Offset.Zero
                            // Keep the point under the fingers where it is while zooming, then follow them.
                            o = clampPan(from + event.calculatePan() - (from - o) * z, next, size, ratio)
                            s = next
                            change(s, o)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter,
            step.text,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * [pan] kept so the zoomed picture never leaves a gap at an edge: the picture is fitted to [box] at
 * [ratio], then grown by [scale] about the centre.
 */
internal fun clampPan(
    pan: Offset,
    scale: Float,
    box: IntSize,
    ratio: Float,
): Offset {
    val w = box.width.toFloat()
    val h = box.height.toFloat()
    if (w <= 0f || h <= 0f || ratio <= 0f) return Offset.Zero
    val fitW = if (w / h > ratio) h * ratio else w
    val fitH = if (w / h > ratio) h else w / ratio
    val maxX = ((fitW * scale - w) / 2f).coerceAtLeast(0f)
    val maxY = ((fitH * scale - h) / 2f).coerceAtLeast(0f)
    return Offset(pan.x.coerceIn(-maxX, maxX), pan.y.coerceIn(-maxY, maxY))
}
