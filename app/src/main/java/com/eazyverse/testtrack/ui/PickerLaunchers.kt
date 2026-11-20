package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/*
 * The system screens a page hands over to and gets an answer back from: the photo picker, and the
 * ask to record the screen.
 */

/**
 * The system photo picker, images only. Answers what a tap does: open the picker. [onPicked] gets
 * the picture, or null when the reader backs out without one.
 */
@Composable
internal fun rememberImagePicker(onPicked: (Uri?) -> Unit): () -> Unit {
    val picked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> picked(uri) }
    return remember(launcher) { { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}

/**
 * Android's ask to record the whole screen. Answers what a tap does: show the ask. [onGranted] gets
 * the result code and data the capture service starts from; [onRefused] runs when the reader says
 * no or backs out.
 */
@Composable
internal fun rememberCaptureConsent(
    onGranted: (resultCode: Int, data: Intent) -> Unit,
    onRefused: () -> Unit = {},
): () -> Unit {
    val context = LocalContext.current
    val granted by rememberUpdatedState(onGranted)
    val refused by rememberUpdatedState(onRefused)
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) granted(result.resultCode, data) else refused()
        }
    return remember(consent, context) { { consent.launch(wholeScreenCapture(context)) } }
}
