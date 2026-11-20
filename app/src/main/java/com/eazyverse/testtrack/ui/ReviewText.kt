package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    ReviewBullet(
        lead = "Lost dispute:",
        body = "No reward; up to ${FeedRules.STAKE} tix deducted.",
        highlights = listOf("No reward", "${FeedRules.STAKE} tix"),
    )
}

/**
 * A way out, said as a question and a small outlined button beside it. It was underlined small
 * print, which read as a note rather than something to tap.
 */
@Composable
internal fun QuietAction(
    question: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(question, style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp), color = Tt.colors.text3)
        Spacer(Modifier.width(10.dp))
        RowPill(action, PillKind.GHOST, onClick = onClick)
    }
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
    val t = Tt.colors
    val mark = AppTileIcon * LocalDensity.current.fontScale.coerceAtLeast(1f)
    Column(Modifier.padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(mark)) {
                AppMark(packageName, name, icon, size = mark)
                SourceMark(live, Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).height(mark), verticalArrangement = Arrangement.Center) {
                Text(name, style = AppTileName, color = t.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    packageName,
                    style = ReviewPackageStyle,
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (offline) {
                Spacer(Modifier.width(8.dp))
                Pill("Offline", t.text2, t.glass2)
            }
        }
        if (owner.isNotBlank()) {
            Text("by $owner", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = t.text3)
        }
    }
}

private val ReviewPackageStyle = tileStyle(12.5f, 14f, FontWeight.Medium)

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
