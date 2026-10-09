package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---- loading -----------------------------------------------------------------------------

/**
 * A placeholder in the shape of what is arriving.
 *
 * Never a spinner in the middle of an empty screen: a skeleton says how much is coming and where
 * it will be, and because results are cached this is only ever seen on genuinely first sight.
 */
@Composable
fun Skeleton(
    width: Dp? = null,
    height: Dp = 12.dp,
    corner: Dp = 6.dp,
    modifier: Modifier = Modifier,
) {
    val base = Status.upcoming
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)

    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -600f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "shift",
    )

    Box(
        modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(
                Brush.linearGradient(
                    listOf(base, highlight, base),
                    start = Offset(shift, 0f),
                    end = Offset(shift + 400f, 0f),
                ),
            ),
    )
}

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
