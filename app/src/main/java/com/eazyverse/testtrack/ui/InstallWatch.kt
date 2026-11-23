package com.eazyverse.testtrack.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * [onInstalled] each time an app lands on the phone (a fresh install, or a new copy over the old
 * one) while this is composed. With [packageName], only that app.
 *
 * Resume alone is not enough: Play installs in the background, so somebody who taps Install there
 * and comes straight back is looked at before the app exists, and nothing looked again. Listening
 * only while the page is open costs nothing the rest of the time. Android only tells us about apps
 * the manifest's `<queries>` lets us see, which is every app with a launcher entry.
 */
@Composable
fun WatchInstalls(
    packageName: String? = null,
    onInstalled: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val landed by rememberUpdatedState(onInstalled)
    DisposableEffect(context, packageName) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context,
                    intent: Intent,
                ) {
                    // A new copy over an old one also sends REPLACED: answer that one only.
                    if (intent.action == Intent.ACTION_PACKAGE_ADDED && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return
                    if (packageName != null && intent.data?.schemeSpecificPart != packageName) return
                    landed()
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
        // Sent by the system, which a receiver closed to other apps still hears.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
}
