package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.abs

/**
 * The picture being edited, filling its box. Showing, it is the crop and words can be moved,
 * pinched and tapped; drawing, a finger draws; cropping, the whole picture shows with the crop's
 * frame over it and its corners and sides can be dragged; hiding, a finger drags a box over
 * details and two fingers zoom in to reach small ones.
 */
@Composable
internal fun PictureCanvas(
    bitmap: Bitmap,
    doc: PictureDoc,
    mode: EditMode,
    pen: Color,
    /** The pen's width on the screen, in pixels. */
    penWidth: Float,
    shape: CropShape,
    /** Words being typed over, left out of the picture until they are done. */
    typing: Long?,
    onStroke: (PenStroke) -> Unit,
    onText: (PictureText) -> Unit,
    onTextTap: (PictureText) -> Unit,
    onCrop: (Rect) -> Unit,
    /** A box over details, in the picture's own pixels. */
    onHide: (Rect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val shadow = t.photoScrim.toArgb()
    val hideInk = t.photo.toArgb()
    var live by remember { mutableStateOf<PenStroke?>(null) }
    var boxing by remember { mutableStateOf<Rect?>(null) }
    // Hiding only: how far in the picture is zoomed and moved, so a small detail can be reached.
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(mode) {
        if (mode != EditMode.HIDE) {
            zoom = 1f
            pan = Offset.Zero
        }
    }
    var moving by remember { mutableStateOf<PictureText?>(null) }
    var cropping by remember { mutableStateOf<Rect?>(null) }
    var box by remember { mutableStateOf(Size.Zero) }
    val whole = mode == EditMode.CROP
    val view =
        doc.copy(
            crop = cropping ?: doc.crop,
            texts = moving?.let { m -> doc.texts.map { if (it.id == m.id) m else it } } ?: doc.texts,
        )
    val now by rememberUpdatedState(
        Tools(doc, mode, pen, penWidth, shape, onStroke, onText, onTextTap, onCrop, onHide),
    )
    val density = LocalDensity.current
    val inset = with(density) { 28.dp.toPx() }
    val grab = with(density) { 30.dp.toPx() }
    val smallest = with(density) { 6.dp.toPx() }

    Canvas(
        modifier
            .fillMaxSize()
            .onSizeChanged { box = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val tools = now
                    val pad = if (tools.mode == EditMode.CROP) inset else 0f
                    val m = tools.doc.matrix(box.width, box.height, tools.mode == EditMode.CROP, pad)
                    if (tools.mode == EditMode.HIDE) m.postConcat(lens(zoom, pan, box))
                    val inv = Matrix().also { m.invert(it) }
                    when (tools.mode) {
                        EditMode.DRAW -> {
                            val width = tools.penWidth / m.mapRadius(1f).coerceAtLeast(0.0001f)
                            val points = mutableListOf(inv.point(down.position))
                            live = PenStroke(points.toList(), tools.pen, width)
                            down.consume()
                            follow(down) {
                                points += inv.point(it.position)
                                live = PenStroke(points.toList(), tools.pen, width)
                            }
                            live?.let(tools.onStroke)
                            live = null
                        }
                        EditMode.CROP -> {
                            val frame = tools.doc.shownRect(box.width, box.height, true, pad)
                            val start = tools.doc.crop
                            val grip = gripAt(down.position, onScreen(start, frame), grab, tools.shape == CropShape.FREE)
                            if (grip != null) {
                                down.consume()
                                val ratio = tools.doc.fractionRatio(tools.shape)
                                follow(down) {
                                    val dx = (it.position.x - down.position.x) / frame.width
                                    val dy = (it.position.y - down.position.y) / frame.height
                                    cropping = dragCrop(start, grip, dx, dy, ratio)
                                }
                                cropping?.let(tools.onCrop)
                                cropping = null
                            }
                        }
                        EditMode.HIDE -> {
                            down.consume()
                            val start = inv.point(down.position)
                            var pinched = false
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } > 1) pinched = true
                                if (pinched) {
                                    // Two fingers zoom and move the picture; the box being dragged is dropped.
                                    boxing = null
                                    zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                                    pan = keptInView(pan + event.calculatePan(), zoom, box)
                                } else {
                                    val finger = event.changes.firstOrNull { it.id == down.id }
                                    if (finger != null && finger.pressed) boxing = tools.doc.hideBox(start, inv.point(finger.position))
                                }
                                event.changes.forEach { it.consume() }
                            } while (event.changes.any { it.pressed })
                            val drawn = boxing
                            boxing = null
                            val scale = m.mapRadius(1f)
                            if (!pinched && drawn != null && drawn.width * scale >= smallest && drawn.height * scale >= smallest) {
                                tools.onHide(drawn)
                            }
                        }
                        EditMode.NONE -> {
                            val at = inv.point(down.position)
                            val slop = 16.dp.toPx() / m.mapRadius(1f).coerceAtLeast(0.0001f)
                            val hit = tools.doc.texts.lastOrNull { wordsHit(it, at, slop) }
                            if (hit != null) {
                                down.consume()
                                var item: PictureText = hit
                                var travel = 0f
                                var pinched = false
                                do {
                                    val event = awaitPointerEvent()
                                    val pan = event.calculatePan()
                                    val zoom = event.calculateZoom()
                                    val turn = event.calculateRotation()
                                    if (event.changes.count { it.pressed } > 1) pinched = true
                                    travel += abs(pan.x) + abs(pan.y)
                                    val shift = floatArrayOf(pan.x, pan.y).also { inv.mapVectors(it) }
                                    item =
                                        item.copy(
                                            center = item.center + Offset(shift[0], shift[1]),
                                            size = (item.size * zoom).coerceIn(8f, 2000f),
                                            angle = item.angle + turn,
                                        )
                                    moving = item
                                    event.changes.forEach { it.consume() }
                                } while (event.changes.any { it.pressed })
                                moving = null
                                if (!pinched && travel < viewConfiguration.touchSlop) tools.onTextTap(hit) else tools.onText(item)
                            }
                        }
                    }
                }
            },
    ) {
        val m = view.matrix(size.width, size.height, whole, if (whole) inset else 0f)
        var shown = view.shownRect(size.width, size.height, whole, if (whole) inset else 0f)
        if (mode == EditMode.HIDE) {
            val looking = lens(zoom, pan, size)
            m.postConcat(looking)
            val r = RectF(shown.left, shown.top, shown.right, shown.bottom).also { looking.mapRect(it) }
            shown = Rect(r.left, r.top, r.right, r.bottom).intersect(Rect(Offset.Zero, size))
        }
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.save()
            if (!whole) native.clipRect(shown.left, shown.top, shown.right, shown.bottom)
            drawPicture(native, bitmap, view, m, shadow, hideInk, live, typing, boxing)
            native.restore()
        }
        if (whole) cropFrame(onScreen(view.crop, shown), t.photoScrim, t.onPhoto)
    }
}

