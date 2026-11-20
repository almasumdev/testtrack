package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Smartphone
import java.time.Instant

/** One route and one short action list. Upcoming steps already have their place on the route. */
@Composable
internal fun ReviewStepGuide(
    job: ReviewJob,
    usageAccess: Boolean,
    hasProof: Boolean,
    now: Instant = Instant.now(),
) {
    val showTask =
        job.step != ReviewStep.INSTALL ||
            job.onPhone ||
            (job.requiresPlayInstall && job.review.state != "claimed")
    Column(Modifier.fillMaxWidth()) {
        ReviewProgress(job)
        if (showTask) {
            Spacer(Modifier.height(ReviewSectionGap))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                val icon =
                    when (job.step) {
                        ReviewStep.INSTALL -> if (job.onPhone) Lucide.CircleCheck else Lucide.Download
                        ReviewStep.USE -> if (job.waitingForDay) Lucide.Clock else Lucide.Smartphone
                        ReviewStep.LEAVE -> Lucide.MessageSquare
                        ReviewStep.PROVE -> Lucide.Camera
                        ReviewStep.WAIT -> Lucide.Clock
                    }
                val title =
                    when {
                        job.step == ReviewStep.INSTALL -> if (job.onPhone) "Already installed" else "Install to continue"
                        job.step == ReviewStep.USE && !usageAccess -> "Usage access"
                        job.step == ReviewStep.USE && job.waitingForDay -> "Return visit"
                        job.step == ReviewStep.USE -> "Use time"
                        job.rechecking -> "New proof needed"
                        job.noBox -> "No review option on Play"
                        else -> "What to do"
                    }
                ReviewSectionTitle(icon, title)
                when (job.step) {
                    ReviewStep.INSTALL -> {
                        val hours = if (job.live) FeedRules.LIVE_CLAIM_HOURS else FeedRules.CLAIM_HOURS
                        if (job.onPhone) {
                            ReviewBullet(
                                body = "Tap Start review to begin your $hours-hour finish timer.",
                                highlights = listOf("Start review", "$hours-hour"),
                            )
                        } else {
                            ReviewBullet(
                                body = "Install the Google Play version. Your progress is saved.",
                                highlights = listOf("Google Play"),
                            )
                        }
                    }
                    ReviewStep.USE -> ReviewUseTask(job, usageAccess, now)
                    ReviewStep.LEAVE -> {
                        val feedback =
                            when {
                                !job.live -> "private feedback"
                                job.needsChars > 0 -> "5-star rating and review"
                                else -> "5-star rating"
                            }
                        ReviewBullet(body = "Leave $feedback on Google Play.", highlights = listOf(feedback, "Google Play"))
                        ReviewBullet(
                            body = "Capture what you posted before returning here.",
                            highlights = listOf("Capture what you posted"),
                        )
                    }
                    ReviewStep.PROVE -> ReviewProofTask(job, hasProof)
                    ReviewStep.WAIT -> Unit
                }
            }
        }
    }
}

@Composable
private fun ReviewUseTask(
    job: ReviewJob,
    usageAccess: Boolean,
    now: Instant,
) {
    when {
        !usageAccess -> ReviewBullet(body = "Enable TestTrack in Android's Usage access settings.", highlights = listOf("Enable TestTrack"))
        job.waitingForDay -> ReviewReturnVisit(job, now)
        else -> {
            ReviewUsageProgress(job)
            if (job.secondsLeft == 0) {
                ReviewBullet(body = "Usage complete. Checking your progress.", highlights = listOf("Usage complete"))
            } else {
                ReviewBullet(
                    body = "Keep the app open in the foreground.",
                    highlights = listOf("foreground"),
                )
            }
        }
    }
}

@Composable
private fun ReviewProofTask(
    job: ReviewJob,
    hasProof: Boolean,
) {
    when {
        job.rechecking -> {
            ReviewBullet(body = "Capture a fresh screenshot on Google Play.", highlights = listOf("fresh screenshot"))
            job.checkBy?.let { due ->
                // "by Saturday at 2:10 PM", not "by on Saturday".
                val deadline = opensWhen(due).removePrefix("on ")
                ReviewBullet(body = "Send it by $deadline.", highlights = listOf(deadline))
            }
        }
        job.noBox -> {
            ReviewBullet(body = "Show the Play page with no review option.", highlights = listOf("no review option"))
            ReviewBullet(
                body = "Write at least ${job.needsChars} characters here.",
                highlights = listOf("${job.needsChars} characters"),
            )
            val reward = job.reward
            ReviewBullet(body = "Pays $reward tix, half the reward.", highlights = listOf("$reward tix", "half the reward"))
        }
        else -> {
            if (!hasProof) ReviewBullet(body = "Attach a screenshot of what you posted.", highlights = listOf("Attach a screenshot"))
            if (job.needsChars > 0) {
                ReviewBullet(
                    body = "Paste the same feedback here.",
                    highlights = listOf("same feedback"),
                )
            }
        }
    }
    if (hasProof) {
        ReviewBullet(
            body = "Check the screenshot and hide private details before sending.",
            highlights = listOf("hide private details"),
        )
    }
}
