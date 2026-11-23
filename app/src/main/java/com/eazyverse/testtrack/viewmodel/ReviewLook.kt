package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ReviewClock
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.net.usedTimed
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.ReviewJob
import com.eazyverse.testtrack.ui.ReviewViewModel
import com.eazyverse.testtrack.ui.isPast

/*
 * What the review page reads off the phone and tells the server: the install, the usage time and the
 * days of use. Beside its view model, so the class itself stays the part that holds the page's state.
 */

private const val CLAIMED = "claimed"
private const val INSTALLED = "installed"
private const val USED = "used"

/** The most the server takes in one report: a day. A lost pause event can read more. */
private const val MAX_USED_MS = 86_400_000L

/** Report fresh phone observations. [settle] waits for Android to finish writing usage events. */
internal suspend fun ReviewViewModel.look(
    context: Context,
    current: ReviewJob,
    settle: Boolean,
): ReviewJob {
    if (current.settled) return current
    usageAccess = UsageRepo.hasAccess(context)
    val info = InstalledApps.info(context, current.post.packageName)

    sideloaded = info.installed && !info.fromPlay
    if (current.review.id.isEmpty()) return current.copy(onPhone = info.fromPlay)
    if (!info.fromPlay) return current.copy(requiresPlayInstall = true, onPhone = false)

    var review = current.review
    if (review.state == CLAIMED) {
        review = Api.installed(review.id)
        offline = false
    }
    if (review.state == INSTALLED || review.state == USED) {
        val use = ReviewClock.measure(context, current.post.packageName, review.id, review.createdAt, review.neededMs, settle)
        if (!InstalledApps.info(context, current.post.packageName).fromPlay) {
            return current.copy(review = review, requiresPlayInstall = true)
        }
        if (review.isPaced()) {
            review = reportPaced(context, current.post.packageName, review, use)
            return current.copy(review = review, requiresPlayInstall = false)
        }
        // A new day of use is news even when the total has not moved since the last report, and
        // so is a live review's hours passing: the server opens its last step on being asked.
        val due = review.state == INSTALLED && review.returnsAt?.let(::isPast) == true
        if (use.ms > review.usedMs || use.days > review.daysUsed || due) {
            review = Api.used(review.id, maxOf(use.ms, review.usedMs).coerceAtMost(MAX_USED_MS), use.days)
            offline = false
        }
    }
    return current.copy(review = review, requiresPlayInstall = false)
}

/** Timing 2, as the server set it at claim. A review from an older server never is. */
private fun ApiReview.isPaced() = timing >= FeedRules.LIVE_TIMING && step2NeededMs > 0

/**
 * Timing 2's report: the running total as before, the second visit's time read from when it starts
 * to count, and when the first visit's minutes were reached by the phone's own record, so a visit
 * told to TestTrack hours later still starts the wait when it really ended.
 */
private suspend fun ReviewViewModel.reportPaced(
    context: Context,
    pkg: String,
    review: ApiReview,
    use: UsageRepo.Use,
): ApiReview {
    val now = System.currentTimeMillis()
    // Read once the first reading has settled, so this one needs no wait of its own.
    val again = instantOf(review.useAgainFrom)?.toEpochMilli()?.takeIf { it < now }
    val step2 = again?.let { UsageRepo.measure(context, pkg, it, review.step2NeededMs, "review_again", settle = false) }
    val step1At = if (review.step1DoneAt == null) use.reachedAt else null
    // Both visits in and the hours passed: the server opens the last step on being asked.
    val opens = instantOf(review.reviewFrom)?.toEpochMilli()
    val due = review.state == INSTALLED && review.step2Ms >= review.step2NeededMs && opens != null && opens <= now
    if (use.ms <= review.usedMs && use.days <= review.daysUsed && (step2 ?: 0L) <= review.step2Ms && !due) return review
    val reported =
        Api.usedTimed(
            review.id,
            maxOf(use.ms, review.usedMs).coerceAtMost(MAX_USED_MS),
            use.days,
            step2Ms = step2?.coerceAtMost(MAX_USED_MS),
            step1At =
                step1At?.let {
                    java.time.Instant
                        .ofEpochMilli(it)
                        .toString()
                },
        )
    offline = false
    return reported
}
