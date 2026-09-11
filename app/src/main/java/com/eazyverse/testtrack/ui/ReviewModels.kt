package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview

/*
 * The guided review, as the screen needs it.
 *
 * A review here is not a paragraph somebody types. It is a job with steps, and each step is a
 * different claim about the world that the phone can check: the app is installed, it was actually
 * used, here is the evidence. See docs/feedback.md.
 */

/** Which step the reviewer is standing on. */
enum class ReviewStep(
    val label: String,
) {
    /** Get it from Play, through our button, so the installer is Play and not a sideload. */
    INSTALL("Install it"),

    /** Real foreground time. Nothing drags them back; they return when they are ready. */
    USE("Use it"),

    /** Live app: leave a Play review. Closed testing: write it here. */
    LEAVE("Leave your review"),

    /** A screenshot of what they left, which is the only evidence anybody can produce. */
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
    /**
     * Whether this sitting has already sent the reviewer to the Play review page.
     *
     * The one part of the job with no server state behind it, and it cannot have one: nobody
     * outside Google can see that a review was left, which is the whole reason a screenshot is
     * asked for. So the server's states run out at [ReviewStep.LEAVE], and without this the proof
     * step is unreachable and a live app can never be submitted at all.
     *
     * It is not evidence and nothing is paid on it. It only opens the dialog. Losing it to a
     * restart costs one more tap on a button that was going to be tapped anyway.
     */
    val leftReview: Boolean = false,
) {
    val step: ReviewStep
        get() {
            val reached = ReviewStep.of(review)
            val readyToProve = reached == ReviewStep.LEAVE && post.isLive && leftReview
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

    val settled: Boolean
        get() = review.state in setOf("submitted", "approved", "rejected", "disputed")
}

/** What a review is worth saying about itself once it is out of the reviewer's hands. */
fun reviewOutcome(state: String): String =
    when (state) {
        "submitted" -> "With the developer. They have two days, then it approves itself."
        "approved" -> "Approved. The tix are yours."
        "disputed" -> "The developer disputed it. Three members are deciding."
        "rejected" -> "The dispute went against you. Nothing was paid."
        else -> ""
    }
