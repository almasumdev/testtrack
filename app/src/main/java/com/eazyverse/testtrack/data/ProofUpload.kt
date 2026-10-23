package com.eazyverse.testtrack.data

import android.app.Activity
import android.net.Uri
import com.eazyverse.testtrack.net.ApiError

/**
 * A review's screenshot, put in the reviewer's own Drive and shared by link. The picture belongs to
 * its uploader; only its id and a transient metadata-verification grant reach our server.
 *
 * Without a Drive grant nothing is sent. The page shows how to connect, and the screenshot and note
 * stay kept. The file's id is reserved once and remembered, so trying again does not upload twice.
 */
object ProofUpload {
    /** The Drive file's id. */
    suspend fun send(
        activity: Activity,
        reviewId: String,
        packageName: String,
        uri: Uri,
    ): String {
        val context = activity.applicationContext
        val token = AuthRepo.driveTokenOrNull(activity)
        if (token == null) {
            Session.updateDriveConnected(false)
            throw ApiError(0, "drive", "Connect Google Drive to send. Your screenshot is saved there and shared with the owner by link.")
        }
        val mime = context.contentResolver.getType(uri) ?: ProofStore.mime(context, reviewId)
        val name = "review_${packageName}_${reviewId.take(8)}.${if (mime == "image/png") "png" else "jpg"}"
        val read = {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ApiError(400, "unreadable", "That image could not be read.")
        }
        val fileId =
            ProofStore.reserved(context, reviewId)
                ?: DriveRepo.reserveId(token).also { ProofStore.reserve(context, reviewId, it) }
        return when (val result = DriveRepo.upload(token, name, mime, fileId = fileId, bytes = read)) {
            is UploadResult.Ok -> result.file.id
            is UploadResult.Failed -> throw ApiError(0, "drive", result.reason)
        }
    }
}
