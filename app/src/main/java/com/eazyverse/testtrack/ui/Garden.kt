package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import com.eazyverse.testtrack.ui.theme.tabular

/*
 * The pieces Value Guard is built from, drawn in Compose.
 *
 * Same shapes, same spacing and the same rules about where colour goes, so the two apps read as one
 * family: things you tap are solid tiles, things you only read are dashed panels, the accent is
 * spent on what you act on, and the ground under everything is a quiet gradient with app cards and exchange arcs.
 */

// ---- the ground ----------------------------------------------------------------------------

/** Which arrangement of app-card accents a tab uses. */
enum class Scene { CIRCLE, CREW, RANKS, APPS, PROFILE }

/** Shared TestTrack ground, replacing the old contour-map artwork. */
@Composable
fun Backdrop(
    scene: Scene,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.fillMaxSize().exchangeBackground(variant = scene.ordinal), content = content)
}

// ---- marks -----------------------------------------------------------------------------------

/**
 * An icon on its own tinted tile: the mark every row and header in Value Guard leads with.
 *
 * A rounded square rather than a circle, the glyph at about half the tile. The danger version
 * swaps to the red ground so a destructive row says so before its words are read.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    size: Dp = 36.dp,
    danger: Boolean = false,
) {
    val t = Tt.colors
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.305f))
            .background(if (danger) t.badBg else t.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = if (danger) t.bad else t.tileInk)
    }
}

/** A count in a small filled pill, beside a heading. */
@Composable
fun CountBadge(count: Int) {
    val t = Tt.colors
    Box(
        Modifier
            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
            .clip(CircleShape)
            .background(t.badgeFill)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = t.onAccent,
        )
    }
}

// ---- surfaces --------------------------------------------------------------------------------

val PanelRadius = 22.dp

/** Where a tile sits in its group, which decides which of its corners are round. */
enum class TilePlace { ONLY, FIRST, MIDDLE, LAST }

fun TilePlace.shape(): RoundedCornerShape {
    val outer = 22.dp
    val inner = 6.dp
    return when (this) {
        TilePlace.ONLY -> RoundedCornerShape(outer)
        TilePlace.FIRST -> RoundedCornerShape(outer, outer, inner, inner)
        TilePlace.MIDDLE -> RoundedCornerShape(inner)
        TilePlace.LAST -> RoundedCornerShape(inner, inner, outer, outer)
    }
}

/**
 * A run of tiles, three points apart, with no rules between them.
 *
 * Value Guard's settings groups. Each row is its own tile, only the outer corners of the run are
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

/**
 * One settings row: an icon tile, a title with an optional line under it, a value, a chevron.
 *
 * Sixty points at the least, so it takes a thumb comfortably. [danger] turns the title and the tile
 * red and drops the chevron; [trailing] replaces the chevron with anything else, a switch or an
 * external-link mark.
 */
@Composable
fun TileRow(
    icon: ImageVector,
    title: String,
    place: TilePlace,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    danger: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    /** A subtitle with styling in it, such as the gold stars in the rating row. Wins over [subtitle]. */
    subtitleRich: AnnotatedString? = null,
    onClick: (() -> Unit)? = null,
) {
    val t = Tt.colors
    val shape = place.shape()
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, danger = danger)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // Title and subtitle together are exactly as tall as the 36dp icon tile beside them: a
            // 20sp line and a 16sp one, with nothing between. Every row lines up the same way, and a
            // subtitle never runs to a second line and pushes its row taller than the others.
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (danger) t.bad else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = subtitleRich ?: subtitle?.let { AnnotatedString(it) }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(8.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = t.text3)
        }
        if (trailing != null) {
            Spacer(Modifier.width(6.dp))
            trailing()
        } else if (!danger && onClick != null) {
            Spacer(Modifier.width(6.dp))
            Icon(Lucide.ChevronRight, null, Modifier.size(18.dp), tint = t.text4)
        }
    }
}

/**
 * A section's heading: sentence case, one step under a title, with its count in a badge beside it.
 *
 * It was small grey capitals, which is a label, and a label over a list says "form" rather than
 * "here are your things". A count sits in a badge next to the words it counts, as Value Guard's
 * "Needs attention" does; anything that is not a count stays quiet text at the far end.
 */
@Composable
fun CrewHeading(
    text: String,
    trailing: String? = null,
    top: Dp = 20.dp,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = top, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text,
        )
        val count = trailing?.toIntOrNull()
        if (count != null) {
            Spacer(Modifier.width(5.dp))
            // Centred on the words and given no height of its own, so the heading is exactly as
            // tall as its words and they start where the first card on any other tab starts.
            Box(Modifier.height(19.dp).wrapContentHeight(Alignment.CenterVertically, unbounded = true)) {
                CountBadge(count)
            }
        }
        Spacer(Modifier.weight(1f))
        if (trailing != null && count == null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelMedium,
                color = Tt.colors.text3,
            )
        }
    }
}

/**
 * The top of a list that opens with a [CrewHeading]. Other tabs' lists start 16dp under the tabs,
 * at a card's edge; a heading's letters sit 4.5dp inside its line, so starting it 4.5dp higher
 * puts the words themselves on that 16dp line, with the count centred on them.
 */
val HeadedListTop = 11.5.dp

/** The corner a card gets. Value Guard's list cards, a step under its 22 panels. */
val CardRadius = 18.dp

/** One figure and what it counts. */
@Composable
fun Tally(
    modifier: Modifier,
    value: String,
    label: String,
) {
    Column(modifier) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Tt.colors.accentInk,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Row [index] of a list of [count], as a tile of its own.
 *
 * Every list that stands on the page ground is drawn this way, three points apart with only the
 * ends of the run rounded, so nothing is ever read across a contour line and a list looks like
 * Value Guard's rather than rows ruled off on the page.
 */
@Composable
fun ListTile(
    index: Int,
    count: Int,
    content: @Composable () -> Unit,
) {
    val t = Tt.colors
    val place =
        when {
            count <= 1 -> TilePlace.ONLY
            index == 0 -> TilePlace.FIRST
            index == count - 1 -> TilePlace.LAST
            else -> TilePlace.MIDDLE
        }
    val shape = place.shape()
    Box(
        Modifier
            .padding(start = Gutter, end = Gutter, top = if (index > 0) 3.dp else 0.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape),
    ) {
        content()
    }
}

/**
 * How much of the bottom of the screen the floating dock covers, system bar included.
 *
 * The page runs under the dock and the system bar rather than stopping above them, so a list scrolls
 * behind both the way Value Guard's does. Every list on a tab adds this to its bottom padding so its
 * last row can still be scrolled clear of them.
 */
val LocalDockRoom = compositionLocalOf { 0.dp }

/** [LocalDockRoom], read. */
@Composable
fun dockRoom(): Dp = LocalDockRoom.current
