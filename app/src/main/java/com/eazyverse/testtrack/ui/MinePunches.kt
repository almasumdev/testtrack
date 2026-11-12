package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** One step of a review on its ticket: done, the one to do now (or the one held up), or still ahead. */
internal enum class Punch { Done, Next, Hold, Todo }

/**
 * The holes punched in a review's ticket, one per step, in one row and with no labels.
 * Done is filled, the next is a ring (amber when it is held up) and what is ahead is a thin dashed
 * circle. A screen reader hears "Step 3 of 4", or that every step is done.
 */
@Composable
internal fun PunchDots(
    states: List<Punch>,
    modifier: Modifier = Modifier,
    size: Dp = 9.dp,
    gap: Dp = 3.dp,
) {
    val t = Tt.colors
    val step = states.indexOfFirst { it != Punch.Done } + 1
    val said = if (step == 0) "All ${states.size} steps done" else "Step $step of ${states.size}"
    val width = size * states.size + gap * (states.size - 1)
    Canvas(modifier.size(width, size).semantics { contentDescription = said }) {
        val d = size.toPx()
        states.forEachIndexed { i, state ->
            val centre = Offset(i * (d + gap.toPx()) + d / 2f, d / 2f)
            when (state) {
                Punch.Done -> drawCircle(t.leafAccent, d / 2f, centre)
                Punch.Next, Punch.Hold -> {
                    val ring = 1.5.dp.toPx()
                    drawCircle(if (state == Punch.Hold) t.warn else t.leafAccent, (d - ring) / 2f, centre, style = Stroke(ring))
                }
                Punch.Todo -> {
                    val line = 1.dp.toPx()
                    drawCircle(
                        t.glassBorder,
                        (d - line) / 2f,
                        centre,
                        style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 1.5.dp.toPx()))),
                    )
                }
            }
        }
    }
}
