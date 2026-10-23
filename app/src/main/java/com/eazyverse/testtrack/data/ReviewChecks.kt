package com.eazyverse.testtrack.data

import android.content.Context
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiReview
import java.time.OffsetDateTime

/**
 * What this phone can see about its own reviews, said to the server wherever TestTrack reads them:
 * Circle loading, and the half-hourly [FeedbackWatch]. The phone only says what it sees; what that
 * means is the server's call. One that does not go through is asked again next time.
 */
object ReviewChecks {
    suspend fun report(
        context: Context,
        reviews: List<ApiReview>,
    ) {
        val now = OffsetDateTime.now()
        for (review in reviews) {
            if (review.packageName.isBlank()) continue
            when {
                review.state == "claimed" -> installed(context, review)
                review.state == "submitted" && review.isLive && review.kept == null -> kept(context, review, now)
            }
        }
    }

    /**
     * Installed from Play without coming back to its review. The install is what holds the slot, so
     * somebody who installs and goes straight into the app holds it from their next look at
     * TestTrack, not only from the review's own page, by which time the last slot could be gone.
     */
    private suspend fun installed(
        context: Context,
        review: ApiReview,
    ) {
        val info = InstalledApps.info(context, review.packageName)
        if (!info.installed || info.installer != PLAY) return
        runCatching { Api.installed(review.id) }
    }

    /**
     * A live review's after-the-fact check: is the app still on this phone a few days after its
     * review was sent? Asked by the server once, from `keep_from` on. Kept, an unanswered review
     * approves itself then rather than days later; gone, the owner is told, since uninstalling
     * straight after reviewing is what Google's filter looks for.
     */
    private suspend fun kept(
        context: Context,
        review: ApiReview,
        now: OffsetDateTime,
    ) {
        val from = review.keepFrom?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: return
        if (now.isBefore(from)) return
        runCatching { Api.kept(review.id, InstalledApps.info(context, review.packageName).installed) }
    }

    private const val PLAY = "com.android.vending"
}
