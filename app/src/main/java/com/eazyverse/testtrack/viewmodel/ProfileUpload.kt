package com.eazyverse.testtrack.viewmodel

import android.graphics.Bitmap
import android.util.Base64
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.PhotoIn
import com.eazyverse.testtrack.net.setPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * A profile photo, cropped square on the phone, sent to TestTrack to keep.
 *
 * It used to go to the account's own Google Drive, which needed Drive connected first and failed
 * without a word when it was not. Now it is one request to our own server, which redraws it and
 * serves it to everybody who sees the account.
 */
object ProfileUpload {
    /** Pixels on a side: what the server keeps, so nothing is sent only to be thrown away. */
    private const val SIDE = 400

    /** Sends [photo] and returns the URL the account's face is now served at. */
    suspend fun photo(photo: Bitmap): String {
        val data = withContext(Dispatchers.Default) { encode(photo) }
        return Api.setPhoto(PhotoIn(data)).photoUrl
    }

    /** [photo] as a [SIDE] JPEG, base64: tens of kilobytes, well under the server's request limit. */
    internal fun encode(photo: Bitmap): String {
        val square = if (photo.width == SIDE && photo.height == SIDE) photo else Bitmap.createScaledBitmap(photo, SIDE, SIDE, true)
        val out = ByteArrayOutputStream()
        square.compress(Bitmap.CompressFormat.JPEG, 85, out)
        if (square !== photo) square.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
