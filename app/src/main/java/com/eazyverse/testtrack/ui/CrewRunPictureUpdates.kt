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
                CrewUpdateRow(
                    "Screenshot missing",
                    Lucide.Image,
                    Tone.WARN,
                    note = AnnotatedString("${app.label} · Test time saved"),
                    explanation =
                        "Your app time is saved. Add an app screenshot to count this test. " +
                            "You can retake it or use a saved screenshot.",
                    action = "Add",
                    enabled = actions.busySeat != app.id,
                    onAction = { choosing = app },
                )
            }
        }
    }
    if (sending.isNotEmpty()) {
        val late = sending.any { actions.latePicture(it.id) }
        val why = actions.picturesStuck
        val noun = if (sending.size == 1) "screenshot" else "screenshots"
        val label =
            when {
                late -> "Upload waiting · 3h+"
                why != null -> "${sending.size} ${if (sending.size == 1) "upload failed" else "uploads failed"}"
                else -> "Uploading ${sending.size} $noun"
            }
        val explanation =
            when {
                late ->
                    "These screenshots have waited over 3 hours. Tests count once uploads finish. Check your connection and retry. ${why.orEmpty()}"
                        .trim()
                why != null -> "Your screenshots are saved on this phone. Retry to finish the tests. $why"
                else -> "These tests will count once their screenshots finish uploading to your Google Drive."
            }
        CrewCard(padding = CrewUpdatePadding) {
            CrewUpdateRow(
                label,
                if (late) Lucide.Clock else Lucide.CloudUpload,
                tone =
                    when {
                        late -> Tone.BAD
                        why != null -> Tone.WARN
                        else -> Tone.QUIET
                    },
                note = if (late || why != null) AnnotatedString("${sending.size} $noun saved on this phone") else null,
                explanation = explanation,
                action = if (late || why != null) "Retry" else null,
                onAction = actions.onRetryPictures,
            )
        }
    }
    choosing?.let { app -> CrewScreenshotChoice(app, actions) { choosing = null } }
}
