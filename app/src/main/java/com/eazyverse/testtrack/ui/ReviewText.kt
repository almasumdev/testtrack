package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
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
 * The app under review: its mark, its name, and where it is. Kept to its own two lines (the name
 * and the package, the icon's height) rather than [AppHead]'s, which the Send and Waiting pages
 * were approved without.
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
    val mark = AppTileIcon * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SourcedAppMark(packageName, name, icon, live, size = mark)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).height(mark), verticalArrangement = Arrangement.Center) {
                Text(name, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    packageName,
                    style = TextStyles.DeveloperStyle,
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (offline) {
                Spacer(Modifier.width(8.dp))
                StatusChip("Offline", Tone.PLAIN, size = ChipSize.LARGE)
            }
        }
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
