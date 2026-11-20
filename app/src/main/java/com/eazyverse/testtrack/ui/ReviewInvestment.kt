package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.roundToInt

/*
 * How many reviews a request buys and what each pays, on the Ask page. The count is a stepper at
 * the end of its heading with the common counts under it; the pay is a slider in fives from the
 * least the chosen level pays (FeedRules.Feedback) to the most.
 */

private const val TABULAR = "tnum"
private val StepValue = tileStyle(18f, 22f, FontWeight.Bold).copy(fontFeatureSettings = TABULAR)
private val ChipText = tileStyle(14f, 17f, FontWeight.SemiBold).copy(fontFeatureSettings = TABULAR)
private val ChipTextChosen = tileStyle(14f, 17f, FontWeight.Bold).copy(fontFeatureSettings = TABULAR)
private val PayValue = tileStyle(16f, 19f, FontWeight.Bold).copy(fontFeatureSettings = TABULAR)
private val EndLabel = tileStyle(12f, 14f, FontWeight.Normal).copy(fontFeatureSettings = TABULAR)

/** The counts most requests pick, one tap each. */
private val CommonCounts = listOf(3, 5, 10, 20, 50)

/** How far the slider reaches past the page's edges, so its track runs 2dp in from each, as drawn. */
private val SliderReach = 8.dp

/**
 * The Reviews heading, its stepper at the end, and the common counts as chips under it. The row is
 * the stepper's 36dp with the heading centred on it, so the page's 24 above and the heading's 10
 * below stay clear of the buttons.
 */
@Composable
internal fun AskCount(
    reviews: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    SeatHeading("Reviews", gap = 10.dp, end = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StepButton(Lucide.Minus, "Fewer reviews", enabled && reviews > FeedRules.MIN_REVIEWS) { onChange(reviews - 1) }
            Text(
                "$reviews",
                Modifier.widthIn(min = 34.dp),
                style = StepValue,
                color = Tt.colors.text,
                textAlign = TextAlign.Center,
            )
            StepButton(Lucide.Plus, "More reviews", enabled && reviews < FeedRules.MAX_REVIEWS) { onChange(reviews + 1) }
        }
    })
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CommonCounts.forEach { count ->
            CountChip(count, reviews == count, enabled, { onChange(count) }, Modifier.weight(1f))
        }
    }
}

/** Measures [by] wider on each side than it is given and centres on its slot, past both edges. */
private fun Modifier.reach(by: Dp): Modifier =
    layout { measurable, constraints ->
        val extra = 2 * by.roundToPx()
        val wide = constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra)
        val placeable = measurable.measure(wide)
        layout(placeable.width - extra, placeable.height) { placeable.place(-extra / 2, 0) }
    }

@Composable
private fun StepButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(t.tile)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            label,
            Modifier.size(18.dp).then(if (enabled) Modifier else Modifier.alpha(0.5f)),
            tint = if (enabled) t.accentInk else t.text4,
        )
    }
}

@Composable
private fun CountChip(
    count: Int,
    chosen: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(36.dp)
            .clip(shape)
            .background(if (chosen) t.leafAccentSoft else Color.Transparent)
            .border(1.dp, if (chosen) Color.Transparent else t.cardLine, shape)
            .selectable(selected = chosen, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$count reviews" }
            .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = if (chosen) ChipTextChosen else ChipText,
            color = if (chosen) t.tabSelected else t.text2,
        )
    }
}

/**
 * The Each review pays heading with the pay and the ticket at its end, then the slider in fives
 * from [least] to the most, its two ends under it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AskPay(
    reward: Int,
    least: Int,
    enabled: Boolean,
    onChange: (Int) -> Unit,
) {
    val t = Tt.colors
    // The page's ticket, as the feed's: on the card colour and tipped back 12 degrees.
    val ticket = rememberTicketMark(t.card)
    SectionHeading("Each review pays", end = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(ticket, null, Modifier.size(16.dp).rotate(-12f), tint = Color.Unspecified)
            Spacer(Modifier.width(6.dp))
            Text("$reward tix", style = PayValue, color = t.accentInk)
        }
    })
    Slider(
        value = reward.toFloat(),
        // In fives from the least, so 35 for written feedback is a stop of its own.
        onValueChange = { onChange((it / 5).roundToInt() * 5) },
        valueRange = least.toFloat()..FeedRules.MAX_REWARD.toFloat(),
        steps = (FeedRules.MAX_REWARD - least) / 5 - 1,
        enabled = enabled,
        // A 20dp round thumb on a 4dp track, as the page's design draws it, not the stock bar.
        thumb = {
            Box(Modifier.size(20.dp).shadow(2.dp, CircleShape).background(t.primary, CircleShape))
        },
        track = { state ->
            val span = state.valueRange.endInclusive - state.valueRange.start
            val done = if (span > 0f) ((state.value - state.valueRange.start) / span).coerceIn(0f, 1f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(t.track),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(done)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(t.primary),
                )
            }
        },
        modifier =
            Modifier
                .fillMaxWidth()
                .reach(SliderReach)
                .height(36.dp)
                .then(if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA))
                .semantics { contentDescription = "Tix each review pays" },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("$least", style = EndLabel, color = t.text3)
        Text("${FeedRules.MAX_REWARD}", style = EndLabel, color = t.text3)
    }
}
