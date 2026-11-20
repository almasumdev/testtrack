package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The review page's words: the app it is about, and what each step is called and asks for.
 * Split from ReviewScreen.kt.
 */

/**
 * What losing a dispute costs the reviewer, said before the review starts: no pay for it, and the
 * members who decided paid from their tix, as the server settles it (moderation._settle).
 */
@Composable
internal fun DisputeNote() {
    ReviewBullet(
        lead = "Lost dispute:",
        body = "No reward; up to ${FeedRules.STAKE} tix deducted.",
        highlights = listOf("No reward", "${FeedRules.STAKE} tix"),
    )
}

/**
 * The app under review: its mark, its name, and where it is: [AppHead] with the package flush under
 * the name, as the Send and Waiting pages were approved.
 */
@Composable
internal fun ReviewAppRow(
    name: String,
    packageName: String,
    live: Boolean,
    owner: String,
    offline: Boolean = false,
    icon: String = "",
) {
    val t = Tt.colors
    Column(Modifier.padding(top = 4.dp)) {
        AppHead(
            packageName,
            name,
            icon,
            live,
            packageName,
            trailing = if (offline) ({ StatusChip("Offline", Tone.PLAIN, size = ChipSize.LARGE) }) else null,
            subGap = 0.dp,
            subStyle = TextStyles.DeveloperStyle,
        )
        if (owner.isNotBlank()) {
            Text("by $owner", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = t.text3)
        }
    }
}

internal fun stepShort(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Install"
        ReviewStep.USE -> "Use app"
        ReviewStep.LEAVE -> if (job.live) "Review" else "Feedback"
        ReviewStep.PROVE -> "Send proof"
        ReviewStep.WAIT -> ""
    }

internal fun usageLeftLabel(left: Int): String {
    val minutes = left / 60
    val seconds = left % 60
    return when {
        minutes == 0 -> if (seconds == 1) "1 second" else "$seconds seconds"
        seconds == 0 -> if (minutes == 1) "1 minute" else "$minutes minutes"
        else -> "$minutes min $seconds s"
    }
}

internal fun stepAction(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL ->
            when {
                job.onPhone -> "Start review"
                job.requiresPlayInstall -> "Open Google Play"
                else -> "Get it on Play"
            }
        ReviewStep.USE -> "Open the app"
        ReviewStep.LEAVE -> "Open Google Play"
        ReviewStep.PROVE ->
            when {
                job.rechecking -> "Send the new screenshot"
                else -> "Send the proof"
            }
        ReviewStep.WAIT -> ""
    }
