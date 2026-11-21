package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

/** Where a tile sits in its group, which decides which of its corners are round. */
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
 * A run of tiles, three points apart, with no rules between them.
 *
 * Settings groups. Each row is its own tile, only the outer corners of the run are
 * round, and the gap is what separates them; a divider inside a group was a line doing a job the
 * gap already does.
 */
@Composable
fun TileGroup(
    count: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (index: Int, place: TilePlace) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(count) { i ->
            val place =
                when {
                    count == 1 -> TilePlace.ONLY
                    i == 0 -> TilePlace.FIRST
                    i == count - 1 -> TilePlace.LAST
                    else -> TilePlace.MIDDLE
                }
            tile(i, place)
        }
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

/**
 * One settings row: an icon tile, a title with an optional line under it, a status, an end mark.
 *
 * A solid tile on surface2, 56 points at the least, with no edge line, so two edges never sit side
 * by side in a group. [end] says where a tap goes ([RowEnd]); [status] is a short word in a small
 * chip just before it, in [statusTone]. [danger] turns the title and the tile red and drops the end
 * mark; [trailing] replaces the end mark with anything else, such as a switch. [chosen] is a
 * picker's chosen row, marked the one way every picker marks it: the soft accent wash and a bolder
 * title, with the check as its [trailing].
 */
@Composable
fun TileRow(
    icon: ImageVector,
    title: String,
    place: TilePlace,
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
    val shape = place.shape(TileRadius, 4.dp)
    val mark =
        when {
            danger || trailing != null -> null
            end == RowEnd.PAGE || (end == RowEnd.AUTO && onClick != null) -> Lucide.ChevronRight
            end == RowEnd.OUT -> Lucide.ArrowUpRight
            else -> null
        }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(t.surface2)
            .then(if (chosen) Modifier.background(t.leafAccentSoft) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else IconTile(icon, danger = danger, corner = 11.dp, glyph = 19.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Title and subtitle together are exactly as tall as the 36dp icon tile beside them: a
            // 19sp line and a 17sp one, with nothing between. Every row lines up the same way, and a
            // subtitle never runs to a second line and pushes its row taller than the others.
            Text(
                title,
                style = TextStyles.SettingTitle,
                fontWeight = if (chosen) FontWeight.Bold else FontWeight.SemiBold,
                color = if (danger) t.bad else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = subtitleRich ?: subtitle?.let { AnnotatedString(it) }
            if (sub != null) {
                val tone = subtitleTone ?: t.text3
                val line: @Composable () -> Unit = {
                    Text(
                        sub,
                        style = TextStyles.SettingLine,
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (subtitleIcon == null) {
                    line()
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(subtitleIcon, null, Modifier.size(13.dp), tint = tone)
                        Spacer(Modifier.width(5.dp))
                        line()
                    }
                }
            }
        }
        if (status != null) {
            Spacer(Modifier.width(12.dp))
            StatusChip(status, statusTone, size = ChipSize.SMALL)
        }
        if (trailing != null) {
            Spacer(Modifier.width(if (status != null) 8.dp else 12.dp))
            trailing()
        } else if (mark != null) {
            Spacer(Modifier.width(if (status != null) 8.dp else 12.dp))
            Icon(mark, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}
