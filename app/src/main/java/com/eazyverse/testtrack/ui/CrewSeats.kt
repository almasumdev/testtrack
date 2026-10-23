package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.AppWindow
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A crew's fifteen places, drawn: as tiles with the apps in them, as tiles counted from outside,
 * and as one bar of how full it is.
 */

/**
 * How full the crew is, as one bar and one sentence.
 *
 * Apps seen on Play only. One still waiting for the check is drawn behind the fill in a fainter
 * tone rather than counted into it, because the crew does not start until Play has shown it.
 *
 * Fifteen ticks rather than a smooth bar. A crew is a countable number of people and the number is
 * small enough to see, so counting the gaps answers "how many more" without reading the sentence.
 */
@Composable
fun SeatMeter(
    confirmed: Int,
    checking: Int,
    modifier: Modifier = Modifier,
) {
    val lit by animateFloatAsState(confirmed.toFloat(), tween(450), label = "seats")
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(CrewRules.SEATS) { index ->
            val filled = index < lit
            val held = !filled && index < confirmed + checking
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            filled -> MaterialTheme.colorScheme.primary
                            held -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

/**
 * The crew as fifteen boxes when there are no names to put in them: the open crew, seen from
 * outside. Seen on Play is solid, still waiting is faint, free is empty.
 */
@Composable
fun SeatCountTiles(
    filled: Int,
    taken: Int,
    modifier: Modifier = Modifier,
    /** The taken seats' app icons, confirmed first. A seat without one stays a coloured square. */
    icons: List<String> = emptyList(),
) {
    val perRow = 8
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        (0 until CrewRules.SEATS).chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { index ->
                    if (index >= taken) {
                        EmptySeat(Modifier.weight(1f), next = index == taken)
                    } else {
                        Box(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (index < filled) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer
                                    },
                                ),
                        ) {
                            // The app itself, where it has an icon: a crew reads as the apps in it.
                            // One still being checked is drawn faint, as the square was. Without an
                            // icon, or while one will not load, an app mark rather than a blank
                            // square, which looked like a seat still loading. No names: the open
                            // crew is faces only.
                            val icon = icons.getOrNull(index).orEmpty()
                            val shown = if (index < filled) 1f else 0.45f
                            val mark: @Composable () -> Unit = { SeatMark(solid = index < filled) }
                            if (icon.isNotBlank()) {
                                SubcomposeAsyncImage(
                                    model = icon,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize().alpha(shown),
                                    error = { mark() },
                                )
                            } else {
                                mark()
                            }
                        }
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** An app in a seat with no icon to show: a window mark on its square, solid once checked on Play. */
@Composable
private fun SeatMark(solid: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Lucide.AppWindow,
            null,
            Modifier.fillMaxSize(0.56f),
            tint =
                if (solid) {
                    MaterialTheme.colorScheme.onPrimary.copy(
                        alpha = 0.85f,
                    )
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                },
        )
    }
}

/**
 * A seat nobody has taken: a dashed outline, like a space marked out on the floor.
 *
 * It was a faint filled box, which is what a loading placeholder looks like, so a crew with free
 * seats read as a crew still loading. [next] is the one the next app would take, marked with a
 * plus so it reads as an invitation rather than a gap.
 */
@Composable
private fun EmptySeat(
    modifier: Modifier,
    next: Boolean = false,
) {
    val tone = Tt.colors.accentInk
    Box(
        modifier
            .aspectRatio(1f)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                val corner = 9.dp.toPx()
                drawRoundRect(
                    color = tone.copy(alpha = if (next) 0.55f else 0.28f),
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(corner - stroke / 2f),
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (next) Icon(Lucide.Plus, null, Modifier.size(14.dp), tint = tone.copy(alpha = 0.7f))
    }
}
