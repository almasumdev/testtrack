package com.eazyverse.testtrack.net

import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** What the server kept of a picture sent to it. */
@Serializable
data class UploadOut(
    val id: String = "",
    val kind: String = "",
    val width: Int = 0,
    val height: Int = 0,
)

/**
 * A picture's bytes, onto our own server under an id the phone made up and wrote down first.
 *
 * Sending the same id again answers the same way, so a try that died half way is simply repeated.
 * [kind] is `visit`, `crew_feedback` or `review`; [seatId] is the app's seat for the two crew kinds.
 */
suspend fun Api.upload(
    id: String,
    kind: String,
    bytes: ByteArray,
    mime: String,
    seatId: String? = null,
): UploadOut {
    val seat = seatId?.let { "&seat_id=$it" }.orEmpty()
    return send(Request.Builder().url(url("/uploads/$id?kind=$kind$seat")).put(bytes.toRequestBody(mime.toMediaType())))
}

/** The picture of an app, onto the visit it was taken on: what makes that visit count. */
suspend fun Api.visitPicture(
    seatId: String,
    picture: PictureIn,
): ApiSeat = post("/crews/seats/$seatId/visit/picture", picture)

/** Every member's pictures of your own app in this crew, night by night. */
suspend fun Api.crewPictures(crewId: String): ApiCrewPictures = get("/crews/$crewId/pictures")

/** The owner tells the crew a newer version is out, read off the copy Play put on their phone. */
suspend fun Api.tellUpdate(
    seatId: String,
    version: VersionIn,
): ApiSeat = post("/crews/seats/$seatId/version", version)

/** A member's private feedback on Play for one app, with its picture: once a run. */
suspend fun Api.sendFeedback(
    seatId: String,
    feedback: FeedbackIn,
): ApiSeat = post("/crews/seats/$seatId/feedback", feedback)

/** The owner marks their app down tonight, so nobody owes it. */
suspend fun Api.markDown(seatId: String): ApiSeat = post("/crews/seats/$seatId/down", emptyMap<String, String>())

/** The owner of an app taken out of its run asks, once, to get it back. */
suspend fun Api.appeal(
    seatId: String,
    text: String,
): ApiSeat = post("/crews/seats/$seatId/appeal", AppealIn(text))
