package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowUpRight
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Settings rows and the groups they stand in. Split from Garden.kt, which holds the other shared
 * pieces the screens are built from.
 */

/** Where a tile sits in a run of them, which decides which of its corners are round, as on Ranks. */
enum class TilePlace { ONLY, FIRST, MIDDLE, LAST }

/** [outer] rounds the run's own ends, [inner] the corners where two tiles of it meet. */
fun TilePlace.shape(
    outer: Dp = 22.dp,
    inner: Dp = 6.dp,
): RoundedCornerShape =
    when (this) {
        TilePlace.ONLY -> RoundedCornerShape(outer)
        TilePlace.FIRST -> RoundedCornerShape(outer, outer, inner, inner)
        TilePlace.MIDDLE -> RoundedCornerShape(inner)
        TilePlace.LAST -> RoundedCornerShape(inner, inner, outer, outer)
    }

/**
 * A run of settings rows, four points apart, with no rules between them.
 *
 * The rows have no ground of their own, so the gap and the icon tiles are what keep them apart. The
 * run reaches [bleed] past the page's gutter on each side, so a row's press wash stands a little
 * wider than the cards around it while its icon tile still sits inside the gutter.
 */
@Composable
fun TileGroup(
    count: Int,
    modifier: Modifier = Modifier,
    bleed: Dp = 4.dp,
    tile: @Composable (index: Int) -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val extra = if (constraints.hasBoundedWidth) bleed.roundToPx() else 0
                val wide = constraints.maxWidth + extra * 2
                val p = measurable.measure(if (extra > 0) constraints.copy(minWidth = wide, maxWidth = wide) else constraints)
                layout(p.width - extra * 2, p.height) { p.place(-extra, 0) }
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i -> tile(i) }
    }
}

/** What the far end of a [TileRow] says about where a tap goes. */
enum class RowEnd {
    /** A chevron whenever the row can be tapped. */
    AUTO,

    /** Opens a page or a sheet in the app: a chevron. */
    PAGE,

    /** Leaves the app, for the browser, Play, the phone's settings or mail: an arrow up and out. */
    OUT,

    /** Does its job right here: no mark. */
    NONE,
}

/** The shape a row's press wash, and a picker's chosen wash, fill. */
private val RowPressShape = RoundedCornerShape(16.dp)

/**
 * One settings row: an icon tile, a title with a line under it, an end mark.
 *
 * Open, on the page itself: no ground and no edge, only the icon tile is filled, and a soft wash in
 * a 16dp shape shows while it is pressed. 60 points at the least. [end] says where a tap goes
 * ([RowEnd]); [status] is a short word in a small badge at the start of the subtitle line, in
 * [statusTone], and the subtitle gives way before it does. [danger] turns the title and the tile
 * red and drops the end mark; [trailing] replaces the end mark with anything else, such as a
 * switch. [chosen] is a picker's chosen row, marked the one way every picker marks it: the soft
 * accent wash and a bolder title, with the check as its [trailing].
 */
@Composable
fun TileRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    status: String? = null,
    statusTone: Tone = Tone.OK,
    end: RowEnd = RowEnd.AUTO,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** A subtitle with styling in it, such as the gold stars in the rating row. Wins over [subtitle]. */
    subtitleRich: AnnotatedString? = null,
    /** Drawn instead of the icon tile, at its size: an app's own icon in a list of apps. */
    leading: (@Composable () -> Unit)? = null,
    chosen: Boolean = false,
    /** A small mark and a colour for the subtitle, such as the green tick on a ready row. */
    subtitleIcon: ImageVector? = null,
    subtitleTone: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = Tt.colors
    val mark =
        when {
            danger || trailing != null -> null
            end == RowEnd.PAGE || (end == RowEnd.AUTO && onClick != null) -> Lucide.ChevronRight
            end == RowEnd.OUT -> Lucide.ArrowUpRight
            else -> null
        }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val wash by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 60 else 220), label = "row-press")
    val washInk = t.text
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RowPressShape)
            .then(if (chosen) Modifier.background(t.leafAccentSoft) else Modifier)
            .drawBehind { if (wash > 0f) drawRect(washInk.copy(alpha = 0.06f * wash)) }
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = null, onClick = onClick) else Modifier,
            ).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else IconTile(icon, size = 40.dp, danger = danger, corner = 13.dp, glyph = 20.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            // Title and subtitle together are no taller than the 40dp icon tile beside them: a 20sp
            // line and an 18dp one at most, with nothing between. Every row lines up the same way,
            // and a subtitle never runs to a second line and pushes its row taller than the others.
            Text(
                title,
                style = TextStyles.SettingTitle,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                color = if (danger) t.bad else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = subtitleRich ?: subtitle?.let { AnnotatedString(it) }
            if (sub != null || status != null) {
                val tone = subtitleTone ?: t.text3
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (status != null) {
                        StatusChip(status, statusTone, size = ChipSize.INLINE)
                        if (sub != null) Spacer(Modifier.width(6.dp))
                    }
                    if (subtitleIcon != null && sub != null) {
                        Icon(subtitleIcon, null, Modifier.size(13.dp), tint = tone)
                        Spacer(Modifier.width(5.dp))
                    }
                    if (sub != null) {
                        // Weighted, so it is measured after the badge and only the words are cut.
                        Text(
                            sub,
                            Modifier.weight(1f, fill = false),
                            style = TextStyles.SettingLine,
                            color = tone,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (mark != null) {
            Spacer(Modifier.width(12.dp))
            Icon(mark, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}
