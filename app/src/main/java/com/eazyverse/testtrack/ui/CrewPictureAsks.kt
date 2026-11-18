package com.eazyverse.testtrack.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.eazyverse.testtrack.findActivity

/*
 * What a night's round asks before it starts, now that each visit counts with its picture:
 * Android's screen-share yes, every night.
 */

@Composable
internal fun CrewPictureAsks(vm: CrewViewModel) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val pictures = vm.pictures

    // Android's own question comes after ours. A no leaves everything as it was.
    val consent =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                pictures.start(activity, result.resultCode, data)
            } else {
                pictures.skip()
                pictures.tell(
                    "Tonight needs a picture of each app, so TestTrack has to see your screen while " +
                        "they're open. Tap Open all when you're ready.",
                )
            }
        }

    pictures.waitingFor?.let { stops ->
        Ask(
            title = "A picture of each app",
            body =
                "Each app is open for ${CrewRules.OPEN_SECONDS} seconds, with a picture taken along the way. " +
                    "Each app's picture goes to TestTrack so its owner can see it. Android asks " +
                    "next whether TestTrack can see your screen. Say yes once and it covers " +
                    "${if (stops.size == 1) "this app" else "all ${stops.size} tonight"}.",
            confirm = "Start",
            dismiss = "Not now",
            onConfirm = { consent.launch(wholeScreenCapture(context)) },
            onDismiss = { pictures.skip() },
        )
    }
}
