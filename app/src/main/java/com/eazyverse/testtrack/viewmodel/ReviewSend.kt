package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.net.Uri
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.ProofStore
import com.eazyverse.testtrack.data.ProofUpload
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.net.recheck
import com.eazyverse.testtrack.ui.DRIVE_PREFIX
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
