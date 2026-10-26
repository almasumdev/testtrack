package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * Open an app's Play listing.
 *
 * The market scheme first, which lands inside the Play app where the review sheet actually is.
 * The web address is the fallback for a device with no Play, where at least the page loads.
 */
fun openPlay(
    context: Context,
    packageName: String,
) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
    val web =
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName"),
        )
    runCatching { context.startActivity(market) }.onFailure {
        runCatching { context.startActivity(web) }
    }
}

/** Open the app being reviewed, if it is there. */
fun openApp(
    context: Context,
    packageName: String,
) {
    val launch = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launch != null) {
        runCatching { context.startActivity(launch) }
    } else {
        openPlay(context, packageName)
    }
}

/** Giving a review up for good, said plainly before it happens. */
@Composable
fun GiveUpDialog(
    onGiveUp: () -> Unit,
    onDismiss: () -> Unit,
) {
    Ask(
        title = "Give this review up?",
        body = "The slot goes back for somebody else, and you can't take this one again.",
        confirm = "Give it up",
        destructive = true,
        onConfirm = onGiveUp,
        onDismiss = onDismiss,
    )
}

/**
 * Asking to capture the whole screen, by name: on Android 14 and later the default is "share one
 * app", which adds an app picker to a one-tap screenshot.
 */
fun wholeScreenCapture(context: Context): Intent {
    val projections = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        projections.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
    } else {
        projections.createScreenCaptureIntent()
    }
}
