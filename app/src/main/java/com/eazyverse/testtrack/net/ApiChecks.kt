package com.eazyverse.testtrack.net

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
    driveToken: String,
): ApiReview = post("/reviews/$reviewId/recheck", RecheckIn(proofRef, driveToken))
