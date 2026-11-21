package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Armchair
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Trophy
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's guide, under the crew that is filling: the how-to video as a row, what a crew is,
 * how the fortnight goes in four lines, and the rules card (closed to six fact tiles).
 *
 * Every number is the server's rule (CrewRules mirrors app/core/rules.py), so the page cannot
 * promise what the server will not do. What an account still needs before a seat is not here: it
 * sits in the crew box (CrewJoinChecklist.kt).
 */

/** The guide's parts, in the order somebody reads them. */
internal fun LazyListScope.crewGuide(runDays: Int = CrewRules.RUN_DAYS) {
    item(key = "video") { GuideRow { HowToCard(HowTo.CREW) } }
    item(key = "story") { GuideRow { CrewCard(padding = PaddingValues(16.dp)) { CrewStory() } } }
    item(key = "journey") { GuideRow { CrewCard(padding = PaddingValues(16.dp)) { CrewJourney() } } }
    item(key = "rules") { GuideRow { CrewCard(padding = PaddingValues(16.dp)) { CrewRulesCard(runDays) } } }
}

@Composable
private fun GuideRow(content: @Composable () -> Unit) {
    Box(Modifier.padding(horizontal = Gutter)) { content() }
}

// ------------------------------------------------------------------ what a crew is

@Composable
private fun CrewStory() {
    SmallHeading("What a crew is", top = 0.dp)
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.accentInk)
    Text(
        buildAnnotatedString {
            withStyle(strong) { append("${CrewRules.SEATS} developers") }
            append(" put their apps in and open each other's ")
            withStyle(strong) { append("every day for ${CrewRules.RUN_DAYS} days") }
            append(". Everyone gets real testers on real phones.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = Tt.colors.text2,
    )
    Text(
        "Google asks for ${CrewRules.FLOOR} testers over ${CrewRules.RUN_DAYS} days before production. A crew is " +
            "${CrewRules.SEATS}, so it still counts if a couple drop out.",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = Tt.colors.text2,
    )
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
    SmallHeading("How it goes", top = 0.dp)
    val steps =
        listOf(
            Step(Lucide.Armchair, "Fill up", "Wait in the Lobby until all ${CrewRules.SEATS} are in"),
            Step(Lucide.Download, "Install the other ${CrewRules.NIGHTLY}", "From Play, while the crew fills"),
            Step(Lucide.Play, "Open them every day", "${CrewRules.OPEN_SECONDS} seconds each, for ${CrewRules.RUN_DAYS} days"),
            Step(Lucide.Trophy, "Apply for production", "Your app has its testers"),
        )
    steps.forEachIndexed { index, step -> JourneyRow(step, first = index == 0) }
}

private val JourneyTitle = AppTileName
private val JourneyNote = tileStyle(12.5f, 15f, FontWeight.Normal)

@Composable
private fun JourneyRow(
    step: Step,
    first: Boolean,
) {
    val t = Tt.colors
    Row(Modifier.padding(top = if (first) 0.dp else 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(step.icon, 40.dp, glyph = 20.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(step.title, style = JourneyTitle, color = t.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(step.line, style = JourneyNote, color = t.text3, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
