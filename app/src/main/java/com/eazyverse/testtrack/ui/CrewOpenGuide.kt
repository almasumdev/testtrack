package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Armchair
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Scale
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's guide, under the crew that is filling: the how-to video as a row, what a crew is,
 * how the fortnight goes in four lines, and the rules behind a row of their own.
 *
 * Every number is the server's rule (CrewRules mirrors app/core/rules.py), so the page cannot
 * promise what the server will not do. What an account still needs before a seat is not here: it
 * sits in the crew box (CrewJoinChecklist.kt).
 */

/** The guide's parts, in the order somebody reads them. [onRules] opens the rules card as its page. */
internal fun LazyListScope.crewGuide(onRules: () -> Unit = {}) {
    item(key = "video") { GuideRow { HowToCard(HowTo.CREW) } }
    item(key = "story") { GuideRow { CrewCard(padding = PaddingValues(16.dp)) { CrewStory() } } }
    item(key = "journey") { GuideRow { CrewCard(padding = PaddingValues(16.dp)) { CrewJourney() } } }
    item(key = "rules") {
        GuideRow {
            AskRow("The rules", "What you agree to, with the numbers", onRules, icon = Lucide.Scale)
        }
    }
}

@Composable
private fun GuideRow(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = Gutter)) { content() }
}

/** A section's small capital label, with its icon when it has one. */
@Composable
internal fun Eyebrow(
    icon: ImageVector?,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(14.dp), tint = Tt.colors.text4)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, letterSpacing = 0.8.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text4,
        )
    }
}

// ------------------------------------------------------------------ what a crew is

@Composable
private fun CrewStory() {
    Eyebrow(Lucide.Users, "What a crew is")
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.accentInk)
    Text(
        buildAnnotatedString {
            withStyle(strong) { append("${CrewRules.SEATS} developers") }
            append(" put their apps in and open each other's ")
            withStyle(strong) { append("every day for ${CrewRules.RUN_DAYS} days") }
            append(". Everyone gets real testers on real phones.")
        },
        Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = Tt.colors.text2,
    )
    Text(
        "Google asks for ${CrewRules.FLOOR} testers over ${CrewRules.RUN_DAYS} days before production. A crew is " +
            "${CrewRules.SEATS}, so it still counts if a couple drop out.",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = Tt.colors.text2,
    )
}

@Composable
internal fun IconSquare(
    icon: ImageVector,
    tone: Tone,
    filled: Boolean = false,
    size: Int = 36,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(if (filled) tone.ink() else Tt.colors.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint = if (filled) Tt.colors.inkOn(tone.ink()) else tone.ink())
    }
}

// ------------------------------------------------------------------ how it goes

private class Step(
    val icon: ImageVector,
    val title: String,
    val line: String,
)

/** Four steps, each one line under its name beside a 40dp tile, so the text is never taller than the icon. */
@Composable
private fun CrewJourney() {
    Eyebrow(null, "How it goes")
    val steps =
        listOf(
            Step(Lucide.Armchair, "Fill up", "Wait in the Lobby until all ${CrewRules.SEATS} are in"),
            Step(Lucide.Download, "Install the other ${CrewRules.NIGHTLY}", "From Play, while the crew fills"),
            Step(Lucide.Play, "Open them every day", "${CrewRules.OPEN_SECONDS} seconds each, for ${CrewRules.RUN_DAYS} days"),
            Step(Lucide.Trophy, "Apply for production", "Your app has its testers"),
        )
    steps.forEachIndexed { index, step -> JourneyRow(step, first = index == 0) }
}

private val JourneyTitle = tileStyle(16f, 19f, FontWeight.SemiBold)
private val JourneyNote = tileStyle(12.5f, 15f, FontWeight.Normal)

@Composable
private fun JourneyRow(
    step: Step,
    first: Boolean,
) {
    val t = Tt.colors
    Row(Modifier.padding(top = if (first) 10.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.tile),
            contentAlignment = Alignment.Center,
        ) {
            Icon(step.icon, null, Modifier.size(20.dp), tint = t.tileInk)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = JourneyTitle, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(step.line, style = JourneyNote, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
