package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Armchair
import com.composables.icons.lucide.CalendarDays
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.LogOut
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Star
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's rules card: a soft band, four figures over its edge, a sample fortnight, where
 * the seat's tix go, and three things worth knowing. Split from CrewOpenGuide.kt.
 */

@Composable
internal fun CrewRulesCard() {
    // A soft band at the head, with the four figures sitting over its lower edge.
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Tt.colors.tile, Tt.colors.glass)))
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 44.dp),
    ) {
        Eyebrow(Lucide.Scale, "The rules")
        Text(
            "What you agree to",
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
        )
        Text("The same for all ${CrewRules.SEATS} apps in the crew.", style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
    }
    Column(Modifier.overlapUp(30).padding(start = 14.dp, end = 14.dp, bottom = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure(Lucide.Armchair, Tone.ACCENT, "${CrewRules.SEAT_COST}", "tix", "A seat")
            Figure(Lucide.Star, Tone.OK, "+1", "point", "Every app you open")
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure(Lucide.TriangleAlert, Tone.WARN, "1", "day", "Missed: a warning")
            Figure(Lucide.LogOut, Tone.BAD, "2", "in a row", "Missed: your app leaves")
        }

        RulesHeading("Your ${CrewRules.RUN_DAYS} days")
        DayStrip()

        RulesHeading("Your ${CrewRules.SEAT_COST} tix")
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TixCard(
                Lucide.RotateCcw,
                Tone.OK,
                "Come back",
                listOf("Back in full if you take it out while the crew fills", "Or if ${CrewRules.REPORTS_TO_RETURN} reports send it back"),
            )
            TixCard(Lucide.Lock, Tone.BAD, "Kept", listOf("Once the crew is full", "Or if it leaves for missed days"))
        }

        RulesHeading("Good to know")
        GoodToKnow(1, Lucide.CalendarDays, "A day runs from midnight, your time.")
        GoodToKnow(2, Lucide.Info, "An open counts after ${CrewRules.VISIT_SECONDS} seconds in front.")
        GoodToKnow(3, Lucide.Hourglass, "One app waiting at a time.")
    }
}

/** Drawn [dp] higher and that much shorter, so the figures sit over the band without a gap below. */
private fun Modifier.overlapUp(dp: Int) =
    layout { measurable, constraints ->
        val shift = dp.dp.roundToPx()
        val placeable = measurable.measure(constraints)
        layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
    }

@Composable
private fun RulesHeading(text: String) {
    Text(
        text.uppercase(),
        Modifier.padding(start = 2.dp, top = 18.dp, bottom = 8.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.7.sp),
        fontWeight = FontWeight.Bold,
        color = Tt.colors.text4,
    )
}

/** One of the four figures: white, with its colour only on the icon and the number. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.Figure(
    icon: ImageVector,
    tone: Tone,
    value: String,
    unit: String,
    label: String,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .weight(1f)
            .clip(shape)
            .background(Tt.colors.field)
            .border(1.dp, Tt.colors.glassBorder, shape)
            .padding(12.dp),
    ) {
        IconSquare(icon, tone, size = 30)
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 25.sp, lineHeight = 26.sp),
                fontWeight = FontWeight.ExtraBold,
                color = tone.ink(),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                Modifier.padding(bottom = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Tt.colors.text4,
            )
        }
        Text(label, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
    }
}

/** A made-up fortnight, eight days in, with one day missed, to show what a warning is. */
@Composable
private fun DayStrip() {
    val opened = Status.posted.copy(alpha = 0.75f)
    val warned = Status.short.copy(alpha = 0.7f)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Tt.colors.glass2)
            .padding(12.dp),
    ) {
        (0 until 2).forEach { week ->
            Row(Modifier.padding(top = if (week == 0) 0.dp else 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                (1..7).forEach { d ->
                    val day = week * 7 + d
                    val fill =
                        when {
                            day == 4 -> warned
                            day <= 8 -> opened
                            else -> null
                        }
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(fill ?: Tt.colors.field)
                            .then(if (fill == null) Modifier.border(1.dp, Tt.colors.glassBorder, RoundedCornerShape(8.dp)) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "$day",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (fill != null) Tt.colors.inkOn(fill) else Tt.colors.text4,
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Legend(opened, "All opened")
            Legend(warned, "Missed: warning")
            Legend(Status.missed, "2nd in a row: out")
        }
    }
}

@Composable
private fun Legend(
    color: androidx.compose.ui.graphics.Color,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = Tt.colors.text2)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TixCard(
    icon: ImageVector,
    tone: Tone,
    title: String,
    lines: List<String>,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(shape)
            .background(Tt.colors.field)
            .border(1.dp, Tt.colors.glassBorder, shape)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(15.dp), tint = tone.ink())
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = tone.ink())
        }
        lines.forEach { line ->
            Text(line, Modifier.padding(top = 5.dp), style = MaterialTheme.typography.bodySmall, color = Tt.colors.text2)
        }
    }
}

@Composable
private fun GoodToKnow(
    number: Int,
    icon: ImageVector,
    text: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).background(Tt.colors.tile),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Tt.colors.accentInk)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = Tt.colors.text2)
        Icon(icon, null, Modifier.size(16.dp), tint = Tt.colors.text4)
    }
}
