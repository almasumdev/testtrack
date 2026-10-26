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

    /** With the owner, who has two days. */
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
) {
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
                    "we check after three days and then pay you."
            } else {
                "With the developer. They have two days, then it approves itself."
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
