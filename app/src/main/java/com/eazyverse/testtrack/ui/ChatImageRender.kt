package com.eazyverse.testtrack.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import com.eazyverse.testtrack.R
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Drawing an edited picture: the same steps on the screen and into the picture that is sent, so
 * what goes is what was seen.
 */

/** The most a chat picture may weigh. The server takes up to 1 MiB; this leaves room. */
private const val SEND_MAX_BYTES = 1_000_000

/** The longest side a chat picture is sent at. The server keeps less, so nothing is lost. */
private const val SEND_LONG_SIDE = 1600

/** The face words are drawn in: the app's Barlow Bold, as the editor types them. */
@Volatile
private var wordsFace: Typeface? = null

/** Reads Barlow Bold once, before any words are drawn. System bold stands in if it can't be read. */
internal fun loadWordsFace(context: Context) {
    if (wordsFace != null) return
    wordsFace = runCatching { ResourcesCompat.getFont(context, R.font.barlow_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
}

/**
 * The picture under [m] (its own pixels to the canvas), with its lines and words, and [live] being
 * drawn. [hide] leaves one word out while it is being typed over. [shadow] is the soft edge under
 * words, so they read on any picture.
 */
internal fun drawPicture(
    canvas: Canvas,
    bitmap: Bitmap,
    doc: PictureDoc,
    m: Matrix,
    shadow: Int,
    live: PenStroke? = null,
    hide: Long? = null,
) {
    canvas.save()
    canvas.concat(m)
    canvas.drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
    doc.strokes.forEach { drawStroke(canvas, it) }
    live?.let { drawStroke(canvas, it) }
    doc.texts.forEach { if (it.id != hide) drawWords(canvas, it, shadow) }
    canvas.restore()
}

private fun drawStroke(
    canvas: Canvas,
    stroke: PenStroke,
) {
    val points = stroke.points
    if (points.isEmpty()) return
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color.toArgb()
            strokeWidth = stroke.width
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    if (points.size == 1) {
        canvas.drawPoint(points[0].x, points[0].y, paint)
        return
    }
    val path = Path()
    path.moveTo(points[0].x, points[0].y)
    // Through the middle of each pair of points, so a quick line comes out smooth.
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        path.quadTo(a.x, a.y, (a.x + b.x) / 2f, (a.y + b.y) / 2f)
    }
    path.lineTo(points.last().x, points.last().y)
    canvas.drawPath(path, paint)
}

/** The words laid out at their size: bold, centred, one line per line typed. */
private fun layoutOf(
    item: PictureText,
    shadow: Int?,
): StaticLayout {
    val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = item.color.toArgb()
            textSize = item.size
            typeface = wordsFace ?: Typeface.DEFAULT_BOLD
            if (shadow != null) setShadowLayer(item.size * 0.12f, 0f, item.size * 0.03f, shadow)
        }
    val width =
        item.text
            .split('\n')
            .maxOf { paint.measureText(it) }
            .let { ceil(it).toInt() }
            .coerceAtLeast(1)
    return StaticLayout.Builder
        .obtain(item.text, 0, item.text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .setIncludePad(false)
        .build()
}

private fun drawWords(
    canvas: Canvas,
    item: PictureText,
    shadow: Int,
) {
    if (item.text.isBlank()) return
    val layout = layoutOf(item, shadow)
    canvas.save()
    canvas.translate(item.center.x, item.center.y)
    canvas.rotate(item.angle)
    canvas.translate(-layout.width / 2f, -layout.height / 2f)
    layout.draw(canvas)
    canvas.restore()
}

/** How big the words are on the picture, in its own pixels. */
internal fun wordsSize(item: PictureText): Size {
    if (item.text.isBlank()) return Size.Zero
    val layout = layoutOf(item, null)
    return Size(layout.width.toFloat(), layout.height.toFloat())
}

/** Whether [point] (the picture's own pixels) falls on [item], with [slop] to spare round it. */
internal fun wordsHit(
    item: PictureText,
    point: Offset,
    slop: Float,
): Boolean {
    val size = wordsSize(item)
    val rad = Math.toRadians(-item.angle.toDouble())
    val dx = point.x - item.center.x
    val dy = point.y - item.center.y
    val x = dx * cos(rad) - dy * sin(rad)
    val y = dx * sin(rad) + dy * cos(rad)
    return kotlin.math.abs(x) <= size.width / 2f + slop && kotlin.math.abs(y) <= size.height / 2f + slop
}

/**
 * The edited picture as it is sent: turned, cropped, drawn on, no longer than [SEND_LONG_SIDE] on
 * its long side and a JPEG under [SEND_MAX_BYTES]. Slow, so off the main thread.
 */
internal fun exportPicture(
    bitmap: Bitmap,
    doc: PictureDoc,
    shadow: Int,
): ByteArray {
    var longSide = max(doc.cropWidth, doc.cropHeight).coerceAtMost(SEND_LONG_SIDE.toFloat())
    repeat(4) {
        val scale = longSide / max(doc.cropWidth, doc.cropHeight)
        val w = (doc.cropWidth * scale).toInt().coerceAtLeast(1)
        val h = (doc.cropHeight * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        try {
            drawPicture(Canvas(out), bitmap, doc, doc.matrix(w.toFloat(), h.toFloat()), shadow)
            jpegUnder(out)?.let { return it }
        } finally {
            out.recycle()
        }
        longSide *= 0.75f
    }
    error("That picture is too big to send.")
}

private fun jpegUnder(bitmap: Bitmap): ByteArray? {
    val output = ByteArrayOutputStream()
    for (quality in listOf(88, 80, 70, 60)) {
        output.reset()
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
        if (output.size() <= SEND_MAX_BYTES) return output.toByteArray()
    }
    return null
}
