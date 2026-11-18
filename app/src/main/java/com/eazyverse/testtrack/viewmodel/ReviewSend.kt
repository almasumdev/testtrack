package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.data.ProofUpload
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiDispute
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.net.recheck
import com.eazyverse.testtrack.net.reply
import com.eazyverse.testtrack.ui.ReviewJob

/**
 * The screenshot goes to TestTrack once (its id is kept, so trying again does not send it twice),
 * then it is sent: as the review, or as the new screenshot the owner asked for.
 */
internal suspend fun sendProof(
    context: Context,
    job: ReviewJob,
    proof: Uri,
    note: String,
): ApiReview {
    val app = context.applicationContext
    val id = job.review.id
    val ref =
        ProofStore.uploaded(app, id)
            ?: ProofUpload.send(app, id, proof).also { ProofStore.markUploaded(app, id, it) }
    if (!InstalledApps.info(app, job.post.packageName).fromPlay) {
        throw ApiError(400, "play_install", "Install the Google Play version to continue.")
    }
    return if (job.rechecking) {
        Api.recheck(id, ref)
    } else {
        Api.submit(id, ref, note, noBox = job.noBox)
    }
}

/** Refuse a review, with the owner's picture when they added one. */
internal suspend fun rejectWithPicture(
    context: Context,
    reviewId: String,
    reason: String,
    picture: Uri?,
): ApiDispute {
    if (picture == null) return Api.reject(reviewId, reason)
    return Api.reject(reviewId, reason, ProofUpload.sendPicture(context, picture))
}

/** The reviewer's one answer to a refusal: words, a picture, or both. */
internal suspend fun replyWithPicture(
    context: Context,
    job: ReviewJob,
    text: String,
    picture: Uri?,
): ApiReview {
    val id = job.review.id
    if (picture == null) return Api.reply(id, text, "")
    return Api.reply(id, text, ProofUpload.sendPicture(context, picture))
}
