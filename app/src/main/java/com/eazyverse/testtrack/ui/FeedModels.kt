package com.eazyverse.testtrack.ui

/*
 * The community half of TestTrack, in the shape the screen needs it.
 *
 * Plain values the FeedViewModel holds, mapped from what the API sends. Nothing here is seeded.
 */

/** The tabs of Circle: what people are asking for, and this account's own reviews and checks. */
enum class FeedTab(
    val label: String,
) {
    /** Developers asking the community to try an app and write something useful back. */
    FEEDBACK("Feedback"),

    /** Every review this account has taken on: in progress, waiting on the developer, decided. */
    MINE("My reviews"),

    /**
     * Anything waiting on a decision from this account.
     *
     * Reviews of your own apps, and cases between two other people you are eligible to help
     * decide. One tab because from the reader's side it is one errand: somebody is waiting.
     */
    WAITING("To check"),
}

/**
 * What a post costs and what a review pays.
 *
 * A number on both sides is the whole point. Feedback that is free to ask for arrives as fourteen
 * requests nobody answers, and feedback that pays nothing gets written as "nice app".
 *
 * The reward is set by whoever posts, which is what makes it worth printing on a card: a figure
 * that is the same everywhere is decoration, and a figure somebody chose is a bid. Somebody in a
 * hurry, or asking about something tedious, can pay their way up the list.
 *
 * What stops everybody simply offering the maximum is that the cost follows the offer. A post buys
 * the selected number of reviews, charged at the chosen reward per review. By default
 * that comes to sixty, which is what a post has always cost here; the old flat price turns out to
 * be this rule with the minimum plugged into it.
 */
object FeedRules {
    /** rules.LIVE_USE_SECONDS and rules.CLOSED_USE_SECONDS on the server: use before Play opens. */
    const val LIVE_USE_SECONDS = 300
    const val CLOSED_USE_SECONDS = 120

    /** rules.LIVE_RETURN_HOURS: a live app is used again this long after its first use. */
    const val LIVE_RETURN_HOURS = 20

    /** rules.CLAIM_HOURS and rules.LIVE_CLAIM_HOURS: how long a taken review may sit unfinished. */
    const val CLAIM_HOURS = 24
    const val LIVE_CLAIM_HOURS = 72

    /** The least a post may offer. Below this nobody writes forty characters for it. */
    const val MIN_REWARD = 20

    /** The most. Without a ceiling one large balance can outbid every other post on the screen. */
    const val MAX_REWARD = 200

    /** How many reviews a post is buying, and so the multiple its price is charged at. */
    const val REVIEWS_BOUGHT = 3
    const val MIN_REVIEWS = 1
    const val MAX_REVIEWS = 50

    /**
     * What a request asks of each reviewer (rules.FEEDBACK): the least it may pay for one review,
     * and the characters of text pasted from Play. Stars is every request from before these.
     */
    enum class Feedback(
        val wire: String,
        val least: Int,
        val chars: Int,
        val label: String,
    ) {
        STARS("stars", MIN_REWARD, 0, "Star rating"),
        WRITTEN("written", 35, 100, "Written feedback"),
        DETAILED("detailed", 50, 250, "Detailed feedback"),
        ;

        companion object {
            fun of(wire: String): Feedback = entries.firstOrNull { it.wire == wire } ?: STARS
        }
    }

    /**
     * A live review Play would not take, sent in TestTrack instead (rules.NO_BOX_MIN_CHARS): at
     * least this much feedback, or the request's own if more. It pays half.
     */
    const val NO_BOX_MIN_CHARS = 100

    /** What asking for a new screenshot holds from the owner, and how long the reviewer has. */
    const val RECHECK_FEE = 5
    const val RECHECK_HOURS = 48

    /** The longest text a review may carry, and so a ready-written one (rules.WRITTEN_MAX). */
    const val WRITTEN_MAX = 600

    /** The feed's options version from which a request can ask for written or detailed feedback. */
    const val FEEDBACK_OPTIONS = 3

    /** What going up costs, given what it is offering. */
    fun postCost(
        reward: Int,
        reviews: Int = REVIEWS_BOUGHT,
    ) = reward * reviews

    /** The focus question on the post form: a sentence. */
    const val ASK_MAX = 200

    /** An app's name as typed in the app: 30, so it fits a row. The server still takes up to 60 (PostIn.app_name). */
    const val APP_NAME_MAX = 30

    /** Members who decide a refused review, and what each is paid (rules.JURORS, rules.JUROR_FEE). */
    const val JURORS = 3
    const val JUROR_FEE = 5

    /**
     * What a dispute costs whoever loses it (rules.stake). The owner holds it while the members
     * decide; a reviewer who loses pays it to them instead of being paid for the review.
     */
    const val STAKE = JURORS * JUROR_FEE

    /** What a new account starts with, once per account and installation (rules.STARTER_TIX). */
    const val STARTER_TIX = 120

    /** The longest reason a refusal may give, as the server takes it (RejectIn.reason). */
    const val REFUSE_MAX = 280
}
