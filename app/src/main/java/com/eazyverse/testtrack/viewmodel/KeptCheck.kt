package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiReview
import java.time.OffsetDateTime

/**
 * A live review's after-the-fact check: is the app still on this phone a few days after its review
 * was sent?
 *
 * Asked by the server once, from `keep_from` on, and answered whenever TestTrack next reads this
 * person's reviews. Kept, an unanswered review approves itself then rather than days later;
 * gone, the owner is told, since uninstalling straight after reviewing is what Google's filter
 * looks for. The phone only says what it sees; what that means is the server's call.
 */
object KeptCheck {
    suspend fun report(
        context: Context,
        reviews: List<ApiReview>,
    ) {
        val now = OffsetDateTime.now()
        for (review in reviews) {
            if (review.state != "submitted" || !review.isLive || review.kept != null) continue
            val from = review.keepFrom?.let { runCatching { OffsetDateTime.parse(it) }.getOrNull() } ?: continue
            if (now.isBefore(from) || review.packageName.isBlank()) continue
            val installed = InstalledApps.info(context, review.packageName).installed
            // One that does not go through is asked again next time; nothing waits on it.
            runCatching { Api.kept(review.id, installed) }
        }
    }
}
