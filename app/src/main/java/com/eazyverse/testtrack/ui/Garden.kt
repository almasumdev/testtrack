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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
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
 * The shared pieces the screens are built from.
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
 * An icon on its own tinted tile: the mark every row and header leads with.
 *
 * A rounded square rather than a circle, the glyph at about half the tile. [tone] colours the glyph
 * on the quiet tile; [filled] fills the tile with the tone instead, the glyph in the ink that reads
 * on it. The danger version swaps to the red ground so a destructive row says so before its words
 * are read.
 *
 * [corner] and [glyph] are 0.3 and 0.52 of [size] unless given, such as a 40dp tile's 20dp glyph or
 * a sheet's 36dp mark with its 12dp corner. [tint] is the glyph's ink and [ground] the tile's, in
 * place of the tone's, such as a gold stat.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    size: Dp = 36.dp,
    tone: Tone = Tone.ACCENT,
    filled: Boolean = false,
    danger: Boolean = false,
    corner: Dp = size * 0.3f,
    glyph: Dp = size * 0.52f,
    tint: Color? = null,
    ground: Color? = null,
) {
    val t = Tt.colors
    val toneInk = if (tone == Tone.ACCENT) t.tileInk else tone.ink()
    val (toneGround, toneGlyph) =
        when {
            danger -> t.badBg to t.bad
            filled -> tone.ink() to t.inkOn(tone.ink())
            else -> t.tile to toneInk
        }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(ground ?: toneGround),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(glyph), tint = tint ?: toneGlyph)
    }
}

/** A count in a small filled pill, beside a heading. Above [cap] it reads "cap+". */
@Composable
fun CountBadge(
    count: Int,
    cap: Int = Int.MAX_VALUE,
) {
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
            if (count > cap) "$cap+" else "$count",
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = t.onAccent,
        )
    }
}

// ---- surfaces --------------------------------------------------------------------------------

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

/**
 * One settings row: an icon tile, a title with an optional line under it, a value, a chevron.
 *
 * Sixty points at the least, so it takes a thumb comfortably. [danger] turns the title and the tile
 * red and drops the chevron; [trailing] replaces the chevron with anything else, a switch or an
 * external-link mark. [chosen] is a picker's chosen row, marked the one way every picker marks it:
 * the soft accent wash and a bolder title, with the check as its [trailing].
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
    /** Drawn instead of the icon tile, at its size: an app's own icon in a list of apps. */
    leading: (@Composable () -> Unit)? = null,
    chosen: Boolean = false,
    /** A small mark and a colour for the subtitle, such as the green tick on a ready row. */
    subtitleIcon: ImageVector? = null,
    subtitleTone: Color? = null,
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
            .then(if (chosen) Modifier.background(t.leafAccentSoft) else Modifier)
            .border(1.dp, t.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 14.dp, top = 8.dp, end = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else IconTile(icon, danger = danger)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            // Title and subtitle together are exactly as tall as the 36dp icon tile beside them: a
            // 20sp line and a 16sp one, with nothing between. Every row lines up the same way, and a
            // subtitle never runs to a second line and pushes its row taller than the others.
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
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
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
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
 * A section's heading on a tab's list, on the gutter: [SectionHeading] with a count in a badge
 * beside it, or anything that is not a count as quiet text at the far end.
 */
@Composable
fun CrewHeading(
    text: String,
    trailing: String? = null,
    top: Dp = 20.dp,
) {
    val count = trailing?.toIntOrNull()
    SectionHeading(
        text,
        Modifier.padding(horizontal = Gutter),
        count = count,
        top = top,
        end =
            if (trailing != null && count == null) {
                { Text(trailing, style = MaterialTheme.typography.labelMedium, color = Tt.colors.text3) }
            } else {
                null
            },
    )
}

/**
 * The top of a list that opens with a [CrewHeading]. Other tabs' lists start 16dp under the tabs,
 * at a card's edge; a heading's letters sit 4.5dp inside its line, so starting it 4.5dp higher
 * puts the words themselves on that 16dp line, with the count centred on them.
 */
val HeadedListTop = 11.5.dp

/** The corner a card gets: a step under the 22dp panels. */
val CardRadius = 18.dp

/** The Circle tile's corner: tickets, the ask row, every hairline tile. */
val TileRadius = 14.dp

/**
 * Row [index] of a list of [count], as a tile of its own.
 *
 * Every list that stands on the page ground is drawn this way: a hairline tile on the page ground,
 * all four corners rounded, eight points apart, like the Circle rows. [count] is kept so callers do
 * not change, but every row is shaped the same.
 */
@Composable
fun ListTile(
    index: Int,
    @Suppress("UNUSED_PARAMETER") count: Int,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .padding(start = Gutter, end = Gutter, top = if (index > 0) 8.dp else 0.dp)
            .fillMaxWidth()
            .tileFrame(),
    ) {
        content()
    }
}

/**
 * The hairline tile: the page colour inside a 1dp card line, clipped to [shape]. With [onClick] the
 * whole tile is the press shape, read out as [role]. Size and padding go before and after it as usual.
 *
 * [fill] is the ground inside the line: the page colour unless given, such as none at all
 * ([Color.Transparent]) for a frame drawn on a card, or the sheet's surface.
 */
@Composable
fun Modifier.tileFrame(
    shape: Shape = RoundedCornerShape(TileRadius),
    onClick: (() -> Unit)? = null,
    fill: Color = Tt.colors.bg,
    role: Role? = null,
): Modifier {
    val t = Tt.colors
    return this
        .clip(shape)
        .background(fill)
        .border(1.dp, t.cardLine, shape)
        .then(if (onClick != null) Modifier.clickable(role = role, onClick = onClick) else Modifier)
}

/**
 * One answer of a few side by side, such as the Ask page's Stars, Written and Detailed: a hairline
 * tile, or when [selected] the soft accent wash with no line, the way a picker marks its chosen row.
 * Picked as a radio button; untappable when not [enabled], and faded too unless not [fadeWhenOff].
 *
 * [fill] and [line] are the ground and the line while not chosen, [chosenFill] and [chosenLine]
 * once chosen, such as the review counts' bare unchosen chips or a two-answer question's tile
 * with its ink line.
 */
@Composable
fun Modifier.choiceTile(
    selected: Boolean,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(TileRadius),
    fill: Color = Tt.colors.bg,
    line: Color = Tt.colors.cardLine,
    chosenFill: Color = Tt.colors.leafAccentSoft,
    chosenLine: Color = Color.Transparent,
    fadeWhenOff: Boolean = true,
    onClick: () -> Unit,
): Modifier =
    this
        .clip(shape)
        .background(if (selected) chosenFill else fill)
        .border(1.dp, if (selected) chosenLine else line, shape)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .then(if (enabled || !fadeWhenOff) Modifier else Modifier.alpha(DISABLED_ALPHA))

/**
 * How much of the bottom of the screen the floating dock covers, system bar included.
 *
 * The page runs under the dock and the system bar rather than stopping above them, so a list scrolls
 * behind both. Every list on a tab adds this to its bottom padding so its
 * last row can still be scrolled clear of them.
 */
val LocalDockRoom = compositionLocalOf { 0.dp }

/** [LocalDockRoom], read. */
@Composable
fun dockRoom(): Dp = LocalDockRoom.current
