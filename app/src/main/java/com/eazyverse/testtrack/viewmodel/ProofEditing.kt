package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.eazyverse.testtrack.data.ProofImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object ProofEditing {
    suspend fun load(
        context: Context,
        uri: Uri,
    ): Bitmap =
        withContext(Dispatchers.IO) {
            ProofImages.decode(context, uri)
        }

    /**
     * Writes an edited screenshot to the cache as a JPEG and answers its file. [edited] already has
     * its hidden boxes burned into the pixels (the editor's flatten does that); a JPEG from a bitmap
     * carries pixels only, so no EXIF or other metadata from the original goes with it. [edited] is
     * left for the caller to recycle.
     */
    suspend fun save(
        context: Context,
        edited: Bitmap,
    ): Uri =
        withContext(Dispatchers.IO) {
            val file = File(context.cacheDir, "proof_edit_${UUID.randomUUID()}.jpg")
            file.writeBytes(ProofImages.jpeg(edited))
            Uri.fromFile(file)
        }
}
