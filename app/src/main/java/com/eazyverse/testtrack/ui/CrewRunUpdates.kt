package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircle
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.ui.theme.Tt

/** Compact actionable updates, outside tonight's progress and start button. */
@Composable
internal fun CrewRunUpdates(
    runs: List<Crew>,
    tonight: List<CrewApp>,
    actions: CrewActions,
    onOpenCrew: (String) -> Unit,
) {
    val risky = runs.filter { it.atRisk && it.owed > 0 }
    val installs = tonight.filter { !actions.isInstalled(it.packageName) || !actions.fromPlay(it.packageName) }.distinctBy { it.id }
    val apps = runs.filter { !it.out && !it.nightsDone }.flatMap { it.apps + it.optional }
    val pictures = apps.any { it.pictureDue || actions.sendingPicture(it) || actions.waitingPicture(it.id) }
    val attention = if (risky.isEmpty() && installs.isEmpty() && !pictures) crewRunAttention(tonight, actions) else null
    var installsOpen by rememberSaveable { mutableStateOf(false) }
    var attentionOpen by rememberSaveable { mutableStateOf(false) }

    if (risky.isNotEmpty() || installs.isNotEmpty() || pictures || attention != null) {
        Column {
            CrewHeading("Updates")
            Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (risky.isNotEmpty()) MissedNightCard(risky, onOpenCrew)
                if (installs.isNotEmpty()) {
                    CrewCard(padding = CrewUpdatePadding) {
                        CrewUpdateRow(
                            "${installs.size} ${pluralOf(installs.size, "app")} to install",
                            Lucide.Download,
                            note = AnnotatedString(if (installs.size == 1) "Get the Google Play copy" else "Get the Google Play copies"),
                            action = "View",
                            onAction = { installsOpen = true },
                        )
                    }
                }
                if (pictures) CrewRunPictureUpdates(apps, actions)
                attention?.let { note ->
                    CrewCard(padding = CrewUpdatePadding) {
                        CrewUpdateRow(
                            note.title,
                            when (note.kind) {
                                CrewRunAttentionKind.FEEDBACK -> Lucide.MessageCircle
                                CrewRunAttentionKind.UPDATE -> Lucide.RefreshCw
                                CrewRunAttentionKind.MIXED -> Lucide.TriangleAlert
                            },
                            Tone.WARN,
                            explanation = note.explanation,
                            action = "View",
                            onAction = { attentionOpen = true },
                        )
                    }
                }
            }
        }
    }
    if (installsOpen) CrewRunInstallsSheet(installs, runs, actions) { installsOpen = false }
    if (attentionOpen && attention != null) CrewRunAttentionSheet(attention, runs, actions, onOpenCrew) { attentionOpen = false }
}

@Composable
private fun MissedNightCard(
    risky: List<Crew>,
    onOpenCrew: (String) -> Unit,
) {
    val deadline = SpanStyle(fontWeight = FontWeight.Medium, color = Tt.colors.text)
    CrewCard(padding = CrewUpdatePadding) {
        risky.forEachIndexed { index, crew ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            CrewUpdateRow(
                if (risky.size == 1) "Missed yesterday" else "${crew.name} · Missed yesterday",
                Lucide.TriangleAlert,
                Tone.WARN,
                note =
                    buildAnnotatedString {
                        if (risky.size == 1) append("${crew.name} · ")
                        append("${crew.owed} ${pluralOf(crew.owed, "app")} left · ")
                        withStyle(deadline) { append("Before midnight") }
                    },
                explanation = "Miss tonight too and your app leaves this crew. Finish all owed apps before your local midnight.",
                action = "View crew",
                onAction = { onOpenCrew(crew.id) },
            )
        }
    }
}
