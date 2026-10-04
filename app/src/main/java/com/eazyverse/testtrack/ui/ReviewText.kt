package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The review page's words: the app it is about, and what each step is called and asks for.
 * Split from ReviewScreen.kt.
 */

/** The app under review: its mark, its name, and where it is. */
@Composable
internal fun ReviewAppRow(
    name: String,
    live: Boolean,
    owner: String,
    offline: Boolean = false,
) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AppTile(name, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                (if (live) "Live on Google Play" else "Closed testing") +
                    if (owner.isNotBlank()) " · by $owner" else "",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Showing the last saved answer: still usable, just not this minute's.
        if (offline) {
            Spacer(Modifier.width(8.dp))
            Pill("Offline", Tt.colors.text2, Tt.colors.glass2)
        }
    }
}

internal fun stepShort(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Install"
        ReviewStep.USE -> "Use"
        ReviewStep.LEAVE -> "Review"
        ReviewStep.PROVE -> "Prove"
        ReviewStep.WAIT -> ""
    }

internal fun stepTitle(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Install ${job.post.appName}"
        ReviewStep.USE -> "Try the app"
        ReviewStep.LEAVE -> if (job.post.isLive) "Review it on Play" else "Send feedback on Play"
        ReviewStep.PROVE -> "Send your proof"
        ReviewStep.WAIT -> "Sent"
    }

internal fun stepLine(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Get it from Google Play so it counts."
        ReviewStep.USE ->
            if (job.secondsLeft == 0) "Long enough. Leave your review next." else "${job.secondsLeft} more seconds in the app"
        ReviewStep.LEAVE ->
            if (job.post.isLive) {
                "Rate it on Play, then come back with a screenshot."
            } else {
                "Use “Send feedback” on its Play page, then come back with a screenshot."
            }
        ReviewStep.PROVE -> "Add a screenshot of what you left on Play."
        ReviewStep.WAIT -> ""
    }

internal fun stepAction(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Get it on Play"
        ReviewStep.USE -> "Open the app"
        ReviewStep.LEAVE -> "Open Play to review"
        ReviewStep.PROVE -> "Send the proof"
        ReviewStep.WAIT -> ""
    }