/** What the gesture reads, kept fresh between drags. */
private class Tools(
    val doc: PictureDoc,
    val mode: EditMode,
    val pen: Color,
    val penWidth: Float,
    val shape: CropShape,
    val onStroke: (PenStroke) -> Unit,
    val onText: (PictureText) -> Unit,
    val onTextTap: (PictureText) -> Unit,
    val onCrop: (Rect) -> Unit,
    val onHide: (Rect) -> Unit,
)

/** The zoom about the box's middle, then the move: what hiding lays over the fitted picture. */
private fun lens(
    zoom: Float,
    pan: Offset,
    box: Size,
) = Matrix().apply {
    setScale(zoom, zoom, box.width / 2f, box.height / 2f)
    postTranslate(pan.x, pan.y)
}

/** A move kept so the zoomed picture still fills the box: none at all when not zoomed. */
private fun keptInView(
    pan: Offset,
    zoom: Float,
    box: Size,
): Offset {
    val x = box.width * (zoom - 1f) / 2f
    val y = box.height * (zoom - 1f) / 2f
    return Offset(pan.x.coerceIn(-x, x), pan.y.coerceIn(-y, y))
}

private fun Matrix.point(at: Offset): Offset {
    val p = floatArrayOf(at.x, at.y)
    mapPoints(p)
    return Offset(p[0], p[1])
}

