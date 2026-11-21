package com.eazyverse.testtrack.ui

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
 * A run of settings rows, each its own hairline tile, eight points apart.
 *
 * The rows sit inside the page's gutter, the same as the record cards above them.
 */
@Composable
fun TileGroup(
    count: Int,
    modifier: Modifier = Modifier,
    tile: @Composable (index: Int) -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

/**
 * One settings row: an icon tile, a title with a line under it, an end mark.
 *
 * A hairline tile ([tileFrame]), the same as the record cards on the You page: the page colour
 * inside a 1dp card line, 14 corners, and the tile's own ripple when pressed. 60 points at the
 * least. [end] says where a tap goes ([RowEnd]). A row with a state says it in its [subtitle].
 * [danger] turns the title and the tile red and drops the end mark; [trailing] replaces the end
 * mark with anything else, such as a switch. [chosen] is a picker's chosen row, marked the one way
 * every picker marks it: the soft accent wash and a bolder title, with the check as its [trailing].
 */
@Composable
fun TileRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .tileFrame(onClick = onClick, fill = if (chosen) t.leafAccentSoft else t.bg)
            // 12 over and under: the 36dp icon tile makes the row exactly the 60 it is at the least.
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else IconTile(icon, size = 36.dp, danger = danger, corner = 11.dp, glyph = 19.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // One line each, so every row is the same height and a long line is cut, not wrapped.
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (subtitleIcon != null) {
                        Icon(subtitleIcon, null, Modifier.size(13.dp), tint = tone)
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        sub,
                        style = TextStyles.SettingLine,
                        color = tone,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        } else if (mark != null) {
            Spacer(Modifier.width(10.dp))
            Icon(mark, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}
