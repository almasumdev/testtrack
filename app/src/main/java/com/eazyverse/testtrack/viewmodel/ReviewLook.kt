package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ReviewClock
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
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
