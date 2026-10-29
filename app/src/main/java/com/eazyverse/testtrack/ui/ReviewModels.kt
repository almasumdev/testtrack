package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview

/*
 * The guided review, as the screen needs it.
 *
 * A review here is a job with steps, and each step is a different claim about the world that the
 * phone can check: the app is installed, it was actually used, and here is a picture of what was
 * left for it on Google Play. A closed test's feedback is private feedback to its developer on
 * Play; a live app's is a public rating and review, paced like a real user's. See docs/feedback.md.
 */

/** Which step the reviewer is standing on. */
enum class ReviewStep(
    val label: String,
) {
    /** Get it from Play, through our button, so the installer is Play and not a sideload. */
    INSTALL("Install it"),

    /** Real foreground time. Nothing drags them back; they return when they are ready. */
    USE("Use it"),

    /** Left on Google Play: private feedback for a closed test, a rating and review for a live app. */
    LEAVE("Leave it on Google Play"),

    /** A screenshot of what they left there, which is the only evidence anybody can produce. */
    PROVE("Prove it"),

    /** With the owner, who has a day. */
    WAIT("Waiting on the developer"),
    ;

    companion object {
        /**
         * Where a review from the API has got to.
         *
         * Derived from the server's state rather than kept on the phone. A step remembered
         * locally is a step that survives a reinstall as a lie.
         */
        fun of(review: ApiReview): ReviewStep =
            when (review.state) {
                "claimed" -> INSTALL
                "installed" -> USE
                "used" -> LEAVE
                else -> WAIT
            }
    }
}

