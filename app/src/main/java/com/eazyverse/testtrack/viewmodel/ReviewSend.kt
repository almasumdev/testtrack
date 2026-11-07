package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import android.net.Uri
import com.eazyverse.testtrack.data.AuthRepo
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
 * The screenshot goes to the reviewer's Drive once (its id is kept, so trying again does not
 * upload it twice), then it is sent: as the review, or as the new screenshot the owner asked for.
 */
internal suspend fun sendProof(
    activity: Activity,
    job: ReviewJob,
    proof: Uri,
    note: String,
): ApiReview {
    val context = activity.applicationContext
    val token =
        AuthRepo.driveTokenOrNull(activity)
            ?: throw ApiError(400, "drive", "Connect Google Drive to send your saved feedback.")
    val id = job.review.id
    val ref =
        ProofStore.uploaded(context, id)
            ?: (DRIVE_PREFIX + ProofUpload.send(activity, id, job.post.packageName, proof))
                .also { ProofStore.markUploaded(context, id, it) }
    return if (job.rechecking) {
        Api.recheck(id, ref, token)
    } else {
        Api.submit(id, ref, note, driveToken = token, noBox = job.noBox)
    }
}

/** A picture for a dispute, into the sender's own Drive: its ref and the Drive token that proves it is theirs. */
private suspend fun uploadPicture(
    activity: Activity,
    reviewId: String,
    packageName: String,
    picture: Uri,
): Pair<String, String> {
    val token =
        AuthRepo.driveTokenOrNull(activity)
            ?: throw ApiError(400, "drive", "Connect Google Drive to send a screenshot.")
    return (DRIVE_PREFIX + ProofUpload.send(activity, reviewId, packageName, picture)) to token
}

/** Refuse a review, with the owner's picture when they added one. */
internal suspend fun rejectWithPicture(
    context: Context,
    reviewId: String,
    packageName: String,
    reason: String,
    picture: Uri?,
): ApiDispute {
    if (picture == null || context !is Activity) return Api.reject(reviewId, reason)
    val (ref, token) = uploadPicture(context, reviewId, packageName, picture)
    return Api.reject(reviewId, reason, ref, token)
}

/** The reviewer's one answer to a refusal: words, a picture, or both. */
internal suspend fun replyWithPicture(
    activity: Activity,
    job: ReviewJob,
    text: String,
    picture: Uri?,
): ApiReview {
    val id = job.review.id
    if (picture == null) return Api.reply(id, text, "", "")
    val (ref, token) = uploadPicture(activity, id, job.post.packageName, picture)
    return Api.reply(id, text, ref, token)
}
