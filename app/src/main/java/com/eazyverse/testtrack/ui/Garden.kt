package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Check
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

/** How big a shared chip or badge is drawn: its usual size, or the smaller one for a corner. */
enum class PartSize { REGULAR, SMALL }

/**
 * A count in a small filled pill, beside a heading or in the bar. Above [cap] it reads "cap+".
 *
 * [PartSize.SMALL] is the one on a button's corner: 18dp at the least, 10.5 bold, with a 1.5dp
 * [ring] in the ground under it so it stands clear of the button.
 */
@Composable
fun CountBadge(
    count: Int,
    cap: Int = Int.MAX_VALUE,
    size: PartSize = PartSize.REGULAR,
    ring: Color? = null,
    modifier: Modifier = Modifier,
) {
    CountBadge(if (count > cap) "$cap+" else "$count", size, ring, modifier)
}

/** A [CountBadge] whose words are already written, such as "9+". */
@Composable
fun CountBadge(
    text: String,
    size: PartSize = PartSize.REGULAR,
    ring: Color? = null,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val small = size == PartSize.SMALL
    val least = if (small) 18.dp else 22.dp
    Box(
        modifier
            .defaultMinSize(minWidth = least, minHeight = least)
            .clip(CircleShape)
            .background(t.badgeFill)
            .then(if (ring != null) Modifier.border(1.5.dp, ring, CircleShape) else Modifier)
            .padding(horizontal = if (small) 4.dp else 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = if (small) TextStyles.SmallBadge else MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = t.onAccent,
        )
    }
}

// ---- surfaces --------------------------------------------------------------------------------

/**
 * Fades the top [edge] of a scrolled list into the page, so a row slides out under the bar rather
 * than being cut by it. Only once the list has moved: at the top nothing is faded.
 */
@Composable
fun Modifier.scrollEdgeFade(
    state: LazyListState,
    edge: Dp = 22.dp,
): Modifier {
    // Any opaque ink will do: the mask keeps the list where it is opaque and nothing of its colour.
    val ink = Tt.colors.bg
    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (state.canScrollBackward) {
                val height = edge.toPx()
                drawRect(
                    Brush.verticalGradient(0f to ink.copy(alpha = 0f), 1f to ink, startY = 0f, endY = height),
                    size = Size(size.width, height),
                    blendMode = BlendMode.DstIn,
                )
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
 * One answer of a few side by side, drawn on [choiceTile]: an 18dp icon, the [name] and a quiet
 * [line] under it, 12 inside. Chosen, the name goes bold in the selected ink and a check sits 11
 * from the top right corner. [dim] fades it once another answer is the one that counts; [fadeWhenOff]
 * false keeps it at full strength while not [enabled], such as a vote that is being sent.
 */
@Composable
fun ChoiceTile(
    icon: ImageVector,
    name: String,
    line: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    dim: Boolean = false,
    fadeWhenOff: Boolean = true,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Box(
        modifier
            .alpha(if (dim && !selected) 0.45f else 1f)
            .choiceTile(selected, enabled, fadeWhenOff = fadeWhenOff, onClick = onClick),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = t.tileInk)
            Spacer(Modifier.height(6.dp))
            Text(
                name,
                style = ChoiceName,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) t.tabSelected else t.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(line, style = ChoiceLine, color = t.text3)
        }
        if (selected) {
            Icon(
                Lucide.Check,
                null,
                Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 11.dp).size(15.dp),
                tint = t.tabSelected,
            )
        }
    }
}

private val ChoiceName = tileStyle(15f, 18f, FontWeight.SemiBold)
private val ChoiceLine = tileStyle(12f, 15f, FontWeight.Normal)

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
