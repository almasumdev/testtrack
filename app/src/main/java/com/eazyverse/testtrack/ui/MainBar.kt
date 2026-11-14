package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.PI
import kotlin.math.sin

/** The bar starts at its top line; lists reserve its entire measured height. */
internal val DockTop = 0.dp

/**
 * Quiet tile: four named tabs and one Add action on the full-width ground.
 *
 * The centre tile is a button, never a selected page. Ranks belongs to You, which stays marked
 * while its ranks page is open. The whole row grows with text, and each slot keeps a thumb's room.
 */
@Composable
fun MainBar(
    selected: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier,
    /** What waits on this account, by tab; a tab missing or at 0 shows no number. */
    counts: Map<MainTab, Int> = emptyMap(),
    onAdd: () -> Unit = {},
) {
    val t = Tt.colors
    val haptic = LocalHapticFeedback.current
    val active = if (selected == MainTab.RANKS) MainTab.PROFILE else selected
    val tabs = remember { listOf(MainTab.CIRCLE, MainTab.GROUPS, null, MainTab.APPS, MainTab.PROFILE) }
    val movement = updateTransition(tabs.indexOf(active), label = "tab-movement")
    val selection by movement.animateFloat(
        transitionSpec = {
            if (initialState.crossesAdd(targetState)) {
                tween(720, easing = LinearEasing)
            } else {
                spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 500f)
            }
        },
        label = "tab-selection",
    ) { it.toFloat() }
    Column(modifier.fillMaxWidth().background(t.bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.cardLine))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(
                Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .drawBehind {
                        val slot = size.width / tabs.size
                        val rtl = layoutDirection == LayoutDirection.Rtl
                        val from = movement.currentState
                        val to = movement.targetState
                        val crossing = from.crossesAdd(to)
                        val progress = if (crossing) ((selection - from) / (to - from)).coerceIn(0f, 1f) else 0f
                        // Compress before takeoff, then fly at a steady horizontal speed under
                        // constant gravity. Reserve the last beat for a grounded landing squash.
                        val flight = ((progress - 0.12f) / 0.76f).coerceIn(0f, 1f)
                        val travel = if (crossing) from + (to - from) * flight else selection
                        val position = if (rtl) tabs.lastIndex - travel else travel
                        val lift = 4f * 64.dp.toPx() * flight * (1f - flight)
                        val squash = when {
                            progress < 0.12f -> sin(progress / 0.12f * PI).toFloat() * 0.18f
                            progress > 0.88f -> sin((progress - 0.88f) / 0.12f * PI).toFloat() * 0.22f
                            else -> 0f
                        }
                        val stretch = sin(flight * PI).toFloat()
                        val direction = (if (to > from) 1f else -1f) * (if (rtl) -1f else 1f)
                        val centre = Offset(slot * (position + 0.5f), 22.dp.toPx() - lift + 16.dp.toPx() * squash)
                        rotate(360f * flight * direction, pivot = centre) {
                            scale(1f + squash - 0.04f * stretch, 1f - squash + 0.08f * stretch, pivot = centre) {
                                drawRoundRect(
                                    color = t.leafAccentSoft,
                                    topLeft = centre - Offset(26.dp.toPx(), 16.dp.toPx()),
                                    size = Size(52.dp.toPx(), 32.dp.toPx()),
                                    cornerRadius = CornerRadius(8.dp.toPx()),
                                )
                            }
                        }
                    }
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    if (tab == null) {
                        val source = remember { MutableInteractionSource() }
                        val pressed by source.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            if (pressed) 0.92f else 1f,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
                            label = "add-press",
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 64.dp)
                                .padding(horizontal = 4.dp)
                                .clickable(
                                    interactionSource = source,
                                    indication = null,
                                    role = Role.Button,
                                    onClickLabel = "Open Add menu",
                                    onClick = onAdd,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(width = 48.dp, height = 36.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }.clip(RoundedCornerShape(11.dp))
                                    .background(t.accent),
                                contentAlignment = Alignment.Center,
                            ) { Icon(Lucide.Plus, "Add", Modifier.size(24.dp), tint = t.onAccent) }
                        }
                    } else {
                        val chosen = tab == active
                        val count = counts[tab] ?: 0
                        val source = remember { MutableInteractionSource() }
                        val pressed by source.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            if (pressed) 0.92f else 1f,
                            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 700f),
                            label = "tab-press-${tab.name}",
                        )
                        val ink by animateColorAsState(
                            if (chosen) t.tabSelected else t.tabUnselected,
                            tween(180),
                            label = "tab-ink-${tab.name}",
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 64.dp)
                                .selectable(
                                    selected = chosen,
                                    interactionSource = source,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (tab != selected) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onSelect(tab)
                                        }
                                    },
                                ).semantics(mergeDescendants = true) {
                                    contentDescription = if (count > 0) "${tab.label}, $count to do" else tab.label
                                }.padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            // The shared ground slides underneath; only the pressed icon scales.
                            Box(
                                Modifier
                                    .size(width = 52.dp, height = 32.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }.background(
                                        if (pressed) t.leafAccentSoft else Color.Transparent,
                                        RoundedCornerShape(8.dp),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) { TabIcon(tab, count, ink, null) }
                            Text(
                                tab.label,
                                Modifier.padding(horizontal = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                                color = ink,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The centre slot is an action; changing sides takes the selection over it. */
private fun Int.crossesAdd(other: Int): Boolean = (this < 2 && other > 2) || (this > 2 && other < 2)

/**
 * A tab's icon, and over its corner how many things on it wait for this account.
 *
 * The count is the primary colour, ringed in the dock's own colour so it lifts off the icon under
 * it and off the selected pill. Said with the tab's name for a screen reader.
 */
@Composable
private fun TabIcon(
    tab: MainTab,
    count: Int,
    tint: Color,
    description: String?,
) {
    Box {
        Icon(
            tab.icon,
            description?.let { if (count > 0) "$it, $count to do" else it },
            Modifier.size(22.dp),
            tint = tint,
        )
        if (count > 0) {
            val shape = RoundedCornerShape(percent = 50)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 10.dp, y = (-6).dp)
                    .clip(shape)
                    .background(Tt.colors.bg)
                    .padding(1.5.dp)
                    .clip(shape)
                    .background(Tt.colors.primary)
                    .padding(horizontal = 4.5.dp, vertical = 0.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (count > 99) "99+" else "$count",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    fontWeight = FontWeight.Bold,
                    color = Tt.colors.onPrimary,
                )
            }
        }
    }
}
