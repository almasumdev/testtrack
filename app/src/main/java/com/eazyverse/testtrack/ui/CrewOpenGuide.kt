package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Armchair
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.ListChecks
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Route
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Trophy
import com.composables.icons.lucide.UserPlus
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Open tab's guide, under the crew that is filling: the how-to video, what a crew is, what to
 * set up before taking a seat, how the fortnight goes, and the rules with their numbers.
 *
 * Every number is the server's rule (CrewRules mirrors app/core/rules.py), so the page cannot
 * promise what the server will not do. Colour sits on the icons and the figures only; the cards
 * themselves are the same glass as everything else on the tab.
 */

/** What is already in place for taking a seat. [app] is null until My apps has answered. */
data class CrewReady(
    val app: Boolean?,
    val member: Boolean,
    val permissions: Boolean,
) {
    val done: Int get() = listOf(app == true, member, permissions).count { it }
    val all: Boolean get() = done == 3
}

/** The guide's sections, each its own card, in the order somebody reads them. */
internal fun androidx.compose.foundation.lazy.LazyListScope.crewGuide(
    ready: CrewReady,
    hasApps: Boolean,
    onApp: () -> Unit,
    onJoin: () -> Unit,
    onAllow: () -> Unit,
) {
    item(key = "video") { GuideCard { HowToBody(HowTo.CREW) } }
    item(key = "story") { GuideCard { CrewStory() } }
    if (!ready.all) {
        item(key = "ready") { GuideCard { CrewGetReady(ready, hasApps, onApp, onJoin, onAllow) } }
    }
    item(key = "journey") { GuideCard { CrewJourney() } }
    item(key = "rules") { GuideCard(padding = PaddingValues(0.dp)) { CrewRulesCard() } }
}

@Composable
private fun GuideCard(
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Box(Modifier.padding(start = Gutter, end = Gutter, top = 12.dp)) {
        CrewCard(padding = padding) { content() }
    }
}

/** A section's small capital label, with its icon. */
@Composable
internal fun Eyebrow(
    icon: ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = Tt.colors.text4)
        Spacer(Modifier.width(6.dp))
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
            append(". Everyone gets real testers on real phones, and nobody waits for strangers.")
        },
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        color = Tt.colors.text2,
    )
    Text(
        "Google asks for ${CrewRules.FLOOR} testers over ${CrewRules.RUN_DAYS} days before production. A crew is " +
            "${CrewRules.SEATS}, so it still counts if a couple drop out.",
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp),
        color = Tt.colors.text2,
    )
}

// ------------------------------------------------------------------ get ready

@Composable
private fun CrewGetReady(
    ready: CrewReady,
    hasApps: Boolean,
    onApp: () -> Unit,
    onJoin: () -> Unit,
    onAllow: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { Eyebrow(Lucide.ListChecks, "Get ready") }
        StatusChip("${ready.done} of 3", Tone.OK)
    }
    Text(
        "Before you take a seat",
        Modifier.padding(top = 6.dp, bottom = 4.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    ReadyRow(
        Lucide.Smartphone,
        "An app checked on Play",
        "Added in My apps, in a Play closed test",
        ready.app,
        action = if (hasApps) "Check" else "Add",
        onClick = onApp,
    )
    ReadyRow(Lucide.UserPlus, "On the testers group", "So you can install the others' closed tests", ready.member, "Join", onJoin)
    ReadyRow(
        Lucide.ShieldCheck,
        "Two permissions",
        "Usage access proves each visit; over other apps shows the timer",
        ready.permissions,
        "Allow",
        onAllow,
    )
}

/** One thing to have in place: ticked when it is, or the one button that does it. */
@Composable
private fun ReadyRow(
    icon: ImageVector,
    title: String,
    line: String,
    done: Boolean?,
    action: String,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconSquare(if (done == true) Lucide.Check else icon, if (done == true) Tone.OK else Tone.ACCENT, filled = done == true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(line, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
        }
        // Unknown yet: neither a tick nor a button, rather than asking for something already done.
        if (done == false) {
            Spacer(Modifier.width(8.dp))
            RowPill(action, PillKind.SOLID, onClick = onClick)
        }
    }
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
            .background(if (filled) tone.ink() else tone.soft()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size((size / 2).dp), tint = if (filled) Tt.colors.inkOn(tone.ink()) else tone.ink())
    }
}

// ------------------------------------------------------------------ how it goes

@Composable
private fun CrewJourney() {
    Eyebrow(Lucide.Route, "How it goes")
    Spacer(Modifier.height(6.dp))
    val steps =
        listOf(
            Step(
                Lucide.Armchair,
                Tone.ACCENT,
                "Fill up",
                "Take a seat and wait in the Lobby. The crew starts at midnight, your time, the day all ${CrewRules.SEATS} are in.",
            ),
            Step(
                Lucide.Download,
                Tone.QUIET,
                "Install the other ${CrewRules.NIGHTLY}",
                "From Play, while it fills. One that won't install? Report it: ${CrewRules.REPORTS_TO_RETURN} reports " +
                    "send it back to be fixed.",
            ),
            Step(
                Lucide.Play,
                Tone.OK,
                "Open them every day",
                "Open all takes you through them with a timer on screen. ${CrewRules.VISIT_SECONDS} seconds in each counts it.",
                tag = "${CrewRules.RUN_DAYS} days",
            ),
            Step(
                Lucide.Trophy,
                Tone.WARN,
                "Apply for production",
                "After ${CrewRules.RUN_DAYS} days your app has its testers. Record keeps the run, Members shows who opened it.",
            ),
        )
    steps.forEachIndexed { i, step -> JourneyStep(step, last = i == steps.lastIndex) }
}

private class Step(
    val icon: ImageVector,
    val tone: Tone,
    val title: String,
    val line: String,
    val tag: String? = null,
)

@Composable
private fun JourneyStep(
    step: Step,
    last: Boolean,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            IconSquare(step.icon, step.tone, size = 42)
            // The thread to the next step, so four cards read as one way through.
            if (!last) {
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Tt.colors.glassBorder),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 2.dp, bottom = if (last) 0.dp else 14.dp)) {
            Text(step.title, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), fontWeight = FontWeight.Bold)
            Text(
                step.line,
                Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = Tt.colors.text2,
            )
            if (step.tag != null) {
                Spacer(Modifier.height(6.dp))
                StatusChip(step.tag, Tone.ACCENT)
            }
        }
    }
}
