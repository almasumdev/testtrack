package com.eazyverse.testtrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Lucide
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
            icon = Lucide.Camera,
            body =
                "Each app stays open ${CrewRules.OPEN_SECONDS} seconds and its owner gets a picture. " +
                    "Android asks once to share your screen.",
            confirm = "Start",
            dismiss = "Not now",
            onConfirm = { askCapture() },
            onDismiss = { pictures.skip() },
        )
    }
}
