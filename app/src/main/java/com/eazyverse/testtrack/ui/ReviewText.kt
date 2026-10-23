package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    Text(
        "If the developer refuses your review and the members agree, you are not paid and up to " +
            "${FeedRules.STAKE} tix of yours pay the ${FeedRules.JURORS} members who decided.",
        Modifier.widthIn(max = 300.dp),
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text3,
        textAlign = TextAlign.Center,
    )
}

/** The way out, quiet and at the end: somebody who cannot finish gives the slot back. 48 to touch. */
@Composable
internal fun GiveUpLink(onGiveUp: () -> Unit) {
    Text(
        "Can't finish? Give this review up",
        Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onGiveUp)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
        color = Tt.colors.text3,
        textDecoration = TextDecoration.Underline,
    )
}

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
        ReviewStep.USE ->
            when {
                !job.waitingForDay -> "Try the app"
                job.usedOnTwoDays -> "Almost there"
                else -> "Come back tomorrow"
            }
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
    if (job.waitingForDay) {
        if (!job.usedOnTwoDays) {
            return "Use the app again on another day. Reviews from a second day of use are the ones Google keeps."
        }
        return when (val back = job.hoursToReturn) {
            null, 0 -> "Used on two days. Come back to TestTrack and it opens."
            1 -> "Used on two days. It opens in about an hour; come back to TestTrack then."
            else -> "Used on two days. It opens in about $back hours; come back to TestTrack then."
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
