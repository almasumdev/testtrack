package com.eazyverse.testtrack.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A card with a ticket's two bites out of it: a rounded rectangle with a half circle cut from the
 * top edge and another from the bottom, [stub] in from the right. The stub is the part to the
 * right of the bites, and the tear line is drawn down between them.
 *
 * The cut is a real hole in the outline, not a patch in the page colour, so the page, and
 * whatever scrolls under the card, shows through it in both themes.
 */
class TicketShape(
    private val stub: Dp = TICKET_STUB,
    private val notch: Dp = 7.dp,
    private val corner: Dp = 14.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val body =
            Path().apply {
                addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(with(density) { corner.toPx() })))
            }
        // Centred on the tear line, which sits three quarters of a dp inside the stub's edge.
        val x = size.width - with(density) { stub.toPx() + 0.75.dp.toPx() }
        val r = with(density) { notch.toPx() }
        val bites =
            Path().apply {
                addOval(Rect(Offset(x - r, -r), Size(r * 2f, r * 2f)))
                addOval(Rect(Offset(x - r, size.height - r), Size(r * 2f, r * 2f)))
            }
        return Outline.Generic(Path.combine(PathOperation.Difference, body, bites))
    }
}

/** The width of a ticket's stub, which [TicketShape] cuts its bites in front of. */
internal val TICKET_STUB = 58.dp

/** The dashed line where a ticket tears, a hair inside the stub's left edge and clear of the bites. */
internal fun Modifier.tearLine(color: Color) =
    drawBehind {
        val x = 0.75.dp.toPx()
        drawLine(
            color = color,
            start = Offset(x, 7.dp.toPx()),
            end = Offset(x, size.height - 7.dp.toPx()),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        )
    }

/** Which of the two pieces a torn ticket is drawn as. */
enum class TornPart { BODY, STUB }

/**
 * One piece of a ticket torn in two, for a review that has been paid: the body, or the stub that
 * came away. Both are laid over the whole ticket's size, so they share the tear line [stub] in
 * from the right edge, the same quarter circle bites at the top and bottom, and the same corners
 * as [TicketShape]. Between the bites the tear is a zigzag, one point every 3.5dp of height,
 * swinging 1.6dp either side of the line; the stub's edge is the body's, reversed, so the two fit.
 */
class TornTicketShape(
    private val part: TornPart,
    private val stub: Dp = TICKET_STUB,
    private val notch: Dp = 7.dp,
    private val corner: Dp = 14.dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val xb = w - with(density) { stub.toPx() }
        val n = with(density) { notch.toPx() }
        val c = with(density) { corner.toPx() }
        val zig = zigzag(xb, h, n, with(density) { 3.5.dp.toPx() }, with(density) { 1.6.dp.toPx() }, with(density) { 1.dp.toPx() })
        val bite = Rect(xb - n, -n, xb + n, n)
        val bottomBite = Rect(xb - n, h - n, xb + n, h + n)
        val path = Path()
        if (part == TornPart.BODY) {
            path.moveTo(c, 0f)
            path.lineTo(xb - n, 0f)
            path.arcTo(bite, 180f, -90f, false)
            zig.forEach { path.lineTo(it.x, it.y) }
            path.lineTo(xb, h - n)
            path.arcTo(bottomBite, 270f, -90f, false)
            path.lineTo(c, h)
            path.arcTo(Rect(0f, h - 2 * c, 2 * c, h), 90f, 90f, false)
            path.lineTo(0f, c)
            path.arcTo(Rect(0f, 0f, 2 * c, 2 * c), 180f, 90f, false)
        } else {
            path.moveTo(xb, n)
            path.arcTo(bite, 90f, -90f, false)
            path.lineTo(w - c, 0f)
            path.arcTo(Rect(w - 2 * c, 0f, w, 2 * c), 270f, 90f, false)
            path.lineTo(w, h - c)
            path.arcTo(Rect(w - 2 * c, h - 2 * c, w, h), 0f, 90f, false)
            path.lineTo(xb + n, h)
            path.arcTo(bottomBite, 0f, -90f, false)
            zig.asReversed().forEach { path.lineTo(it.x, it.y) }
        }
        path.close()
        return Outline.Generic(path)
    }

    /** The points of the tear between the bites, top to bottom, alternating either side of [xb]. */
    private fun zigzag(
        xb: Float,
        h: Float,
        n: Float,
        step: Float,
        swing: Float,
        margin: Float,
    ): List<Offset> {
        val points = mutableListOf<Offset>()
        var y = n + step
        var k = 0
        while (y < h - n - margin) {
            points += Offset(if (k % 2 == 0) xb - swing else xb + swing, y)
            y += step
            k++
        }
        return points
    }
}
