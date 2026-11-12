package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The tab row as one round track with the current tab filled in it.
 *
 * It draws its own gutter on both sides and a little room above, so a caller gives it the full
 * width and nothing else. [gap] is only kept for callers that still pass it; segments sit a fixed
 * 2dp apart.
 *
 * Segments are equal widths whenever every label and its count fit in an equal share, shrinking
 * the words to at most 0.7 of their size to get there. Only when they cannot do that do segments
 * take the room their words need with the leftover shared out, and past that the track scrolls and
 * keeps the current tab in view. Each label reserves its semibold width and is centred in it, so
 * choosing a tab does not move its neighbours or push its text off the middle.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    /** Per tab, in the same order. Zero, or an absent entry, draws nothing. */
    counts: List<Int> = emptyList(),
    @Suppress("UNUSED_PARAMETER") gap: Int = 20,
    onSelect: (Int) -> Unit,
) {
    if (labels.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 6.dp)) {
        val base = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
        val countStyle =
            MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val n = labels.size

        fun px(d: Dp) = with(density) { d.toPx() }

        fun count(index: Int) = counts.getOrElse(index) { 0 }

        // What each segment spends besides its words: touch room on both sides, and its count when it has one.
        val fixed =
            labels.indices.map {
                val c = count(it)
                px(SegPad * 2) +
                    if (c > 0) px(CountGap) + measurer.measure("$c", countStyle).size.width + px(CountPad * 2) else 0f
            }

        fun words(
            index: Int,
            scale: Float,
        ) = measurer
            .measure(labels[index], base.copy(fontSize = base.fontSize * scale, fontWeight = FontWeight.SemiBold))
            .size.width
            .toFloat()

        // The track's inside, which the segments and their gaps share exactly.
        val viewport = constraints.maxWidth - px(TrackInset * 2)
        val inner = viewport - px(SegGap) * (n - 1)
        val equal = inner / n

        // The biggest size at which every label and its count sit inside an equal share, if any.
        val equalScale =
            labels.indices
                .minOf { (equal - fixed[it]) / words(it, 1f) }
                .coerceAtMost(1f)
        val equalFits =
            equalScale >= MIN_SCALE && labels.indices.all { words(it, equalScale) + fixed[it] <= equal }

        val fullWords = labels.indices.sumOf { words(it, 1f).toDouble() }.toFloat()
        val scale =
            when {
                equalFits -> equalScale
                fullWords + fixed.sum() <= inner -> 1f
                else -> ((inner - fixed.sum()) / fullWords).coerceIn(MIN_SCALE, 1f)
            }
        val style = base.copy(fontSize = base.fontSize * scale)
        val natural = labels.indices.map { words(it, scale) + fixed[it] }
        val overflow = !equalFits && natural.sum() > inner
        val share = if (overflow) 0f else (inner - natural.sum()) / n
        val widths = if (equalFits) List(n) { equal } else natural.map { it + share }

        val scroll = rememberScrollState()
        LaunchedEffect(selected, overflow, viewport, scale) {
            if (overflow && selected in 0 until n) {
                val left = widths.take(selected).sum() + px(SegGap) * selected
                scroll.animateScrollTo((left - (viewport - widths[selected]) / 2f).toInt().coerceAtLeast(0))
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = TrackHeight)
                .clip(TrackShape)
                .background(Tt.colors.card)
                .border(1.dp, Tt.colors.cardLine, TrackShape)
                .padding(TrackInset),
            contentAlignment = Alignment.Center,
        ) {
            // As tall as its tallest segment, and never under SegHeight, so the pill keeps the
            // same gap to the track on every side whatever the font size.
            Row(
                Modifier
                    .heightIn(min = SegHeight)
                    .height(IntrinsicSize.Min)
                    .horizontalScroll(scroll, enabled = overflow),
                horizontalArrangement = Arrangement.spacedBy(SegGap),
            ) {
                labels.forEachIndexed { index, label ->
                    val c = count(index)
                    Segment(
                        label = label,
                        count = c,
                        current = index == selected,
                        style = style,
                        countStyle = countStyle,
                        width = with(density) { widths[index].toDp() },
                        labelWidth = with(density) { words(index, scale).toDp() },
                    ) { onSelect(index) }
                }
            }
        }
    }
}

@Composable
private fun Segment(
    label: String,
    count: Int,
    current: Boolean,
    style: TextStyle,
    countStyle: TextStyle,
    width: Dp,
    labelWidth: Dp,
    onSelect: () -> Unit,
) {
    val t = Tt.colors
    val ink by animateColorAsState(
        if (current) t.tabSelected else t.text2,
        tween(durationMillis = 200),
        label = "segment-ink",
    )
    val fill by animateColorAsState(
        if (current) t.leafAccentSoft else t.leafAccentSoft.copy(alpha = 0f),
        tween(durationMillis = 200),
        label = "segment-fill",
    )
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .heightIn(min = SegHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            // The tab you are already on does not answer, and `selectable` keeps its announcement.
            .selectable(
                selected = current,
                enabled = !current,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ).semantics { if (count > 0) contentDescription = "$label, $count waiting" },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                Modifier.width(labelWidth),
                style = style,
                textAlign = TextAlign.Center,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                color = ink,
                maxLines = 1,
                softWrap = false,
            )
            if (count > 0) {
                Spacer(Modifier.width(CountGap))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        // At night the selected pill is the same grey as tile, so the badge sits on card there.
                        .background(if (current && t.isDark) t.card else t.tile)
                        .padding(horizontal = CountPad),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$count", style = countStyle, color = t.tileInk, maxLines = 1)
                }
            }
        }
    }
}

private const val MIN_SCALE = 0.7f
private val TrackShape = RoundedCornerShape(percent = 50)

// From the track's outer edge to the selected pill, on all four sides: the 1dp border and the
// padding together. The maths and the padding both read this one value.
private val TrackInset = 4.dp
private val SegHeight = 34.dp
private val TrackHeight = SegHeight + TrackInset * 2
private val SegGap = 2.dp

// Room each side of a segment's contents.
private val SegPad = 10.dp
private val CountGap = 5.dp
private val CountPad = 5.dp
