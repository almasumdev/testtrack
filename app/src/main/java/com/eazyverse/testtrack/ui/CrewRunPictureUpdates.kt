package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.CloudUpload
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.pluralOf

/** Run's compact proof updates. Crew detail retains its existing page-level recovery card. */
@Composable
internal fun CrewRunPictureUpdates(
    apps: List<CrewApp>,
    actions: CrewActions,
) {
    val unique = apps.distinctBy { it.id }
    val missing = unique.filter { it.pictureDue && !actions.sendingPicture(it) }
    val sending = unique.filter { actions.sendingPicture(it) || actions.waitingPicture(it.id) }
    var choosing by remember { mutableStateOf<CrewApp?>(null) }
    if (missing.isNotEmpty()) {
        CrewCard(padding = CrewUpdatePadding) {
            missing.forEachIndexed { index, app ->
                if (index > 0) Spacer(Modifier.height(12.dp))
                StatusLine(
                    Lucide.Image,
                    "Screenshot missing",
                    tone = Tone.WARN,
                    note = AnnotatedString("Test time saved for ${app.label}"),
                    info =
                        "Your app time is saved. Add an app screenshot to count this test. " +
                            "You can retake it or use a saved screenshot.",
                ) { InlineAction("Add", enabled = actions.busySeat != app.id) { choosing = app } }
            }
        }
    }
    if (sending.isNotEmpty()) {
        val late = sending.any { actions.latePicture(it.id) }
        val why = actions.picturesStuck
        val noun = pluralOf(sending.size, "screenshot")
        val label =
            when {
                late -> "Upload waiting over 3 hours"
                why != null -> "${sending.size} ${if (sending.size == 1) "upload failed" else "uploads failed"}"
                else -> "Uploading ${sending.size} $noun"
            }
        val explanation =
            when {
                late ->
                    "These screenshots have waited over 3 hours. Tests count once uploads finish. Check your connection and retry. ${why.orEmpty()}"
                        .trim()
                why != null -> "Your screenshots are saved on this phone. Retry to finish the tests. $why"
                sending.size == 1 -> "It counts once the screenshot is sent. Check you're online."
                else -> "They count once the screenshots are sent. Check you're online."
            }
        CrewCard(padding = CrewUpdatePadding) {
            StatusLine(
                if (late) Lucide.Clock else Lucide.CloudUpload,
                label,
                tone =
                    when {
                        late -> Tone.BAD
                        why != null -> Tone.WARN
                        else -> Tone.QUIET
                    },
                note = if (late || why != null) AnnotatedString("${sending.size} $noun saved on this phone") else null,
                info = explanation,
                actions = if (late || why != null) ({ InlineAction("Retry", onClick = actions.onRetryPictures) }) else null,
            )
        }
    }
    choosing?.let { app -> CrewScreenshotChoice(app, actions) { choosing = null } }
}
