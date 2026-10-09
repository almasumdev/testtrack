package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

// ---- loading -----------------------------------------------------------------------------

/**
 * A placeholder in the shape of what is arriving.
 *
 * Never a spinner in the middle of an empty screen: a skeleton says how much is coming and where
 * it will be, and because results are cached this is only ever seen on genuinely first sight.
 *
 * The shine is one diagonal band crossing the whole window, not one per box. Each box used to run
 * its own gradient in its own coordinates, so a page of them twinkled out of step; now every box
 * draws its slice of the same band, worked out from where it sits on screen and from the robots'
 * shared clock, so the page reads as one surface being lit and a tab opened later is lit in step.
 */
@Composable
fun Skeleton(
    width: Dp? = null,
    height: Dp = 12.dp,
    corner: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val base = Status.upcoming
    // A touch of the accent over it, so a bar still stands off the glass tile it sits on.
    val tint = Tt.colors.accentInk.copy(alpha = 0.07f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
    val moving = ValueAnimator.areAnimatorsEnabled()
    val time = robotTime(moving)
    val span = LocalWindowInfo.current.containerSize.let { it.width + it.height * SLOPE }
    var origin by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .onGloballyPositioned { origin = it.positionInWindow() }
            .clip(RoundedCornerShape(corner))
            .drawBehind {
                drawRect(base)
                drawRect(tint)
                if (!moving) return@drawBehind
                // Where the band's middle is along the sweep, from just before the window's
                // top-left corner to just past its bottom-right one.
                val band = span * 0.35f
                val along = (time.longValue % SWEEP_MS).toFloat() / SWEEP_MS * (span + 2 * band) - band
                // The sweep runs along (1, SLOPE); a point's distance along it is x + SLOPE * y.
                val norm = 1f + SLOPE * SLOPE
                val start = Offset((along - band / 2f) / norm, SLOPE * (along - band / 2f) / norm) - origin
                val end = Offset((along + band / 2f) / norm, SLOPE * (along + band / 2f) / norm) - origin
                drawRect(Brush.linearGradient(listOf(Color.Transparent, highlight, Color.Transparent), start, end))
            },
    )
}

/** How far down the sweep leans for each step across. */
private const val SLOPE = 0.35f

/** One pass of the shine, corner to corner. */
private const val SWEEP_MS = 1600L

/**
 * How many rows a skeleton draws.
 *
 * More than the screen holds on purpose. A list that stops halfway down has already answered the
 * question, and the answer it gives is "there are three of these", which is a thing nobody knows
 * yet.
 */
const val SKELETON_ROWS = 8

private val Titles = listOf(118, 92, 140, 104, 126, 86, 132)
private val Metas = listOf(168, 132, 190, 150, 118, 176, 142)

private fun title(i: Int) = Titles[i % Titles.size].dp

private fun meta(i: Int) = Metas[i % Metas.size].dp

/**
 * A meter's placeholder, in the meter's own shape.
 *
 * [Meter] is boxes with gaps between them, so a single long bar standing in for it is a skeleton
 * describing a different component. The count is a guess because the real one is not known yet,
 * which is fine: what a skeleton owes is the shape, and a row of boxes is unmistakably the shape.
 *
 * Same 4dp gap and 3dp corner as the real one, so nothing shifts sideways when it lands.
 */
@Composable
fun SkeletonMeter(
    boxes: Int = 12,
    height: Dp = 10.dp,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(boxes) {
            Skeleton(height = height, corner = 3.dp, modifier = Modifier.weight(1f))
        }
    }
}
