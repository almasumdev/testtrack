package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.roundToInt

/**
 * The clear strip above the dock's pill, inside what the shell measures. Left out of
 * [LocalDockRoom], so a list's last [Gutter] is measured from the pill a person can see.
 */
internal val DockTop = 6.dp

/**
 * The bar: Value Guard's floating dock.
 *
 * A pill that floats over the ground rather than a strip bolted to the bottom edge. Every tab but
 * the current one is just its icon; the current one is wider, with its name beside the icon, so
 * where you are is said once, in words, and the other four stay out of the way.
 *
 * One selection pill, not five. Each tab carries a 0..1 share of being selected, sprung from one
 * value to the other; widths, the pill's place and the name's reveal are all read off those
 * shares. On a switch the pill slides from the old tab to the new one and resizes on the way, and
 * the icons between glide to their new places.
 *
 * The row is drawn twice: once in the bar's ink, and once in the selected ink clipped to the
 * pill's exact outline, over the pill's own fill. So an icon is light exactly where the pill is
 * over it, to the pixel, including the ones it slides across. Colouring each icon by a guess at
 * where it sat left pale ghosts beside the pill mid-switch.
 *
 * No pressed highlight: a grey block flashed under the tapped icon before anything moved. The
 * pill starting to move is the answer to the tap, with a light haptic tick under it.
 */
@Composable
fun MainBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(32.dp)
    val shares =
        MainTab.entries.map { tab ->
            animateFloatAsState(
                if (tab == selected) 1f else 0f,
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                label = "tab-${tab.name}",
            )
        }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = DockInset, end = DockInset, top = DockTop, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .shadow(18.dp, shape, ambientColor = t.shadow.copy(alpha = 0.25f), spotColor = t.shadow.copy(alpha = 0.25f))
                .clip(shape)
                .background(t.dock)
                .border(1.dp, t.glassBorder, shape)
                .padding(4.dp),
        ) {
            if (maxWidth < Wide + 48.dp * 4 || LocalDensity.current.fontScale > 1.2f) {
                CompactBar(selected, onSelect)
                return@BoxWithConstraints
            }
            val slots = remember(maxWidth) { Slots(maxWidth, MainTab.entries.size) }
            val values = shares.map(State<Float>::value)
            val widths = values.map { slots.width(it) }
            val lefts = widths.runningFold(0.dp) { at, w -> at + w }

            // The pill: wherever the shares say, which mid-switch is part way between two tabs.
            val total = values.sum().coerceAtLeast(0.0001f)
            val pillLeft = lefts.zip(values).fold(0.dp) { at, (left, s) -> at + left * (s / total) }
            val pillWidth = widths.zip(values).fold(0.dp) { at, (w, s) -> at + w * (s / total) }

            // Underneath: the bar's ink, and the only layer that takes taps.
            BarRow(values, widths, t.dockInk, selected) { tab ->
                if (tab != selected) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(tab)
                }
            }

            // On top: the pill, and the same row in the selected ink, both cut to the pill.
            val density = LocalDensity.current
            val left = with(density) { pillLeft.toPx() }
            val right = left + with(density) { pillWidth.toPx() }
            val pill =
                GenericShape { size, _ ->
                    addRoundRect(RoundRect(left, 0f, right, size.height, CornerRadius(size.height / 2)))
                }
            Box(
                Modifier
                    .matchParentSize()
                    .clip(pill)
                    .background(t.selectedFill)
                    .clearAndSetSemantics {},
            ) {
                BarRow(values, widths, t.onSelected, selected = null, onSelect = null)
            }
        }
    }
}

@Composable
private fun CompactBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    val t = Tt.colors
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            selected.label,
            Modifier.padding(top = 6.dp, bottom = 2.dp),
            style = MaterialTheme.typography.labelLarge,
            color = t.dockInk,
        )
        Row(Modifier.fillMaxWidth()) {
            MainTab.entries.forEach { tab ->
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(if (tab == selected) t.selectedFill else t.dock)
                        .selectable(tab == selected, role = Role.Tab, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.icon,
                        tab.label,
                        Modifier.size(22.dp),
                        tint = if (tab == selected) t.onSelected else t.dockInk,
                    )
                }
            }
        }
    }
}

/**
 * The five tabs in a line, in one ink.
 *
 * Drawn twice by [MainBar] with identical layout, so the two copies line up exactly. Only the copy
 * given [onSelect] is interactive and announced; the other is a picture.
 */
@Composable
private fun BarRow(
    shares: List<Float>,
    widths: List<Dp>,
    ink: Color,
    selected: MainTab?,
    onSelect: ((MainTab) -> Unit)?,
) {
    Row(Modifier.height(BarHeight), verticalAlignment = Alignment.CenterVertically) {
        MainTab.entries.forEachIndexed { index, tab ->
            val share = shares[index]
            Row(
                Modifier
                    .width(widths[index])
                    .height(BarHeight)
                    .then(
                        if (onSelect != null) {
                            // `selectable` carries the selected state into the accessibility tree,
                            // and Tab is the role a screen reader announces for one of five.
                            Modifier.selectable(
                                selected = tab == selected,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Tab,
                                onClick = { onSelect(tab) },
                            )
                        } else {
                            Modifier
                        },
                    ).clipToBounds(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, if (tab == selected) null else tab.label, Modifier.size(22.dp), tint = ink)
                if (share > 0.02f) Name(tab.label, share, ink)
            }
        }
    }
}

/**
 * The current tab's name, taking its room as the tab grows into it.
 *
 * Its width is laid out in proportion to how far the reveal has got, so the icon beside it moves
 * over smoothly rather than jumping when the name arrives, and it fades in over the last stretch.
 */
@Composable
private fun Name(
    label: String,
    share: Float,
    ink: Color,
) {
    val reveal = ((share - 0.35f) / 0.65f).coerceIn(0f, 1f)
    Text(
        label,
        Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Int.MAX_VALUE))
                val gap = 8.dp.roundToPx()
                val room = ((placeable.width + gap) * reveal).roundToInt()
                layout(room, placeable.height) { placeable.placeRelative(gap, 0) }
            }.graphicsLayer { alpha = ((reveal - 0.4f) / 0.6f).coerceIn(0f, 1f) },
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
        fontWeight = FontWeight.SemiBold,
        color = ink,
        maxLines = 1,
        softWrap = false,
    )
}

/**
 * How wide a tab is for how selected it is.
 *
 * The current tab gets [Wide], room for "My apps" beside its icon; the other four share what is
 * left. Mid-switch the two tabs changing hands are in between and the widths still add up to the
 * bar, because the shares do.
 */
private class Slots(
    inner: Dp,
    count: Int,
) {
    private val wide = Wide.coerceAtMost(inner * 0.42f)
    private val narrow = (inner - wide) / (count - 1)

    fun width(share: Float): Dp = narrow + (wide - narrow) * share
}

private val Wide = 124.dp
private val BarHeight = 48.dp
private val DockInset = 20.dp
