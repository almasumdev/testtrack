package com.eazyverse.testtrack.data

import android.content.Context
import java.time.Instant

/**
 * Where a review's usage window opens on this phone: the server's claim time or the moment this
 * phone first saw the review, whichever is earlier on this phone's clock.
 *
 * The claim time alone is the server's clock. On a phone running a few minutes behind it, the
 * window opened in the phone's future and every reading came back zero until the clocks caught up,
 * which on some phones they never did. The first sighting is the phone's own clock, remembered
 * across restarts, so the window can never open later than the reviewer started.
 *
 * Also when the app was first really used for the review, by the phone's own usage record. The
 * server hears of that use only when TestTrack reports it, which can be after midnight for an app
 * used at 11 PM; the day the use happened is the phone's to say, and a live review's second day is
 * counted from it.
 */
object ReviewClock {
    /**
     * The review's use on this phone since its window opened, as [UsageRepo.measureUse] reads it,
     * keeping the earliest first sitting seen so a record the phone later drops is not lost.
     */
    suspend fun measure(
        context: Context,
        pkg: String,
        reviewId: String,
        createdAt: String,
        needMs: Long,
        settle: Boolean,
    ): UsageRepo.Use {
        val start = windowStart(context, reviewId, createdAt)
        val use = UsageRepo.measureUse(context, pkg, start, needMs = needMs, source = "review", settle = settle)
        use.firstAt?.let { at ->
            val was = firstUse(context, reviewId)
            if (was == null || at < was) prefs(context).edit().putLong(firstKey(reviewId), at).apply()
        }
        return use
    }

    /** When the app was first used for this review by this phone's record, or null if not yet. */
    fun firstUse(
        context: Context,
        reviewId: String,
    ): Long? = prefs(context).getLong(firstKey(reviewId), 0L).takeIf { it > 0L }

    fun windowStart(
        context: Context,
        reviewId: String,
        createdAt: String,
    ): Long {
        val prefs = prefs(context)
        val key = key(reviewId)
        val seen =
            prefs.getLong(key, 0L).takeIf { it > 0L }
                ?: System.currentTimeMillis().also { prefs.edit().putLong(key, it).apply() }
        return minOf(claimedAt(createdAt), seen)
    }

    /** Taken again after it was only opened, a review comes back under the same id: its clock starts now. */
    fun restart(
        context: Context,
        reviewId: String,
    ) = prefs(context)
        .edit()
        .remove(key(reviewId))
        .remove(firstKey(reviewId))
        .apply()

    /**
     * When the clock started, according to the server: from the moment the job was taken on rather
     * than whenever the screen opened, so closing the app and coming back tomorrow does not reset
     * the count. An hour ago if it cannot be read, which under-reports rather than over-reports: the
     * server only ever takes the larger figure, so a short answer costs nothing but a long one would
     * be a free pass.
     */
    private fun claimedAt(createdAt: String): Long =
        runCatching { Instant.parse(createdAt).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis() - 3_600_000L)

    private fun prefs(context: Context) = context.getSharedPreferences("review_clock", Context.MODE_PRIVATE)

    private fun key(reviewId: String) = "first_seen_$reviewId"

    private fun firstKey(reviewId: String) = "first_used_$reviewId"
}
