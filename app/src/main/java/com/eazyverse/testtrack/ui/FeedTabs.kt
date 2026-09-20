package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Circle's three tabs, as three words.
 *
 * No container and no fill. The pill this replaces was sized to its own words, which left a third
 * of the row empty once the balance moved up into the header, and it marked the selected tab with
 * `primaryContainer` on `surface`: #1C1D3A on #101626, three percent apart in lightness, the same
 * smudge the bottom bar was marking its selected tab with. Weight and colour do it instead, which
 * cannot be too low contrast because it is the text itself.
 *
 * It also stops the top of this screen being three rounded containers stacked on each other, now
 * that the header above is one.
 *
 * [waiting] is the only number here, and it is the one thing on the screen genuinely owed: reviews
 * of this account's apps, and cases it can help decide. Shown from whichever tab you are on rather
 * than only from Waiting, because it is an alert, and an alert you can only see once you have gone
 * looking is not one.
 */
@Composable
fun FeedTabs(
    selected: FeedTab,
    modifier: Modifier = Modifier,
    waiting: Int = 0,
    onSelect: (FeedTab) -> Unit,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedTab.entries.forEach { tab ->
            val current = tab == selected
            val tint by animateColorAsState(
                targetValue =
                    if (current) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                animationSpec = tween(durationMillis = 200),
                label = "tab",
            )

            /*
             * One weight for all three, and the colour carries the selection on its own.
             *
             * Bold is wider than medium, so switching used to re-measure the word, which moved
             * every tab after it and the badge on the end. That is what the switch looked wrong
             * for: not the fade, but the row jumping sideways under the finger that tapped it.
             * Nothing here changes width any more, so the only thing that happens is the colour.
             */
            val press = remember { MutableInteractionSource() }

            Row(
                Modifier
                    // The press is taken here and drawn further in. The whole of this is the
                    // target, because a word is not one: the padding is what makes it the size of
                    // a thumb rather than the size of the ink.
                    .clickable(
                        interactionSource = press,
                        indication = null,
                    ) { onSelect(tab) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.label,
                    // Drawn on the letters and nowhere else. The count is left out on purpose:
                    // it is a reading, not a button.
                    modifier = Modifier.inkRipple(press, MaterialTheme.colorScheme.primary),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tint,
                    maxLines = 1,
                )
                if (tab == FeedTab.WAITING && waiting > 0) {
                    Spacer(Modifier.width(6.dp))
                    WaitingCount(waiting)
                }
            }
        }
    }
}

/** How many things are sitting on this account, or nothing at all when none are. */
@Composable
private fun WaitingCount(count: Int) {
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
 * text: the box flashes and the word sits on it. What is wanted here is the ink itself.
 *
 * The trick is the blend mode. The text is composited into its own layer, and the growing circle
 * is then drawn over that layer with [BlendMode.SrcIn], which keeps the new colour only where the
 * layer already had something opaque. The letterforms are the only opaque thing in it, so the
 * circle is clipped to the glyphs: counters, the gap between letters and everything outside the
 * word stay as they were. [CompositingStrategy.Offscreen] is what makes that layer exist; without
 * it the blend would reach for whatever is behind the text and take the background with it.
 *
 * It spreads from the middle of the word rather than from the finger. A press is reported in the
 * coordinates of whatever took the click, which is the whole tab including the padding that makes
 * it thumb sized, and carrying that offset across to here to be a few pixels more accurate about
 * something a word wide is not worth the arithmetic it would take to get wrong.
 */
@Composable
private fun Modifier.inkRipple(
    interactionSource: InteractionSource,
    color: Color,
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
                    launch { spread.animateTo(1f, tween(durationMillis = 340, easing = FastOutSlowInEasing)) }
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
                val reach = kotlin.math.hypot(size.width, size.height) / 2f
                drawCircle(
                    color = color.copy(alpha = 0.75f * strength.value),
                    radius = reach * spread.value,
                    center = center,
                    blendMode = BlendMode.SrcIn,
                )
            }
        }
}