/** Every move of the finger that went down, until it lifts. */
private suspend fun AwaitPointerEventScope.follow(
    down: PointerInputChange,
    onMove: (PointerInputChange) -> Unit,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: break
        if (!change.pressed) break
        if (change.positionChanged()) onMove(change)
        change.consume()
    }
}

/** A crop held as fractions, on the screen inside [frame]. */
private fun onScreen(
    crop: Rect,
    frame: Rect,
) = Rect(
    frame.left + crop.left * frame.width,
    frame.top + crop.top * frame.height,
    frame.left + crop.right * frame.width,
    frame.top + crop.bottom * frame.height,
)

/** The part of the crop under [at]: a corner, a side when [sides], the middle to move it, or nothing. */
private fun gripAt(
    at: Offset,
    crop: Rect,
    grab: Float,
    sides: Boolean,
): CropGrip? {
    val nearL = abs(at.x - crop.left) <= grab
    val nearR = abs(at.x - crop.right) <= grab
    val nearT = abs(at.y - crop.top) <= grab
    val nearB = abs(at.y - crop.bottom) <= grab
    val inX = at.x in crop.left - grab..crop.right + grab
    val inY = at.y in crop.top - grab..crop.bottom + grab
    return when {
        nearT && nearL -> CropGrip.TOP_LEFT
        nearT && nearR -> CropGrip.TOP_RIGHT
        nearB && nearL -> CropGrip.BOTTOM_LEFT
        nearB && nearR -> CropGrip.BOTTOM_RIGHT
        sides && nearL && inY -> CropGrip.LEFT
        sides && nearR && inY -> CropGrip.RIGHT
        sides && nearT && inX -> CropGrip.TOP
        sides && nearB && inX -> CropGrip.BOTTOM
        crop.contains(at) -> CropGrip.MOVE
        else -> null
    }
}

/** The crop's frame: the rest shaded, a thin edge, thirds, and a heavy mark at each corner. */
private fun DrawScope.cropFrame(
    crop: Rect,
    shade: Color,
    ink: Color,
) {
    drawRect(shade, Offset.Zero, Size(size.width, crop.top))
    drawRect(shade, Offset(0f, crop.bottom), Size(size.width, size.height - crop.bottom))
    drawRect(shade, Offset(0f, crop.top), Size(crop.left, crop.height))
    drawRect(shade, Offset(crop.right, crop.top), Size(size.width - crop.right, crop.height))
    val thin = 1.dp.toPx()
    for (i in 1..2) {
        val x = crop.left + crop.width * i / 3f
        val y = crop.top + crop.height * i / 3f
        drawLine(ink.copy(alpha = 0.45f), Offset(x, crop.top), Offset(x, crop.bottom), thin)
        drawLine(ink.copy(alpha = 0.45f), Offset(crop.left, y), Offset(crop.right, y), thin)
    }
    drawRect(ink, crop.topLeft, crop.size, style = Stroke(1.5.dp.toPx()))
    val heavy = 4.dp.toPx()
    val arm = minOf(22.dp.toPx(), crop.width / 3f, crop.height / 3f)
    val h = heavy / 2f
    listOf(
        Triple(Offset(crop.left - h, crop.top - h), 1f, 1f),
        Triple(Offset(crop.right + h, crop.top - h), -1f, 1f),
        Triple(Offset(crop.left - h, crop.bottom + h), 1f, -1f),
        Triple(Offset(crop.right + h, crop.bottom + h), -1f, -1f),
    ).forEach { (corner, sx, sy) ->
        drawLine(ink, corner, corner + Offset(arm * sx, 0f), heavy)
        drawLine(ink, corner, corner + Offset(0f, arm * sy), heavy)
    }
}
