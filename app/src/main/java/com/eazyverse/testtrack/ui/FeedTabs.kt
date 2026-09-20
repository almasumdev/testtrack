package com.eazyverse.testtrack.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
                    // Unbounded and centred, rather than a rounded rectangle drawn tight around a
                    // word. A box appearing behind text reads as a box appearing, not as a press.
                    .clickable(
                        interactionSource = press,
                        indication = ripple(bounded = false, radius = 34.dp),
                    ) { onSelect(tab) }
                    // A word is not a target. The padding is what makes this the size of a thumb
                    // rather than the size of the ink.
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tab.label,
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
