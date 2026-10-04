package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
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
