package com.eazyverse.testtrack.fixture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil.compose.AsyncImage
import com.eazyverse.testtrack.ui.ProofEditor
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import java.io.File

/** Local UI/bitmap fixture only. No network, account, backend, Drive or production build path. */
class ProofFixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val file = File(cacheDir, "fictional-proof.jpg")
        val bitmap = Bitmap.createBitmap(800, 1400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 36f
            }
        canvas.drawText("FICTIONAL TEST SCREEN", 50f, 100f, paint)
        canvas.drawText("Email: fixture@example.com", 50f, 250f, paint)
        canvas.drawText("Private note: hide this line", 50f, 350f, paint)
        canvas.drawText("Total: 42.00", 50f, 600f, paint)
        canvas.drawText("All fixture content is invented.", 50f, 1150f, paint)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        setContent {
            TestTrackTheme {
                var editing by remember { mutableStateOf(true) }
                var image by remember { mutableStateOf(Uri.fromFile(file)) }
                if (editing) {
                    ProofEditor(image, onKeep = { image = it }, onClose = { editing = false })
                } else {
                    Column {
                        Text("Saved local fixture")
                        AsyncImage(image, "Edited fictional screenshot", Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
