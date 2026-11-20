package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
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
    /** The bar's colour when it sits on a ground too close to the default, such as a ticket stub. */
    ground: Color = Status.upcoming,
) {
    val base = ground
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
 * How many rows a list skeleton draws, never fewer than ten.
 *
 * More than the screen holds on purpose. A list that stops halfway down has already answered the
 * question, and the answer it gives is "there are three of these", which is a thing nobody knows
 * yet.
 */
const val SKELETON_ROWS = 10

private val Titles = listOf(118, 92, 140, 104, 126, 86, 132)

private fun title(i: Int) = Titles[i % Titles.size].dp

/**
 * An [AppHead] on its way: the icon's square, the name and the line under it. [name] and [sub] are
 * the two bars' widths.
 */
@Composable
fun AppHeadSkeleton(
    modifier: Modifier = Modifier,
    name: Dp = 140.dp,
    sub: Dp = 80.dp,
    /** The icon's size: [AppHead]'s, grown with the font, unless given. */
    icon: Dp = AppTileIcon * LocalDensity.current.fontScale.coerceAtLeast(1f),
    corner: Dp = icon * 0.3f,
    nameHeight: Dp = 15.dp,
    /** Between the two bars. */
    gap: Dp = 7.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Skeleton(width = icon, height = icon, corner = corner)
        Spacer(Modifier.width(12.dp))
        Column {
            Skeleton(width = name, height = nameHeight)
            Spacer(Modifier.height(gap))
            Skeleton(width = sub, height = 11.dp)
        }
    }
}

/**
 * A [SectionHeading] on its way: a bar [width] wide, and at the far end an [end] bar as tall as
 * [endHeight], such as a pill's. [top] is the space over it, as the heading's own.
 */
@Composable
fun SkeletonHeading(
    width: Dp,
    modifier: Modifier = Modifier,
    end: Dp? = null,
    endHeight: Dp = 15.dp,
    top: Dp = 24.dp,
) {
    Row(
        modifier.fillMaxWidth().padding(top = top, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { Skeleton(width = width, height = 15.dp) }
        if (end != null) Skeleton(width = end, height = endHeight, corner = if (endHeight > 20.dp) endHeight / 2 else 6.dp)
    }
}

/**
 * A page's placeholder giving way to the page, as a short fade rather than a cut.
 *
 * [content] is told which of the two it is drawing, and must draw from that rather than from the
 * screen's own flags: for the length of the fade both are on screen, and the one leaving has to go
 * on being the placeholder it was, not jump to the answer that has just arrived.
 */
@Composable
fun LoadSwap(
    loading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (loading: Boolean) -> Unit,
) = Crossfade(loading, modifier, animationSpec = tween(LOAD_SWAP_MS), label = "load swap") { content(it) }

/** Long enough to read as a change of state, short enough not to hold anything up. */
private const val LOAD_SWAP_MS = 320