/** Everything one review screen is showing. */
data class ReviewJob(
    val post: ApiPost,
    val review: ApiReview,
    /** Whether the reviewer has been to Google Play for it and is on to the proof. */
    val leftReview: Boolean = false,
    /**
     * When the app was first used for it, by this phone's own usage record (epoch ms), or null.
     * Preferred over the server's [ApiReview.firstUsedAt], which is when TestTrack reported that
     * use: an app used at 11 PM and reported after midnight is still first used the day before.
     */
    val firstUsedHere: Long? = null,
    /** Not taken yet, and already on this phone from Play: taking it starts the review at once. */
    val onPhone: Boolean = false,
) {
    /** The first day of use: the phone's own record where it has one, else what the server heard. */
    private val firstUse: java.time.Instant?
        get() = firstUsedHere?.let { java.time.Instant.ofEpochMilli(it) } ?: instantOf(review.firstUsedAt)

    val step: ReviewStep
        get() {
            val reached = ReviewStep.of(review)
            val readyToProve = reached == ReviewStep.LEAVE && leftReview
            return if (readyToProve) ReviewStep.PROVE else reached
        }

    /** How far through the usage requirement, between nothing and everything. */
    val usedFraction: Float
        get() =
            if (review.neededMs <= 0) {
                1f
            } else {
                (review.usedMs.toFloat() / review.neededMs).coerceIn(0f, 1f)
            }

    val secondsLeft: Int
        get() = (((review.neededMs - review.usedMs) / 1000).coerceAtLeast(0)).toInt()

    val live: Boolean get() = post.isLive

    /** A live review with all the time it needs, still waiting on its second day of use. */
    val waitingForDay: Boolean get() = live && secondsLeft == 0 && review.state == "installed"

    /** Used on two days already, so only the hours since it was taken are still to pass. */
    val usedOnTwoDays: Boolean get() = review.daysUsed >= 2

    /**
     * While [waitingForDay], the hours until it may open, at least one, from when the server says;
     * null when it is not waiting or the server has not said.
     */
    val hoursToReturn: Int?
        get() {
            val at = review.returnsAt ?: return null
            if (!waitingForDay) return null
            val millis =
                runCatching {
                    java.time.OffsetDateTime
                        .parse(at)
                        .toInstant()
                        .toEpochMilli()
                }.getOrNull() ?: return null
            val left = millis - System.currentTimeMillis()
            return if (left <= 0) 0 else ((left + 3_599_999) / 3_600_000).toInt().coerceAtLeast(1)
        }

    val settled: Boolean
        get() = review.state in setOf("submitted", "approved", "rejected", "disputed", "abandoned", "expired")

    /**
     * While [waitingForDay] and not yet used on two days: today is already another calendar day
     * than the first use, so opening the app now is the second day's use.
     */
    fun secondDayNow(
        now: java.time.Instant = java.time.Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Boolean {
        if (!waitingForDay || usedOnTwoDays) return false
        val first = firstUse ?: return false
        return first.atZone(zone).toLocalDate() < now.atZone(zone).toLocalDate()
    }

    /**
     * The earliest a live review waiting on its second day can open, as the server rules it:
     * used again [LIVE_RETURN_HOURS] after its first use, or used on a second calendar day once
     * that long has passed since it was taken. Null when it is not waiting, or a time is missing.
     */
    fun opensAt(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): java.time.Instant? {
        if (!waitingForDay) return null
        val back = instantOf(review.returnsAt) ?: return null
        if (usedOnTwoDays) return back
        val first = firstUse ?: return back
        val taken = instantOf(review.createdAt) ?: return back
        val nextDay =
            first
                .atZone(zone)
                .toLocalDate()
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
        val twoDays = maxOf(nextDay, taken.plusSeconds(LIVE_RETURN_HOURS * 3600L))
        return minOf(back, twoDays)
    }

    private companion object {
        /** rules.LIVE_RETURN_HOURS on the server. */
        const val LIVE_RETURN_HOURS = 20

        fun instantOf(iso: String?): java.time.Instant? =
            iso?.let {
                runCatching {
                    java.time.OffsetDateTime
                        .parse(it)
                        .toInstant()
                }.getOrNull()
            }
    }
}

/** A request opened and not yet taken, as a review that has not started: nothing on the server. */
internal fun previewOf(post: ApiPost) =
    ApiReview(
        id = "",
        postId = post.id,
        state = "claimed",
        usedMs = 0,
        neededMs = (if (post.isLive) FeedRules.LIVE_USE_SECONDS else FeedRules.CLOSED_USE_SECONDS) * 1000L,
        proofRef = "",
        written = "",
        reason = "",
        createdAt =
            java.time.Instant
                .now()
                .toString(),
        isLive = post.isLive,
    )

/** Whether a time the server sent has come. */
internal fun isPast(iso: String): Boolean =
    runCatching {
        java.time.Instant
            .parse(iso)
            .toEpochMilli() <= System.currentTimeMillis()
    }.getOrDefault(false)

/**
 * When a review opens, said the way a person says it: "today at 2:10 PM", "tomorrow at 2:10 PM",
 * or "on Friday at 2:10 PM". The time in the phone's own format.
 */
fun opensWhen(
    at: java.time.Instant,
    now: java.time.Instant = java.time.Instant.now(),
    zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): String {
    val then = at.atZone(zone)
    val time =
        then.format(
            java.time.format.DateTimeFormatter
                .ofLocalizedTime(java.time.format.FormatStyle.SHORT),
        )
    val days =
        java.time.temporal.ChronoUnit.DAYS
            .between(now.atZone(zone).toLocalDate(), then.toLocalDate())
    return when (days) {
        0L -> "today at $time"
        1L -> "tomorrow at $time"
        else -> "on ${then.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())} at $time"
    }
}

/** "about 20 hours", "about an hour", or "a few minutes", until [at]. */
fun untilWords(
    at: java.time.Instant,
    now: java.time.Instant = java.time.Instant.now(),
): String {
    val minutes =
        java.time.Duration
            .between(now, at)
            .toMinutes()
    val hours = (minutes + 30) / 60
    return when {
        minutes < 50 -> "a few minutes"
        hours <= 1 -> "about an hour"
        else -> "about $hours hours"
    }
}

/** "3h 5m", "45m", for the dial's countdown. */
fun untilShort(
    at: java.time.Instant,
    now: java.time.Instant = java.time.Instant.now(),
): String {
    val minutes =
        java.time.Duration
            .between(now, at)
            .toMinutes()
            .coerceAtLeast(1)
    return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
}

/** What a review is worth saying about itself once it is out of the reviewer's hands. */
fun reviewOutcome(
    state: String,
    live: Boolean = false,
    /** Why it ended, where its reviewer did not give it up. */
    endedReason: String? = null,
): String =
    when (state) {
        "abandoned" ->
            when (endedReason) {
                "reported" -> "You told the developer it wouldn't install. Nothing counts against you, and the slot went back."
                "request_deleted" -> "The request was removed, so this review stopped. Nothing counts against you."
                "member_deleted" -> "The developer's account was removed, so this review stopped. Nothing counts against you."
                "team" -> "The TestTrack team stopped this review. Nothing counts against you."
                else -> "You gave this one up. The slot went to somebody else."
            }
        "submitted" ->
            if (live) {
                "With the developer, who can accept it any time. If they don't answer, keep the app installed: " +
                    "we check after a day and then pay you."
            } else {
                "With the developer. They have a day, then it approves itself."
            }
        "approved" ->
            if (live) {
                "Approved. The tix are yours. Keep the app a few more days so your review counts on Google Play."
            } else {
                "Approved. The tix are yours."
            }
        "disputed" -> "The developer disputed it. Three members are deciding."
        "rejected" -> "The members sided with the developer. Nothing was paid, and their fee came from your tix."
        "expired" ->
            if (live) "It sat unfinished for three days, so the slot went back." else "It sat unfinished for a day, so the slot went back."
        else -> ""
    }
