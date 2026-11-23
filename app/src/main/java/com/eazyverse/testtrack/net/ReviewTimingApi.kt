package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The guided review under timing 2: taking one on while asking for it, and reporting the two
 * visits. A live review taken this way runs three minutes, three more a day later, and opens two
 * days after the first three. An older server ignores what it does not know and answers timing 1.
 * The bodies of both calls live here with them; [Api.claim] and [Api.used] send them too.
 */

@Serializable
data class ClaimIn(
    @SerialName("device_fp") val deviceFp: String,
    /** This app shows what a request asks for, so the server lets it take written ones. */
    @SerialName("knows_feedback") val knowsFeedback: Boolean,
    /**
     * The pacing this app knows for a live review (FeedRules.LIVE_TIMING). Left at 1 it is not sent,
     * as kotlinx drops defaults, and the server keeps the review on the old rules.
     */
    val timing: Int = 1,
)

@Serializable
data class UsedIn(
    @SerialName("used_ms") val usedMs: Long,
    /** Calendar days the app was used on since the review was taken. */
    val days: Int = 0,
    /** Timing 2: foreground time since the review's use_again_from. Null until that time has come. */
    @SerialName("step2_ms") val step2Ms: Long? = null,
    /** Timing 2: when this phone saw the first visit's minutes reached (ISO, with a zone). */
    @SerialName("step1_at") val step1At: String? = null,
)

/**
 * Take a review on, asking for the pacing this build knows ([timing], FeedRules.LIVE_TIMING). A
 * closed test stays on its own rules whatever is asked.
 */
suspend fun Api.claimTimed(
    postId: String,
    deviceFp: String,
    timing: Int,
): ApiReview = post("/feed/posts/$postId/claim", ClaimIn(deviceFp, knowsFeedback = true, timing = timing))

/**
 * Real foreground time so far, with timing 2's figures: [step2Ms] since the review's
 * use_again_from, and [step1At] when this phone saw the first visit's minutes reached. Either is
 * left out when this phone does not know it.
 */
suspend fun Api.usedTimed(
    reviewId: String,
    usedMs: Long,
    days: Int,
    step2Ms: Long?,
    step1At: String?,
): ApiReview = post("/reviews/$reviewId/used", UsedIn(usedMs, days, step2Ms, step1At))
