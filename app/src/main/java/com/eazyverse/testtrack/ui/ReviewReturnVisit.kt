package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.time.Duration
import java.time.Instant

/** Shared by the countdown and primary action, and paused when the review is off screen. */
@Composable
internal fun rememberReviewNow(job: ReviewJob?): State<Instant> {
    val owner = LocalLifecycleOwner.current
    return produceState(Instant.now(), owner, job?.review?.id, job?.waitingForDay) {
        value = Instant.now()
        if (job?.waitingForDay != true) return@produceState
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                value = Instant.now()
                delay(1000)
            }
        }
    }
}

/** A time highlight on the page ground, with the exact local opening time directly below. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewReturnVisit(
    job: ReviewJob,
    now: Instant,
) {
    val t = Tt.colors
    val opens = job.opensAt()
    val waiting = opens?.isAfter(now) == true
    val ready = job.secondDayNow(now) || (opens != null && !waiting)
    // Timing 2 waits twice: first for the second visit, then for the review to open.
    val backLater = job.paced && !job.secondVisitDone
    val minutes = FeedRules.LIVE_STEP_SECONDS / 60
    val label =
        when {
            waiting && backLater -> "Come back in"
            waiting -> "Review opens in"
            job.usedOnTwoDays -> "Both visits complete"
            ready -> "Return visit ready"
            else -> "Return visit needed"
        }
    val time =
        when {
            waiting -> returnCountdown(opens, now)
            job.usedOnTwoDays -> "Ready"
            ready && job.paced && job.review.step2Ms > 0 -> secondsShort(job.secondVisitSecondsLeft) + " left"
            ready -> "Now"
            job.paced -> "Later"
            else -> "Another day"
        }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
        FlowRow(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .background(t.tile, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = ReviewLineGap)
                .clearAndSetSemantics { contentDescription = "$label $time" },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(ReviewLineGap),
        ) {
            Text(label, Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodyMedium, color = t.text2)
            Text(time, style = MaterialTheme.typography.titleLarge.tabular(), color = t.accentInk)
        }
        if (waiting) {
            Text(
                (if (backLater) "From " else "Opens ") + opensWhen(opens, now),
                Modifier.align(Alignment.CenterHorizontally),
                style = MaterialTheme.typography.labelLarge,
                color = t.accentInk,
            )
        }
        val instruction =
            when {
                job.usedOnTwoDays -> "Both visits are done. No more app use needed."
                job.paced && ready -> "Open the app and use it for $minutes more minutes."
                job.paced ->
                    "Then use the app for $minutes more minutes. The review opens ${FeedRules.LIVE_OPEN_HOURS} hours after your first visit."
                ready -> "Open the app again today to count your return visit."
                else -> "Open the app again when the timer ends."
            }
        ReviewBullet(
            body = instruction,
            highlights =
                listOf(
                    "No more app use needed",
                    "Open the app again today",
                    "when the timer ends",
                    "$minutes more minutes",
                    "${FeedRules.LIVE_OPEN_HOURS} hours",
                ),
        )
    }
}

/** "2m 40s" or "40s", for what is left of the second visit. */
private fun secondsShort(seconds: Int): String = if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"

/** Round up so the final partial second never reads as an already finished wait. */
private fun returnCountdown(
    opens: Instant,
    now: Instant,
): String {
    val seconds = ((Duration.between(now, opens).toMillis().coerceAtLeast(0) + 999) / 1000)
    return when {
        seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
        seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}
