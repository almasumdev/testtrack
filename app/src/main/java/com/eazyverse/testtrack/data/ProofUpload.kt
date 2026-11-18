package com.eazyverse.testtrack.data

import android.content.Context
import android.net.Uri
import com.eazyverse.testtrack.net.ApiError

/**
 * A Circle review's pictures, sent to TestTrack's own server: the screenshot of the review, and a
 * picture added to a refusal or to the answer to one. Only the people in that review can open them.
 */
object ProofUpload {
    /**
     * The review's kept screenshot, and the id it is now under.
     *
     * The id is made once and remembered with the screenshot ([ProofStore.reserve]), so trying again
     * sends the same picture under the same id and nothing is kept twice. An id remembered from
     * before pictures came to TestTrack is not one of ours and a new one is made.
     */
    suspend fun send(
        context: Context,
        reviewId: String,
        uri: Uri,
    ): String {
        val app = context.applicationContext
        val id =
            ProofStore.reserved(app, reviewId)
                ?: ServerUpload.newId().also { ProofStore.reserve(app, reviewId, it) }
        // The kept screenshot is a JPEG already. Any other type is redrawn as one, which the server takes.
        val type = app.contentResolver.getType(uri) ?: ProofStore.mime(app, reviewId)
        val asIs = type in ServerUpload.TYPES
        return ServerUpload.send(
            id = id,
            kind = ServerUpload.REVIEW,
            mime = if (asIs) type else JPEG,
            remint = { ProofStore.reserve(app, reviewId, it) },
        ) {
            if (asIs) app.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw unreadable() else redrawn(app, uri)
        }
    }

    /**
     * A picture that goes with a refusal or an answer, straight from the phone's gallery: redrawn
     * smaller and without what the camera wrote into it, under an id of its own each time it is sent.
     */
    suspend fun sendPicture(
        context: Context,
        uri: Uri,
    ): String {
        val app = context.applicationContext
        return ServerUpload.send(ServerUpload.newId(), ServerUpload.REVIEW, JPEG) { redrawn(app, uri) }
    }

    private fun redrawn(
        app: Context,
        uri: Uri,
    ): ByteArray = runCatching { ProofImages.bytes(app, uri) }.getOrElse { throw unreadable() }

    private fun unreadable() = ApiError(400, "unreadable", "That image could not be read.")

    private const val JPEG = "image/jpeg"
}
