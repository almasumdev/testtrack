package com.eazyverse.testtrack.net

import kotlinx.serialization.Serializable

/** A new profile photo: a square JPEG, base64, which the server redraws and keeps. */
@Serializable
data class PhotoIn(
    val data: String,
)

/** A new face for this account, kept by TestTrack; the answer carries the URL it is served at. */
suspend fun Api.setPhoto(body: PhotoIn): Me = put("/me/photo", body)
