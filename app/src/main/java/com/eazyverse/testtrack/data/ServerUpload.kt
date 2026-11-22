package com.eazyverse.testtrack.data

import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.upload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * A picture, sent to TestTrack's own server to keep.
 *
 * The phone makes up the picture's id and the caller writes it down before anything is sent, so a
 * try that dies half way is repeated under the same id and the server answers as if it were the
 * first. The id is then what the review, the visit or the feedback is sent with as its proof.
 */
object ServerUpload {
    const val VISIT = "visit"
    const val CREW_FEEDBACK = "crew_feedback"
    const val REVIEW = "review"

    /** A picture in a crew's chat, kept a day. Sent with the sender's seat, or the crew when they have none. */
    const val CHAT = "chat"

    /** The two types the server takes. Anything else is redrawn as a JPEG before it goes. */
    val TYPES = setOf("image/jpeg", "image/webp")

    /** The server's disk is nearly full. A picture in the night's queue waits on the phone and goes later. */
    const val FULL = "Pictures can't be saved right now. They'll send by themselves later."

    /** The same, for a picture the person sends by hand, which nothing sends again for them. */
    const val FULL_NOW = "Pictures can't be saved right now. Try again later."

    /** This copy of TestTrack is too old for the server to take pictures from. */
    const val UPDATE = "Update TestTrack to send this picture."

    private const val TOO_BIG = "That picture is too big. Choose a smaller one."
    private const val NOT_A_PICTURE = "That picture can't be used. Choose another."
    private const val LATER = "That picture couldn't be sent. Try again in a moment."

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Whether [value] is an id of ours. An id kept from before pictures came to TestTrack is not,
     * and is treated as none: a new one is made and the picture sent again from the phone.
     */
    fun isId(value: String?): Boolean = value != null && value.length == 36 && runCatching { UUID.fromString(value) }.isSuccess

    /**
     * Send [bytes] under [id] and answer with the id the picture is now kept under.
     *
     * That is [id] itself, unless the server says the id is taken: then one new id is made, handed
     * to [remint] to be written down, and the picture sent once more under it. A refusal is thrown
     * as an [ApiError] carrying a line a person can read.
     */
    suspend fun send(
        id: String,
        kind: String,
        mime: String,
        seatId: String? = null,
        /** A chat picture's crew, for a sender with no seat in it. */
        crewId: String? = null,
        remint: (String) -> Unit = {},
        bytes: () -> ByteArray,
    ): String {
        val data = withContext(Dispatchers.IO) { bytes() }
        try {
            return Api.upload(id, kind, data, mime, seatId, crewId).id.ifBlank { id }
        } catch (e: ApiError) {
            if (!taken(e)) throw e.readable()
        }
        val fresh = newId().also(remint)
        try {
            return Api.upload(fresh, kind, data, mime, seatId, crewId).id.ifBlank { fresh }
        } catch (e: ApiError) {
            // Taken twice cannot be the id's fault. Never read as a final no: it is tried again.
            throw if (taken(e)) ApiError(0, e.code, LATER) else e.readable()
        }
    }

    /** Why a picture in the night's queue is still waiting: it goes by itself, so the line says so. */
    fun queuedLine(e: ApiError): String = if (full(e)) FULL else line(e)

    /** What to tell the person about a picture the server would not take. */
    private fun line(e: ApiError): String =
        when {
            full(e) -> FULL_NOW
            e.status == 426 || e.code == "update_app" -> UPDATE
            e.status == 413 -> TOO_BIG
            e.status == 415 -> NOT_A_PICTURE
            else -> e.message
        }

    private fun full(e: ApiError) = e.status == 507 || e.code == "storage_full"

    private fun ApiError.readable() = ApiError(status, code, line(this), details)

    /**
     * The id is taken: the only 409 that a new id helps. Any other 409 (a crew that was pruned, a
     * chat that closed) is final and is thrown with its status, so the queue drops it and moves on.
     */
    internal fun taken(e: ApiError) = e.status == TAKEN && e.code == TAKEN_CODE

    private const val TAKEN = 409
    private const val TAKEN_CODE = "upload_taken"
}
