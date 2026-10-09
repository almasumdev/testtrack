package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

    suspend fun save(
        context: Context,
        original: Bitmap,
        masks: List<RectF>,
    ): Uri =
        withContext(Dispatchers.IO) {
            val edited = original.copy(Bitmap.Config.ARGB_8888, true)
            try {
                val paint = Paint().apply { color = Color.BLACK }
                val canvas = Canvas(edited)
                for (mask in masks) {
                    canvas.drawRect(
                        mask.left * edited.width,
                        mask.top * edited.height,
                        mask.right * edited.width,
                        mask.bottom * edited.height,
                        paint,
                    )
                }
                val file = File(context.cacheDir, "proof_edit_${UUID.randomUUID()}.jpg")
                file.writeBytes(ProofImages.jpeg(edited))
                Uri.fromFile(file)
            } finally {
                edited.recycle()
            }
        }
}
