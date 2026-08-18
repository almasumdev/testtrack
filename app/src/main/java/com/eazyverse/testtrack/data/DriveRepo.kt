package com.eazyverse.testtrack.data

import android.net.Uri
import com.eazyverse.testtrack.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/** A proof image stored in the tester's own Drive. */
data class DriveFile(val id: String, val name: String) {
    /** Viewable by anyone with the link, once [DriveRepo.makeLinkReadable] has run. */
    val viewUrl: String get() = "https://drive.google.com/file/d/$id/view"

    /** Direct image bytes — what Coil should load in the grid. */
    val thumbUrl: String get() = "https://drive.google.com/thumbnail?id=$id&sz=w1200"
}

sealed interface UploadResult {
    data class Ok(val file: DriveFile) : UploadResult
    data class Failed(val reason: String) : UploadResult
}

/**
 * Uploads proof screenshots to the signed-in tester's own Google Drive using the narrow
 * `drive.file` scope, which only ever grants access to files this app created. Non-sensitive, so
 * no CASA assessment and no paid verification — unlike `drive` or `drive.readonly`.
 *
 * Trade-off to remember: the tester owns these files. They can delete one or revoke access, so
 * the file id is stored in Firestore and a missing image shows as a broken cell rather than
 * silently passing.
 */
object DriveRepo {

    private const val UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
    private const val FILES_URL = "https://www.googleapis.com/drive/v3/files"

    /** Finds or creates the app's folder. `drive.file` can see folders this app made. */
    private fun folderId(accessToken: String): String {
        val query = "mimeType='application/vnd.google-apps.folder' and " +
            "name='${Config.DRIVE_FOLDER}' and trashed=false"

        val lookup = Request.Builder()
            .url("$FILES_URL?q=${Uri.encode(query)}&fields=files(id,name)&spaces=drive")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        GroupGate.http.newCall(lookup).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (response.isSuccessful) {
                val files: JSONArray = JSONObject(body).optJSONArray("files") ?: JSONArray()
                if (files.length() > 0) return files.getJSONObject(0).getString("id")
            }
        }

        val metadata = JSONObject()
            .put("name", Config.DRIVE_FOLDER)
            .put("mimeType", "application/vnd.google-apps.folder")

        val create = Request.Builder()
            .url("$FILES_URL?fields=id")
            .header("Authorization", "Bearer $accessToken")
            .post(metadata.toString().toRequestBody("application/json".toMediaType()))
            .build()

        GroupGate.http.newCall(create).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("folder create failed: ${response.code} $body")
            return JSONObject(body).getString("id")
        }
    }

    /**
     * Grants read access to anyone holding the link, so the app owner can view the proof.
     * Not optional: without it the file stays private to the tester and the grid shows nothing.
     */
    private fun makeLinkReadable(accessToken: String, fileId: String) {
        val body = JSONObject().put("role", "reader").put("type", "anyone")
        val request = Request.Builder()
            .url("$FILES_URL/$fileId/permissions")
            .header("Authorization", "Bearer $accessToken")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        GroupGate.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("permission failed: ${response.code} ${response.body?.string().orEmpty()}")
            }
        }
    }

    /** Uploads an already-compressed JPEG written by the capture service. */
    suspend fun upload(accessToken: String, file: File, fileName: String): UploadResult =
        withContext(Dispatchers.IO) {
            try {
                val parent = folderId(accessToken)

                val metadata = JSONObject()
                    .put("name", fileName)
                    .put("parents", JSONArray().put(parent))

                val multipart = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(metadata.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                    .addPart(file.readBytes().toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val request = Request.Builder()
                    .url("$UPLOAD_URL&fields=id,name")
                    .header("Authorization", "Bearer $accessToken")
                    .post(multipart)
                    .build()

                GroupGate.http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext UploadResult.Failed(refusal(response.code, body))
                    }

                    val json = JSONObject(body)
                    val uploaded = DriveFile(json.getString("id"), json.optString("name", fileName))
                    makeLinkReadable(accessToken, uploaded.id)
                    UploadResult.Ok(uploaded)
                }
            } catch (e: Exception) {
                UploadResult.Failed(broke(e))
            }
        }

    /**
     * Why Drive turned an upload down, in the tester's terms.
     *
     * The two that matter are told apart because the answers are opposites: a full Drive is
     * something only they can fix and retrying will never help, and a stale token fixes itself
     * from Setup. Everything else keeps its status code, which is the one part of the raw body
     * worth carrying — it is what makes a report actionable when somebody sends a screenshot.
     */
    private fun refusal(code: Int, body: String): String = when {
        code == 401 || code == 403 && body.contains("authError", true) ->
            "Your Drive access has expired. Open Setup from the home screen and reconnect it."
        body.contains("storageQuota", true) || body.contains("quotaExceeded", true) ->
            "Your Google Drive is full, so the screenshot couldn't be saved. Free some space and " +
                "open the app again."
        code == 429 || code >= 500 ->
            "Drive is busy. Try opening the app again in a minute. ($code)"
        else -> "Drive wouldn't take the screenshot. ($code)"
    }

    /**
     * Why the upload never reached Drive.
     *
     * This used to be `e.message`, which is how somebody testing a shopping app came to be shown
     * `/data/user/0/com.eazyverse.testtrack/files/captures/com.sylhetlink.provider_178697…jpg:
     * open failed: ENOENT (No such file or directory)` in red. Every word of it was true and none
     * of it was theirs to do anything about.
     */
    private fun broke(e: Exception): String = when (e) {
        is FileNotFoundException ->
            "That screenshot didn't survive long enough to be saved. Open the app again."
        is IOException -> "Can't reach Drive. Check your connection and try again."
        else -> "We couldn't save that screenshot. Open the app again."
    }
}
