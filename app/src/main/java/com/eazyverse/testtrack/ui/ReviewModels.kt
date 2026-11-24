package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.instantOf
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
                // The owner could not find it on Play: the proof step again, for a new screenshot.
                "checking" -> PROVE
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
    /** Play showed no review box, so it is being sent here instead, for half. */
    val noBox: Boolean = review.noBox,
    /** A fresh phone check failed. Blocks the local flow without changing the server's review state. */
    val requiresPlayInstall: Boolean = false,
    /** A new screenshot is being sent for a review already sent. */
    val updating: Boolean = false,
) {
    /** The first day of use: the phone's own record where it has one, else what the server heard. */
    private val firstUse: java.time.Instant?
        get() = firstUsedHere?.let { java.time.Instant.ofEpochMilli(it) } ?: instantOf(review.firstUsedAt)

    /** Characters of text this review must carry: 0 for stars alone, and when showing it again. */
    val needsChars: Int
        get() {
            val asked = maxOf(post.needsChars, review.needsChars, FeedRules.Feedback.of(post.feedback).chars)
            return when {
                rechecking || updating -> 0
                noBox -> maxOf(FeedRules.NO_BOX_MIN_CHARS, asked)
                else -> asked
            }
        }

    /** Refused, with the members still deciding and the reviewer not yet answered: they may answer once. */
    val canReply: Boolean get() = review.state == "disputed" && review.repliedAt == null

    /** The owner could not find it on Play and is waiting for a new screenshot. */
    val rechecking: Boolean get() = review.state == "checking"

    /** The reviewer may swap the screenshot once, until the owner answers. */
    val canUpdate: Boolean get() = review.canUpdate && review.state == "submitted"

    /** By when that new screenshot has to come. */
    val checkBy: java.time.Instant? get() = instantOf(review.checkBy)

    /** A claimed request came back from Play without a verified install. A fresh preview cannot report. */
    val canReportInstall: Boolean get() = review.id.isNotEmpty() && review.state == "claimed" && requiresPlayInstall

    /** An installed review holds a slot until submitted; rechecks are already the developer's turn. */
    val canGiveUp: Boolean get() = review.id.isNotEmpty() && review.state in setOf("installed", "used")

    /** The text typed so far is long enough to send. */
    fun textEnough(note: String): Boolean = plainLength(note) >= needsChars

    val step: ReviewStep
        get() {
            if (requiresPlayInstall && !settled) return ReviewStep.INSTALL
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
        get() = (((review.neededMs - review.usedMs).coerceAtLeast(0) + 999) / 1000).toInt()

    val live: Boolean get() = post.isLive

    /** The amount shown before and after submission must agree with the chosen feedback route. */
    val reward: Int get() = if (noBox) post.reward / 2 else post.reward

    /**
     * A live review on timing 2, as the server says: two visits of a few minutes a day apart, and
     * the review opening two days after the first. False from an older server, which keeps timing 1.
     */
    val paced: Boolean get() = live && review.timing >= FeedRules.LIVE_TIMING

    /** Timing 2: the second visit's time is in. */
    val secondVisitDone: Boolean get() = review.step2NeededMs > 0 && review.step2Ms >= review.step2NeededMs

    /** Timing 2: seconds of the second visit still to go. */
    val secondVisitSecondsLeft: Int
        get() = (((review.step2NeededMs - review.step2Ms).coerceAtLeast(0) + 999) / 1000).toInt()

    /** Timing 2: from when the second visit counts. */
    val useAgainFrom: java.time.Instant? get() = instantOf(review.useAgainFrom)

    /**
     * A live review with its first visit done, still waiting on its second: a second day of use under
     * timing 1, the second visit and the hours under timing 2.
     */
    val waitingForDay: Boolean
        get() {
            if (review.state != "installed") return false
            return if (paced) review.step1DoneAt != null || secondsLeft == 0 else live && secondsLeft == 0
        }

    /**
     * Used on two days already, so only the hours since it was taken are still to pass. Under timing 2,
     * the second visit is done, so only the hours since the first are.
     */
    val usedOnTwoDays: Boolean get() = if (paced) secondVisitDone else review.daysUsed >= 2

    val settled: Boolean
        get() = review.state in setOf("submitted", "approved", "rejected", "disputed", "abandoned", "expired")

    /**
     * While [waitingForDay] and not yet used on two days: today is already another calendar day
     * than the first use, so opening the app now is the second day's use. Under timing 2, the hours
     * after the first visit have passed, so using the app now counts as the second visit.
     */
    fun secondDayNow(
        now: java.time.Instant = java.time.Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ): Boolean {
        if (!waitingForDay || usedOnTwoDays) return false
        if (paced) return useAgainFrom?.let { !now.isBefore(it) } ?: false
        val first = firstUse ?: return false
        return first.atZone(zone).toLocalDate() < now.atZone(zone).toLocalDate()
    }

    /**
     * The earliest a live review waiting on its second day can open, as the server rules it:
     * used again [LIVE_RETURN_HOURS] after its first use, or used on a second calendar day once
     * that long has passed since it was taken. Null when it is not waiting, or a time is missing.
     *
     * Under timing 2, by the server's own times in elapsed hours rather than calendar days: when the
     * second visit may start while it is still to do, then when the review opens.
     */
    fun opensAt(zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): java.time.Instant? {
        if (!waitingForDay) return null
        if (paced) return if (secondVisitDone) instantOf(review.reviewFrom) else useAgainFrom
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
        /** rules.LIVE_RETURN_HOURS on the server: timing 1 only. */
        const val LIVE_RETURN_HOURS = FeedRules.LIVE_RETURN_HOURS
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
        // This build asks for timing 2 on a live request, so its rules are the ones shown before it
        // is taken. The review the server sends back says what it really runs on.
        timing = if (post.isLive) FeedRules.LIVE_TIMING else 1,
    )

/** Whether a time the server sent has come. */
internal fun isPast(iso: String): Boolean = instantOf(iso)?.let { it.toEpochMilli() <= System.currentTimeMillis() } ?: false

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

/** What a review is worth saying about itself once it is out of the reviewer's hands. */
fun reviewOutcome(
    state: String,
    live: Boolean = false,
    /** Why it ended, where its reviewer did not give it up. */
    endedReason: String? = null,
    /** A timing 2 review whose first visit was done: it ran out four days from that visit. */
    afterFirstVisit: Boolean = false,
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
            when {
                afterFirstVisit ->
                    "It wasn't sent within ${FeedRules.LIVE_FINISH_HOURS / 24} days of your first visit, so the slot went back."
                live -> "It sat unfinished for three days, so the slot went back."
                else -> "It sat unfinished for a day, so the slot went back."
            }
        else -> ""
    }

/** What a post carries when the developer asked nothing in particular; not worth showing. */
internal const val GENERAL_ASK = "General feedback on the app experience."

internal val ReviewSteps = ReviewStep.entries.filter { it != ReviewStep.WAIT }
