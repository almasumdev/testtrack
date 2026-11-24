package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * A Circle review's second chances, kept apart from the main calls: the owner taking back a refusal
 * while members decide, and the new screenshot asked for when the owner can't find it on Play.
 */

/** Accept a review you refused, while the members are still deciding it. */
suspend fun Api.acceptLater(reviewId: String): ApiReview = post("/reviews/$reviewId/accept", Empty())

/** The owner cannot find it on Play: ask its reviewer for a new screenshot. Once per review. */
suspend fun Api.check(reviewId: String): ApiReview = post("/reviews/$reviewId/check", Empty())

/** The reviewer's new screenshot of their review on Play. */
suspend fun Api.recheck(
    reviewId: String,
    proofRef: String,
): ApiReview = post("/reviews/$reviewId/recheck", RecheckIn(proofRef))

/** The reviewer's one answer to a refusal: words, a picture, or both. */
suspend fun Api.reply(
    reviewId: String,
    text: String,
    proofRef: String,
): ApiReview = post("/reviews/$reviewId/reply", ReplyIn(text, proofRef))

/** The reviewer's one swapped screenshot, with an optional note of up to 280 characters. */
@Serializable
data class UpdateIn(
    @SerialName("proof_ref") val proofRef: String,
    val note: String = "",
)

/** Swap the screenshot of a sent review for a new one, once, until the owner answers. */
suspend fun Api.updateProof(
    reviewId: String,
    proofRef: String,
    note: String,
): ApiReview = post("/reviews/$reviewId/update", UpdateIn(proofRef, note))
