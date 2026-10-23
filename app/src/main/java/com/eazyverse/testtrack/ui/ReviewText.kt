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
    icon: String = "",
) {
    // The same header as the To check pages: one app looks the same wherever it is shown.
    Box(Modifier.padding(top = 4.dp)) {
        AppHeader(
            name,
            packageName,
            if (live) CheckKind.PLAY else CheckKind.CLOSED,
            detail = if (owner.isNotBlank()) "by $owner" else "",
            icon = icon,
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
        ReviewStep.LEAVE -> if (job.live) "Review" else "Feedback"
        ReviewStep.PROVE -> "Prove"
        ReviewStep.WAIT -> ""
    }

internal fun stepTitle(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Install ${job.post.appName}"
        ReviewStep.USE -> if (job.hoursToReturn != null) "Come back tomorrow" else "Try the app"
        ReviewStep.LEAVE -> if (job.live) "Review it on Google Play" else "Send feedback on Google Play"
        ReviewStep.PROVE -> "Send your proof"
        ReviewStep.WAIT -> "Sent"
    }

internal fun stepLine(job: ReviewJob): String =
    when (job.step) {
        ReviewStep.INSTALL -> "Get it from Google Play so it counts."
        ReviewStep.USE -> useLine(job)
        ReviewStep.LEAVE ->
            if (job.live) {
                "Rate it and write a few honest words on its Play page. Then tap the camera to take a picture of your review."
            } else {
                "On its Play page, send the developer private feedback. Then tap the camera to take a picture of it."
            }
        ReviewStep.PROVE -> "The screenshot of what you left on Google Play. Add a note for the developer if you like."
        ReviewStep.WAIT -> ""
    }

/** Where the reviewer is with using the app: time still to go, or a live app's second day. */
private fun useLine(job: ReviewJob): String {
    val back = job.hoursToReturn
    if (back != null) {
        return when (back) {
            0 -> "Open the app again now. Reviews from a second day of use are the ones Google keeps."
            1 -> "Open the app again in about an hour. Reviews from a second day of use are the ones Google keeps."
            else -> "Open the app again in about $back hours. Reviews from a second day of use are the ones Google keeps."
        }
    }
    val left = job.secondsLeft
    if (left == 0) {
        return if (job.live) {
            "Long enough. Leave your review on Google Play next."
        } else {
            "Long enough. Send your feedback on Google Play next."
        }
    }
    val minutes = left / 60
    val seconds = left % 60
    val time =
        when {
            minutes == 0 -> "$seconds seconds"
            seconds == 0 -> if (minutes == 1) "1 minute" else "$minutes minutes"
            else -> "$minutes min $seconds s"
        }
    return "$time more in the app"
}

internal fun stepAction(
    step: ReviewStep,
    job: ReviewJob,
): String =
    when (step) {
        ReviewStep.INSTALL -> "Get it on Play"
        ReviewStep.USE -> "Open the app"
        ReviewStep.LEAVE -> "Open Google Play"
        ReviewStep.PROVE -> "Send the proof"
        ReviewStep.WAIT -> ""
    }
