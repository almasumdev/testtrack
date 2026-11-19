package com.eazyverse.testtrack.ui

import android.graphics.Matrix
import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min

/*
 * What the picture editor has done to a picture, as plain values: quarter turns, a crop, pen
 * lines and words. Lines and words are kept in the picture's own pixels, so a turn or a crop
 * after them never moves them on the picture. The crop is kept as fractions of the turned
 * picture, so it stays square to the screen.
 */

/** A line drawn with the pen, in the picture's own pixels. */
internal data class PenStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
)

/** Words put on the picture: their middle, size and slant, in the picture's own pixels and degrees. */
internal data class PictureText(
    val id: Long,
    val text: String,
    val color: Color,
    val center: Offset,
    val size: Float,
    val angle: Float,
)

/** What the editor can be doing besides showing the picture. */
internal enum class EditMode { NONE, CROP, DRAW }

/** A crop's shape: free, or a width over height held while a corner is dragged. */
internal enum class CropShape(
    val label: String,
    val ratio: Float?,
) {
    FREE("Free", null),
    SQUARE("1:1", 1f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    WIDE("16:9", 16f / 9f),
}

internal data class PictureDoc(
    val width: Int,
    val height: Int,
    /** Quarter turns clockwise, 0 to 3. */
    val turns: Int = 0,
    /** The part kept, as fractions of the turned picture. */
    val crop: Rect = WHOLE,
    val strokes: List<PenStroke> = emptyList(),
    val texts: List<PictureText> = emptyList(),
) {
    val turnedWidth: Float get() = (if (turns % 2 == 1) height else width).toFloat()
    val turnedHeight: Float get() = (if (turns % 2 == 1) width else height).toFloat()

    /** The kept part's size in pixels. */
    val cropWidth: Float get() = crop.width * turnedWidth
    val cropHeight: Float get() = crop.height * turnedHeight

    /** A quarter turn clockwise; the crop turns with the picture. */
    fun turned(): PictureDoc =
        copy(
            turns = (turns + 1) % 4,
            crop = Rect(1f - crop.bottom, crop.left, 1f - crop.top, crop.right),
        )

    /** The picture's own pixels to the turned picture's. */
    private fun turnMatrix(): Matrix {
        val m = Matrix()
        m.setRotate(90f * turns)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        m.mapRect(bounds)
        m.postTranslate(-bounds.left, -bounds.top)
        return m
    }

    /** The part shown, in the turned picture's pixels: the crop, or all of it while cropping. */
    private fun region(whole: Boolean): RectF =
        if (whole) {
            RectF(0f, 0f, turnedWidth, turnedHeight)
        } else {
            RectF(crop.left * turnedWidth, crop.top * turnedHeight, crop.right * turnedWidth, crop.bottom * turnedHeight)
        }

    /**
     * The picture's own pixels to a box [boxWidth] by [boxHeight]: the shown part fitted inside it,
     * [inset] in from each edge, and centred.
     */
    fun matrix(
        boxWidth: Float,
        boxHeight: Float,
        whole: Boolean = false,
        inset: Float = 0f,
    ): Matrix {
        val part = region(whole)
        val roomW = max(1f, boxWidth - 2 * inset)
        val roomH = max(1f, boxHeight - 2 * inset)
        val scale = min(roomW / max(1f, part.width()), roomH / max(1f, part.height()))
        val m = turnMatrix()
        m.postTranslate(-part.left, -part.top)
        m.postScale(scale, scale)
        m.postTranslate((boxWidth - part.width() * scale) / 2f, (boxHeight - part.height() * scale) / 2f)
        return m
    }

    /** Where the shown part lands in the box, by [matrix]'s rules. */
    fun shownRect(
        boxWidth: Float,
        boxHeight: Float,
        whole: Boolean = false,
        inset: Float = 0f,
    ): Rect {
        val part = region(whole)
        val roomW = max(1f, boxWidth - 2 * inset)
        val roomH = max(1f, boxHeight - 2 * inset)
        val scale = min(roomW / max(1f, part.width()), roomH / max(1f, part.height()))
        val left = (boxWidth - part.width() * scale) / 2f
        val top = (boxHeight - part.height() * scale) / 2f
        return Rect(left, top, left + part.width() * scale, top + part.height() * scale)
    }

    /** The middle of the crop, in the picture's own pixels: where new words go. */
    fun cropCenter(): Offset {
        val back = Matrix().also { turnMatrix().invert(it) }
        val p = floatArrayOf(crop.center.x * turnedWidth, crop.center.y * turnedHeight)
        back.mapPoints(p)
        return Offset(p[0], p[1])
    }

    /** New words: a ninth of the crop's short side tall, and upright on the screen however it is turned. */
    fun newWords(
        id: Long,
        text: String,
        color: Color,
    ) = PictureText(id, text, color, cropCenter(), min(cropWidth, cropHeight) / 9f, -90f * turns)

    /** The biggest crop of [shape] that fits, centred where the crop is now. Free keeps the crop. */
    fun cropFor(shape: CropShape): Rect {
        val ratio = shape.ratio ?: return crop
        // As fractions: width over height in pixels is ratio, so the fractions differ by the picture's own shape.
        val fracRatio = ratio * turnedHeight / turnedWidth
        var w = 1f
        var h = w / fracRatio
        if (h > 1f) {
            h = 1f
            w = h * fracRatio
        }
        val cx = crop.center.x.coerceIn(w / 2f, 1f - w / 2f)
        val cy = crop.center.y.coerceIn(h / 2f, 1f - h / 2f)
        return Rect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }

    companion object {
        val WHOLE = Rect(0f, 0f, 1f, 1f)

        /** The smallest crop side, as a fraction. */
        const val MIN_CROP = 0.1f
    }
}

/** Which part of a crop a finger took hold of. */
internal enum class CropGrip { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/**
 * The crop after a drag of [dx], [dy] (fractions) on [grip], from [start]. A held [ratio] (width
 * over height, as fractions) keeps a corner's drag to that shape; the sides are free only.
 */
internal fun dragCrop(
    start: Rect,
    grip: CropGrip,
    dx: Float,
    dy: Float,
    ratio: Float?,
): Rect {
    val min = PictureDoc.MIN_CROP
    if (grip == CropGrip.MOVE) {
        val x = dx.coerceIn(-start.left, 1f - start.right)
        val y = dy.coerceIn(-start.top, 1f - start.bottom)
        return start.translate(x, y)
    }
    var l = start.left
    var t = start.top
    var r = start.right
    var b = start.bottom
    when (grip) {
        CropGrip.LEFT, CropGrip.TOP_LEFT, CropGrip.BOTTOM_LEFT -> l = (l + dx).coerceIn(0f, r - min)
        CropGrip.RIGHT, CropGrip.TOP_RIGHT, CropGrip.BOTTOM_RIGHT -> r = (r + dx).coerceIn(l + min, 1f)
        else -> Unit
    }
    when (grip) {
        CropGrip.TOP, CropGrip.TOP_LEFT, CropGrip.TOP_RIGHT -> t = (t + dy).coerceIn(0f, b - min)
        CropGrip.BOTTOM, CropGrip.BOTTOM_LEFT, CropGrip.BOTTOM_RIGHT -> b = (b + dy).coerceIn(t + min, 1f)
        else -> Unit
    }
    if (ratio == null) return Rect(l, t, r, b)
    // A held shape: the height follows the width, from the corner opposite the one held.
    val fromLeft = grip == CropGrip.TOP_LEFT || grip == CropGrip.BOTTOM_LEFT
    val fromTop = grip == CropGrip.TOP_LEFT || grip == CropGrip.TOP_RIGHT
    val anchorX = if (fromLeft) start.right else start.left
    val anchorY = if (fromTop) start.bottom else start.top
    val roomX = if (fromLeft) anchorX else 1f - anchorX
    val roomY = if (fromTop) anchorY else 1f - anchorY
    var w = max(r - l, (b - t) * ratio).coerceAtMost(min(roomX, roomY * ratio))
    w = w.coerceAtLeast(min(min * max(1f, ratio), min(roomX, roomY * ratio)))
    val h = w / ratio
    val left = if (fromLeft) anchorX - w else anchorX
    val top = if (fromTop) anchorY - h else anchorY
    return Rect(left, top, left + w, top + h)
}

/** The ratio a crop holds, as fractions of the turned picture, or null when free. */
internal fun PictureDoc.fractionRatio(shape: CropShape): Float? = shape.ratio?.let { it * turnedHeight / turnedWidth }

/** The edits so far and the ones before them, for undo. */
internal class EditHistory(
    first: PictureDoc,
) {
    var doc by mutableStateOf(first)
        private set
    private val before = mutableStateListOf<PictureDoc>()

    val canUndo: Boolean get() = before.isNotEmpty()

    /** Keep [next] as the picture now, with what it was before for undo. */
    fun commit(next: PictureDoc) {
        if (next == doc) return
        before.addLast(doc)
        doc = next
    }

    fun undo() {
        doc = before.removeLastOrNull() ?: return
    }
}
