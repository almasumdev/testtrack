package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The app's ticket: the mark beside the balance in the top bar and above the pay on a Circle
 * request. Outline in `leafAccent`, body in [fill] (`leafAccentSoft` unless a caller sits it on
 * that same colour and needs another).
 */
@Composable
fun rememberTicketMark(fill: Color = Tt.colors.leafAccentSoft): ImageVector {
    val line = Tt.colors.leafAccent
    return remember(line, fill) { ticketMark(line, fill) }
}

/** A ticket with notched ends and a tear line, on a 24 grid. Built here so it follows the theme. */
private fun ticketMark(
    line: Color,
    fill: Color,
): ImageVector =
    ImageVector
        .Builder("TicketMark", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            PathParser()
                .parsePathString(
                    "M4.5 4.5H19.5a2.5 2.5 0 0 1 2.5 2.5V9a3 3 0 0 0 0 6v2a2.5 2.5 0 0 1-2.5 2.5H4.5A2.5 2.5 0 0 1 2 17v-2a3 3 0 0 0 0-6V7a2.5 2.5 0 0 1 2.5-2.5Z",
                ).toNodes(),
            fill = SolidColor(fill),
            stroke = SolidColor(line),
            strokeLineWidth = 1.6f,
            strokeLineJoin = StrokeJoin.Round,
        ).addPath(
            PathParser().parsePathString("M16 7.4v.4M16 11.8v.4M16 16.2v.4").toNodes(),
            stroke = SolidColor(line),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
        ).build()
