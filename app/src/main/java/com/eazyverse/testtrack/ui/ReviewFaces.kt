package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The faces on a request's card: one neutral person for every review bought, with a small mark for
 * where it stands. Up to ten are drawn one by one in a grid that fills the card's width; above that
 * the card shows one stack per kind with its count.
 */

/** The room around a face's disc where the amber "to check" ring goes: a 1.5dp gap, then a 2dp ring. */
private val RingGap = 1.5.dp
private val RingWidth = 2.dp
private val RingRoom = RingGap + RingWidth

/**
 * Every face in a grid that fills the width. As many fit per row as leave [fitFaces]'s 8dp gap, the
 * faces grow to fill the row, and later rows start from the left on the same columns. A face's size
 * is its outer edge, ring included, so two neighbours never touch.
 */
@Composable
internal fun FaceGrid(
    counts: ReviewCounts,
    compact: Boolean,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val spoken = counts.spoken()
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f)
            .clearAndSetSemantics { contentDescription = spoken },
    ) {
        val fit =
            if (compact) {
                fitFaces(maxWidth.value, minFace = 24f, maxFace = 28f, minGap = 8f)
            } else {
                fitFaces(maxWidth.value, minFace = 30f, maxFace = 36f, minGap = 8f)
            }
        val overhang = markOverhang(fit.face, fit.gap).dp
        Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
            counts.faces().chunked(fit.perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(fit.gap.dp)) {
                    row.forEach { GridFace(it, fit.face.dp, overhang, compact) }
                }
            }
        }
    }
}

/** One face of the grid: the disc inside the ring's room, the ring itself, and the mark on its corner. */
@Composable
private fun GridFace(
    face: ReviewFace,
    size: Dp,
    overhang: Dp,
    compact: Boolean,
) {
    val t = Tt.colors
    val warn = t.warn
    Box(
        Modifier
            .size(size)
            .drawBehind {
                if (face == ReviewFace.TO_CHECK) {
                    val ring = RingWidth.toPx()
                    drawCircle(warn, radius = (this.size.minDimension - ring) / 2, style = Stroke(ring))
                }
            },
    ) {
        val disc = size - RingRoom * 2
        Disc(face, disc, Modifier.align(Alignment.Center))
        if (face != ReviewFace.OPEN) {
            FaceMark(
                face,
                size * 0.5f,
                border = if (compact) 1.5.dp else 2.dp,
                modifier = Modifier.align(Alignment.BottomEnd).offset(overhang, overhang),
            )
        }
    }
}

/** The person, or a dashed ring for a slot nobody took. One under way is drawn at half strength. */
@Composable
private fun Disc(
    face: ReviewFace,
    size: Dp,
    modifier: Modifier = Modifier,
    ground: Color = Color.Transparent,
    fadeInk: Boolean = false,
) {
    val t = Tt.colors
    if (face == ReviewFace.OPEN) {
        val dashed = t.glassBorderStrong
        Box(
            modifier
                .size(size)
                .background(ground, CircleShape)
                .drawBehind {
                    val w = 1.5.dp.toPx()
                    drawCircle(
                        dashed,
                        radius = (this.size.minDimension - w) / 2,
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                    )
                },
        )
        return
    }
    val faded = face == ReviewFace.IN_PROGRESS && !fadeInk
    Box(
        modifier
            .size(size)
            .alpha(if (faded) 0.5f else 1f)
            .clip(CircleShape)
            .background(t.tile),
        contentAlignment = Alignment.Center,
    ) {
        val ink = if (face == ReviewFace.IN_PROGRESS && fadeInk) t.tileInk.copy(alpha = 0.5f) else t.tileInk
        Icon(Lucide.User, null, Modifier.size(size * 0.56f), tint = ink)
    }
}

/** The small mark for where a review stands: a check when done, an eye to check, a clock under way. */
@Composable
private fun FaceMark(
    face: ReviewFace,
    size: Dp,
    border: Dp,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val (fill, ink) =
        when (face) {
            ReviewFace.DONE -> t.ok to t.bg
            ReviewFace.TO_CHECK -> t.warn to t.onWarn
            else -> t.text4 to t.bg
        }
    val icon =
        when (face) {
            ReviewFace.DONE -> Lucide.Check
            ReviewFace.TO_CHECK -> Lucide.Eye
            else -> Lucide.Clock
        }
    Box(
        modifier
            .size(size)
            .background(t.bg, CircleShape)
            .padding(border)
            .background(fill, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.55f), tint = ink)
    }
}

private val StackFace = 28.dp
private val StackStep = StackFace - 10.dp

/**
 * More than ten reviews: one stack for each kind there is, two to a row, each with its count and
 * a word. Done, to check, under way, open; a kind with none is left out.
 */
@Composable
internal fun FaceSummary(
    counts: ReviewCounts,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
) {
    val spoken = counts.spoken()
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.45f else 1f)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        counts.summary().chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { SummaryGroup(it, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryGroup(
    group: FaceGroup,
    modifier: Modifier,
) {
    val t = Tt.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FaceStack(group.face, minOf(group.count, 2))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                "${group.count}",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp, lineHeight = 18.sp, fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (group.face == ReviewFace.TO_CHECK) t.warn else t.text,
            )
            Text(
                group.word,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 15.sp),
                color = t.text3,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Two faces of one kind overlapping, each cut out by a 2dp ring of the page, the mark on the top one. */
@Composable
private fun FaceStack(
    face: ReviewFace,
    shown: Int,
) {
    val bg = Tt.colors.bg
    Box(Modifier.size(width = StackFace + StackStep * (shown - 1), height = StackFace)) {
        repeat(shown) { i ->
            val top = i == shown - 1
            Box(
                Modifier
                    .offset(x = StackStep * i)
                    .size(StackFace)
                    .drawBehind { drawCircle(bg, radius = this.size.minDimension / 2 + 2.dp.toPx()) },
            ) {
                Disc(face, StackFace, Modifier.matchParentSize(), ground = bg, fadeInk = true)
                if (top && face != ReviewFace.OPEN) {
                    FaceMark(face, 15.dp, border = 2.dp, modifier = Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp))
                }
            }
        }
    }
}
