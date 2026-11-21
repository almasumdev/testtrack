package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * One tester of the reader's app, as a card of its own: their face in a ring of the run's nights,
 * their name with their day count, one short line on how tonight went (or the whole run, once it
 * is over), and their pictures of the last three nights, each opening whole when tapped.
 */

/**
 * [dayLabel] takes the place of the day count beside the name, such as a private tester's nights.
 * [onRemove] puts a 32dp X at the tile's end. A tester with no nights yet, asked and not answered,
 * has a dashed ring.
 */
@Composable
internal fun TesterTile(
    tester: CrewTester,
    onFace: () -> Unit,
    onPicture: (NightPicture) -> Unit = {},
    dayLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    val t = Tt.colors
    CrewCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "See who this is", onClick = onFace),
                ) { NightsRing(tester) }
                // A volunteer's hand, on the ring's lower corner.
                if (tester.volunteer) VolunteerBadge(16.dp, Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tester.name,
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 19.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = t.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val days = dayLabel ?: if (tester.squares.isEmpty()) "" else "${tester.days} ${pluralOf(tester.days, "day")}"
                    if (!tester.over && (tester.otherDay || days.isNotEmpty())) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (tester.otherDay) "Their day ${tester.day}" else days,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                            color = t.text3,
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    tester.line,
                    Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (tester.tone == Tone.QUIET) t.text3 else tester.tone.ink(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (tester.pictures.isNotEmpty()) {
                // No spacer: the invisible edge and the offset leave 16 before the first thumb.
                // Side by side, 6 apart. Each reserves 48 to touch, so the invisible edges
                // overlap by what makes the visible gap 6.
                // Shifted by the invisible edge, so the last thumb lines up with the card's padding.
                Row(
                    Modifier.offset(x = (48.dp - ThumbWidth) / 2),
                    horizontalArrangement = Arrangement.spacedBy(ThumbGap - (48.dp - ThumbWidth)),
                ) {
                    tester.pictures.forEach { picture ->
                        PictureThumb(
                            picture.ref,
                            picture.byHand,
                            ThumbWidth,
                            Modifier.minimumInteractiveComponentSize(),
                        ) { onPicture(picture) }
                    }
                }
            }
            if (onRemove != null) {
                Spacer(Modifier.width(4.dp))
                // 32 drawn, 48 to touch, at the tile's end.
                Box(Modifier.size(32.dp).wrapContentSize(unbounded = true)) {
                    IconButton(onClick = onRemove) {
                        Icon(Lucide.X, "Remove ${tester.name}", Modifier.size(18.dp), tint = t.text3)
                    }
                }
            }
        }
    }
}

/** What colour a night is in the ring round a tester's face. */
@Composable
private fun ringColor(square: Square): Color {
    val t = Tt.colors
    return when (square) {
        Square.PICTURE, Square.BY_HAND, Square.OPENED -> Status.posted
        Square.MISSED, Square.WAITING_LONG -> t.bad
        Square.NOT_OWED -> t.glassBorderStrong
        Square.WAITING -> t.accent
        Square.TONIGHT, Square.TO_COME -> t.track
        Square.BEFORE -> Color.Transparent
    }
}

/** A tester's face, 32 across, in a ring of the run's nights, one arc each with a small gap. */
@Composable
private fun NightsRing(tester: CrewTester) {
    val colors = tester.squares.map { ringColor(it) }
    val dashed = Tt.colors.glassBorderStrong
    val opened = tester.squares.count { it.counted }
    Box(
        Modifier.size(44.dp).semantics { contentDescription = "${tester.name}, opened yours on $opened ${pluralOf(opened, "night")}" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // No nights yet, asked and not answered: a dashed ring where they would go.
            if (colors.isEmpty()) {
                val stroke = 1.5.dp.toPx()
                drawCircle(
                    dashed,
                    radius = size.minDimension / 2 - stroke / 2,
                    style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()))),
                )
                return@Canvas
            }
            val stroke = 4.dp.toPx()
            val sweep = 360f / colors.size
            val gap = 3f
            val inset = stroke / 2
            colors.forEachIndexed { i, color ->
                if (color == Color.Transparent) return@forEachIndexed
                drawArc(
                    color = color,
                    startAngle = -90f + i * sweep,
                    sweepAngle = sweep - gap,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke),
                )
            }
        }
        Avatar(
            tester.photo,
            initialOf(tester.name),
            size = 32.dp,
            letterStyle = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        )
    }
}

/** The testers page while it loads: the summary, the grid's shape, the heading and three tiles, or as many as asked. */
@Composable
internal fun TestersLoading(
    gridRows: Int = SKELETON_GRID_ROWS,
    tiles: Int = SKELETON_TILES,
) {
    val t = Tt.colors
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = Gutter, end = Gutter, top = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Loading the testers" },
    ) {
        Skeleton(height = 18.dp, modifier = Modifier.fillMaxWidth(0.7f))
        Spacer(Modifier.height(12.dp))
        CrewCard(padding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
            Box(Modifier.padding(start = 4.dp, bottom = 10.dp)) { Skeleton(width = 110.dp, height = 24.dp, corner = 12.dp) }
            repeat(gridRows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(26.dp)) { Skeleton(width = 24.dp, height = 24.dp, corner = 12.dp) }
                    Row(Modifier.weight(1f)) {
                        repeat(CrewRules.RUN_DAYS) {
                            Box(Modifier.weight(1f).padding(horizontal = 0.5.dp, vertical = 1.dp)) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Status.upcoming)
                                        .background(t.accentInk.copy(alpha = 0.07f)),
                                )
                            }
                        }
                    }
                }
            }
        }
        Box(Modifier.padding(top = 20.dp, bottom = 10.dp)) { Skeleton(width = 80.dp, height = 18.dp) }
        repeat(tiles) { i ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            CrewCard(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(width = 44.dp, height = 44.dp, corner = 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Skeleton(height = 15.dp, modifier = Modifier.fillMaxWidth(0.55f))
                        Spacer(Modifier.height(6.dp))
                        Skeleton(height = 12.dp, modifier = Modifier.fillMaxWidth(0.3f))
                    }
                }
            }
        }
    }
}

private val ThumbWidth = 32.dp
private val ThumbGap = 6.dp

private const val SKELETON_GRID_ROWS = 8
private const val SKELETON_TILES = 3
