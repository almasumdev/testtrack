package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * A row of tabs that is nothing but words.
 *
 * Both tabbed screens draw their own, and they used to draw two copies of the same pill: a
 * container sized to its own words, with the selected one marked by filling it with
 * `primaryContainer` on `surface`, two colours three percent apart in lightness. One of them had
 * a third of its row left empty when the balance moved up into the header, and neither of them
 * needed a container at all now that the header above is one.
 *
 * Each label reserves its bold width, so selection can change weight without shifting its neighbours.
 *
 * Indexes rather than a type. Circle's three and Crew's four are different enums with nothing in
 * common but being a short list of words, and a generic over both would be more machinery than
 * either of them is worth.
 */
@Composable
fun WordTabs(
    labels: List<String>,
    selected: Int,
    modifier: Modifier = Modifier,
    /** Per tab, in the same order. Zero, or an absent entry, draws nothing. */
    counts: List<Int> = emptyList(),
    gap: Int = 20,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(gap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            WordTab(
                label = label,
                count = counts.getOrElse(index) { 0 },
                current = index == selected,
            ) { onSelect(index) }
        }
    }
}

@Composable
private fun WordTab(
    label: String,
    count: Int,
    current: Boolean,
    onSelect: () -> Unit,
) {
    val tint by animateColorAsState(
        targetValue =
            if (current) {
                Tt.colors.tabSelected
            } else {
                Tt.colors.tabUnselected
            },
        animationSpec = tween(durationMillis = 200),
        label = "tab",
    )
    val press = remember { MutableInteractionSource() }
    val textStyle = MaterialTheme.typography.titleMedium
    val measured = rememberTextMeasurer().measure(label, textStyle.copy(fontWeight = FontWeight.Bold))
    val labelWidth = with(LocalDensity.current) { measured.size.width.toDp() }

    Row(
        Modifier
            // The press is taken here and drawn further in. The whole of this is the target,
            // because a word is not one: the padding is what makes it the size of a thumb rather
            // than the size of the ink.
            //
            // The tab you are already on does not answer. Selecting it again does nothing but
            // flash, and a control that lights up without changing anything teaches people that
            // pressing things here is not reliable. `selectable` rather than `clickable` so taking
            // the click away does not take the announcement with it.
            .selectable(
                selected = current,
                enabled = !current,
                interactionSource = press,
                indication = null,
                role = Role.Tab,
                onClick = onSelect,
            ).padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            // Drawn on the letters and nowhere else. The count is left out on purpose: it is a
            // reading, not a button.
            modifier =
                Modifier.width(labelWidth).inkRipple(
                    interactionSource = press,
                    resting = tint,
                    lit = Tt.colors.accentInk,
                ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            TabCount(count)
        }
    }
}

/** How many things are sitting on this tab, or nothing at all when none are. */
@Composable
private fun TabCount(count: Int) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

/**
 * A press that spreads through the letters and nowhere else.
 *
 * Material's ripple fills a shape, so on a word it is a rounded rectangle lighting up behind the
 * text: the box flashes and the word sits on it. Bounding that box tightly to the text only makes
 * it a smaller box. What is wanted is the ink itself.
 *
 * The trick is the blend mode. The text is composited into its own layer, and the growing circle
 * is drawn over that layer with [BlendMode.SrcIn], which keeps the new colour only where the layer
 * was already opaque. The letterforms are the only opaque thing in it, so the circle is clipped to
 * the glyphs: counters, the gaps between letters and everything outside the word stay as they
 * were. [CompositingStrategy.Offscreen] is what makes that layer exist; without it the blend
 * reaches for whatever is behind the text and takes the background with it.
 *
 * The press is carried by moving the colour, never by thinning it. A translucent SrcIn leaves the
 * glyph itself translucent, and the ground here is nearly black, so fading ink into the letters
 * showed that ground through them and the word went darker at the moment it was meant to light up.
 *
 * It spreads from the middle of the word rather than from the finger. A press arrives in the
 * coordinates of whatever took the click, which is the whole tab including the padding that makes
 * it thumb sized, and carrying that offset across to be a few pixels more accurate about something
 * one word wide is arithmetic with nothing to show for it.
 */
@Composable
private fun Modifier.inkRipple(
    interactionSource: InteractionSource,
    resting: Color,
    lit: Color,
): Modifier {
    val spread = remember { Animatable(0f) }
    val strength = remember { Animatable(0f) }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    // Full the instant it is touched, then it grows. The other way round is a
                    // delay somebody reads as the tap not having registered.
                    strength.snapTo(1f)
                    spread.snapTo(0f)
                    launch {
                        spread.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing))
                    }
                }

                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    strength.animateTo(0f, tween(durationMillis = 260))
                }
            }
        }
    }

    return this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            if (strength.value > 0f) {
                // Far enough to cover the word from its middle, whatever its length.
                val reach = hypot(size.width, size.height) / 2f
                drawCircle(
                    color = lerp(resting, lit, strength.value),
                    radius = reach * spread.value,
                    center = center,
                    blendMode = BlendMode.SrcIn,
                )
            }
        }
}
