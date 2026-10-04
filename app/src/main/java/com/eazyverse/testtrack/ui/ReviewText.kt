package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The review page's words: the app it is about, and what each step is called and asks for.
 * Split from ReviewScreen.kt.
 */

/** The app under review: its mark, its name, and where it is. */
@Composable
internal fun ReviewAppRow(
    name: String,
    packageName: String,
    live: Boolean,
    owner: String,
    offline: Boolean = false,
) {
    // The same header as the To check pages: one app looks the same wherever it is shown.
    Box(Modifier.padding(top = 4.dp)) {
        AppHeader(
            name,
            packageName,
            if (live) CheckKind.PLAY else CheckKind.CLOSED,
            detail = if (owner.isNotBlank()) "by $owner" else "",
        ) {
            // Showing the last saved answer: still usable, just not this minute's.
            if (offline) {
                Spacer(Modifier.width(8.dp))
                Pill("Offline", Tt.colors.text2, Tt.colors.glass2)
            }
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
