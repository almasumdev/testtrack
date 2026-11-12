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
internal val TICKET_STUB = 64.dp

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
