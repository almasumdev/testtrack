package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.net.Uri
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.DriveRepo
import com.eazyverse.testtrack.data.ProofImages
import com.eazyverse.testtrack.data.UploadResult
import com.eazyverse.testtrack.net.ApiError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileUpload {
    suspend fun photo(
        activity: Activity,
        uri: Uri,
    ): Pair<String, String> {
        val token =
            AuthRepo.driveTokenOrNull(activity)
                ?: throw ApiError(400, "drive", "Connect Google Drive in your profile before choosing a new photo.")
        val bytes = withContext(Dispatchers.IO) { ProofImages.bytes(activity, uri) }
        return when (
            val upload =
                DriveRepo.upload(token, "profile.jpg", "image/jpeg") { bytes }
        ) {
            is UploadResult.Ok -> ("drive:" + upload.file.id) to token
            is UploadResult.Failed -> throw ApiError(400, "drive", upload.reason)
        }
    }
}
