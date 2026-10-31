package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The top of a crew's night: how tonight stands for this account, and the one press that does it.
 */

@Composable
internal fun RunHero(
    crew: Crew,
    owed: List<CrewApp>,
    here: List<CrewApp>,
    missing: List<CrewApp>,
    /** On this phone, but its open would be refused: an update past its 24 hours, or not from Play. */
    refused: List<CrewApp>,
    actions: CrewActions,
) {
    val done = owed.isEmpty()
    val last = crew.day >= crew.runDays
    val next = if (last) "That was the last night." else "Day ${crew.ownDay + 1} begins at midnight, your time."
    // Put back tonight: nothing is owed, and the list below is there for whoever wants it.
    val optional = crew.apps.isEmpty() && crew.optional.isNotEmpty()
    val clean = if (crew.myMisses == 0) "No missed days. " else ""
    val line =
        when {
            optional -> "You don't owe anything tonight, so you can't miss it. $next"
            done && crew.apps.isEmpty() -> "Nothing to open tonight. $next"
            done -> "All ${crew.apps.size} opened. $next"
            crew.atRisk -> "Miss tonight too and your app leaves this crew."
            missing.size == 1 -> "${missing.first().label} isn't on your phone yet, so install it first."
            missing.isNotEmpty() -> "${missing.size} apps aren't on your phone yet, so install them first."
            refused.size == 1 && !actions.fromPlay(refused.first().packageName) ->
                "${refused.first().label} wasn't installed from Google Play, so install it from Play first."
            refused.size == 1 -> "${refused.first().label} needs its update before it counts."
            refused.isNotEmpty() -> "${refused.size} apps need an update or a copy from Google Play first."
            last -> "${clean}Tonight is the last night."
            else -> "${clean}Day ${crew.ownDay + 1} begins at midnight, your time."
        }
    CrewHero(
        chip = if (crew.atRisk) "Day ${crew.ownDay}, ${crew.myMisses} missed" else "Day ${crew.ownDay} of ${crew.ownNights}",
        tone =
            if (crew.atRisk) {
                Tone.WARN
            } else if (done) {
                Tone.OK
            } else {
                Tone.ACCENT
            },
        icon =
            if (crew.atRisk) {
                Lucide.TriangleAlert
            } else if (done) {
                Lucide.CircleCheck
            } else {
                Lucide.Clock
            },
        headline =
            when {
                optional -> "Tonight is optional"
                done -> "Tonight is done"
                else -> "${owed.size} left tonight"
            },
        line = line,
        lineTone = if (crew.atRisk && !done) Tone.WARN else null,
        meter = {
            DayMeter(crew.day, done, from = crew.firstNight, total = crew.runDays)
            // Tonight counts as done once it is, so the two sides always add up to this account's run.
            val daysDone = (crew.ownDay - if (done) 0 else 1).coerceIn(0, crew.ownNights)
            MeterLabels("$daysDone ${if (daysDone == 1) "day" else "days"} done", "${crew.ownNights - daysDone} to go")
        },
        action =
            when {
                here.isEmpty() -> null
                here.size == 1 -> "Open ${here.first().label}"
                missing.isEmpty() && refused.isEmpty() -> "Open all ${here.size}"
                missing.isEmpty() -> "Open the other ${here.size}"
                else -> "Open the ${here.size} on your phone"
            },
        onAction = { actions.onOpenAll(crew.id) },
    )
}

/** This account's nights are over, and the crew is still on its last: nothing to open. */
@Composable
internal fun NightsDoneHero(crew: Crew) {
    CrewHero(
        chip = "${crew.ownNights} of ${crew.ownNights} nights",
        tone = Tone.OK,
        icon = Lucide.CircleCheck,
        headline = "Finished for you",
        line = "Your nights are done. The crew closes when everyone else finishes.",
        meter = {
            DayMeter(crew.runDays, done = true, from = crew.firstNight, total = crew.runDays)
            MeterLabels("${crew.ownNights} ${if (crew.ownNights == 1) "day" else "days"} done", "Nothing to open")
        },
    )
}

/** Above the apps nobody owes tonight, which the team's putting an app back opened up. */
@Composable
internal fun OptionalNote() {
    Text(
        "You don't have to open these tonight. If you do, it counts and shows.",
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text3,
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 8.dp),
    )
}
