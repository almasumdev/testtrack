package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.eazyverse.testtrack.findActivity

/*
 * What a night's round asks before it starts, now that each visit counts with its picture:
 * Android's screen-share yes, every night.
 */

@Composable
internal fun CrewPictureAsks(vm: CrewViewModel) {
    val activity = LocalContext.current.findActivity()
    val pictures = vm.pictures

    // Android's own question comes after ours. A no leaves everything as it was.
    val askCapture =
        rememberCaptureConsent(
            onGranted = { code, data -> pictures.start(activity, code, data) },
            onRefused = {
                pictures.skip()
                Snacks.info("Tonight needs a picture of each app. Tap Open all when you're ready.")
            },
        )

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
            onConfirm = { askCapture() },
            onDismiss = { pictures.skip() },
        )
    }
}
