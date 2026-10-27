package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Two cards on a running crew's page, from docs/crew.md.
 *
 * The owner's own: tell the crew a newer version is out, which only works from a copy Play put on
 * their phone, and mark the app down tonight. A member's: the apps still waiting on their private
 * feedback on Play, each with the night it is due by, since from the night after an app's opens
 * count only once it is in.
 */

/** The owner's Update and Down tonight, under their Your app card. */
@Composable
internal fun OwnerToolsCard(
    mine: CrewSeat,
    actions: CrewActions,
) {
    val blocked = actions.updateBlocked(mine)
    Box(Modifier.padding(top = 10.dp)) {
        CrewCard {
            Text("Tell your crew", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                if (mine.versionCode != null) {
                    "They have version ${mine.versionName.ifBlank {
                        mine.versionCode.toString()
                    }}. After you tap Update they get 24 hours to install the new one."
                } else {
                    "After you tap Update they get 24 hours to install the new version."
                },
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text3,
            )
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Looks off until the new version from Play is on this phone; a tap says why.
                    if (blocked == null) {
                        Primary("Update", Modifier.fillMaxWidth(), busy = actions.busySeat == mine.id) { actions.onTellUpdate(mine) }
                    } else {
                        Secondary("Update", Modifier.fillMaxWidth()) { actions.onTellUpdate(mine) }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Secondary("Down tonight", Modifier.fillMaxWidth(), enabled = mine.downsLeft > 0) { actions.onMarkDown(mine) }
                }
            }
            if (blocked != null) {
                Text(
                    "Install the new version from Google Play on this phone to tell them.",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tt.colors.text4,
                )
            }
            CardNote("Down tonight: ${mine.downsLeft} of ${CrewRules.DOWN_NIGHTS} left this run.")
        }
    }
}

/** The apps still waiting on this member's private feedback on Play. */
@Composable
internal fun PlayFeedbackCard(
    crew: Crew,
    actions: CrewActions,
) {
    val owed = crew.apps.filter { !it.feedbackSent && !it.reported }
    if (owed.isEmpty()) return
    val late = owed.filter { it.feedbackLate }
    Box(Modifier.padding(top = 10.dp)) {
        CrewCard {
            StatusChip(
                if (late.isNotEmpty()) "${late.size} feedback late" else "${owed.size} feedback to send",
                if (late.isNotEmpty()) Tone.WARN else Tone.ACCENT,
            )
            Text(
                "Private feedback on Play",
                Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Once for each app, by your night ${CrewRules.FEEDBACK_BY_NIGHT} with it. After that, opening an app " +
                    "counts only once its feedback is sent.",
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text3,
            )
            owed.sortedBy { it.feedbackDueDay }.forEach { app ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (app.feedbackLate) "Late: its opens don't count until it's sent" else "Due by night ${app.feedbackDueDay}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (app.feedbackLate) Tt.colors.warn else Tt.colors.text4,
                        )
                    }
                    RowPill("Send", PillKind.SOLID) { actions.onFeedback(app) }
                    Spacer(Modifier.width(6.dp))
                    RowPill("Pick", PillKind.SOFT) { actions.onPickFeedback(app) }
                }
            }
        }
    }
}
