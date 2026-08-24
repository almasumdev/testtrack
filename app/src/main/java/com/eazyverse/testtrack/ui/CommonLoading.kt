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
import androidx.compose.ui.Alignment
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
 * A whole screen, before any of it has arrived.
 *
 * One skeleton for the page, never one per section, because sections do not land together. A
 * header drawn over a list that is still loading states things the list is about to contradict —
 * a group page whose apps have not turned up reads as "everything's done for today", in green,
 * and then flips to "start testing · 5 left". The header was not wrong; it was early.
 *
 * It stands in for content and not for structure: no section labels, no headings. A label is a
 * claim that the section exists, and some of them will not. Content landing shifts the layout a
 * little; a heading over something that never appears is worse.
 */
@Composable
fun SkeletonPage(
    showAction: Boolean = true,
    list: @Composable () -> Unit,
) {
    Spacer(Modifier.height(8.dp))
    Column(Modifier.padding(horizontal = Gutter)) {
        Skeleton(width = 148.dp, height = 34.dp, corner = 10.dp)
        Spacer(Modifier.height(12.dp))
        Skeleton(width = 210.dp, height = 12.dp)
        Spacer(Modifier.height(14.dp))
        SkeletonMeter()
        if (showAction) {
            Spacer(Modifier.height(26.dp))
            Skeleton(height = 52.dp, corner = 16.dp)
        }
    }
    Spacer(Modifier.height(28.dp))
    Rows { list() }
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

/** A ring, a group name, and the day underneath it: the shape of a row on the home screen. */
@Composable
fun SkeletonGroupList(rows: Int = SKELETON_ROWS) {
    repeat(rows) { i ->
        if (i > 0) RowDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Skeleton(width = 36.dp, height = 36.dp, corner = 18.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(width = title(i), height = 13.dp)
                Spacer(Modifier.height(6.dp))
                Skeleton(width = meta(i), height = 11.dp)
            }
        }
    }
}

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

/**
 * A cohort row on the home screen: a name, the day under it, and the day's own meter.
 *
 * Its own shape rather than [SkeletonGroupList], which draws a face at the left because the list
 * it stands in for on the dashboard has one. Home's rows do not, and a circle that resolves into
 * nothing is the skeleton telling a small lie about what is coming.
 */
@Composable
fun SkeletonCohortList(rows: Int = 3) {
    repeat(rows) { i ->
        if (i > 0) RowDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 14.dp)) {
            Skeleton(width = title(i), height = 15.dp)
            Spacer(Modifier.height(7.dp))
            Skeleton(width = meta(i), height = 11.dp)
            Spacer(Modifier.height(10.dp))
            // The row's own day meter, at the 8dp the real one uses.
            SkeletonMeter(boxes = 12, height = 8.dp)
        }
    }
}

/** An icon, an app name, and what it is waiting for. */
@Composable
fun SkeletonAppList(rows: Int = SKELETON_ROWS) {
    repeat(rows) { i ->
        if (i > 0) RowDivider()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(width = title(i), height = 13.dp)
                Spacer(Modifier.height(6.dp))
                Skeleton(width = meta(i), height = 11.dp)
            }
        }
    }
}

/**
 * An icon, a name, who it belongs to, and the row of actions underneath — the shape TestRow and
 * MineRow both settle into now that icon size follows the three lines beside it and every action
 * lives on its own line rather than trailing the title. Its own function rather than a parameter
 * on [SkeletonAppList]: that one still stands for Home's pending queue and Profile's submissions,
 * both still the older single-line shape, and widening it in place would make the skeleton wrong
 * for two screens to fix it for one.
 *
 * Three action-chip widths rather than a fixed count per row: real rows show anywhere from one
 * chip to five, and varying it here is what keeps the skeleton from reading as a promise that
 * every row is about to look identical.
 */
@Composable
fun SkeletonGroupAppList(rows: Int = SKELETON_ROWS) {
    val chipCounts = listOf(2, 3, 1, 3, 2)
    repeat(rows) { i ->
        if (i > 0) RowDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 52.dp, height = 52.dp, corner = 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = title(i), height = 13.dp)
                    Spacer(Modifier.height(5.dp))
                    Skeleton(width = meta(i) * 0.7f, height = 11.dp)
                    Spacer(Modifier.height(4.dp))
                    Skeleton(width = meta(i + 1), height = 10.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(chipCounts[i % chipCounts.size]) { c ->
                    Skeleton(width = (60 + c * 24).dp, height = 24.dp, corner = 8.dp)
                }
            }
        }
    }
}
