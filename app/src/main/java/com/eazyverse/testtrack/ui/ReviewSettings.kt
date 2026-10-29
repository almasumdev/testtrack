package com.eazyverse.testtrack.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/*
 * The review page's small reads of the phone and of what it remembers, beside its view model so
 * the class itself stays the part that talks to the server.
 */

private const val GUIDE_SEEN = "guide_seen"

private fun prefs(context: Context) = context.getSharedPreferences("review_prefs", Context.MODE_PRIVATE)

/** Whether the one-time guide has been read. */
fun ReviewViewModel.guideSeen(context: Context) = prefs(context).getBoolean(GUIDE_SEEN, false)

fun ReviewViewModel.markGuideSeen(context: Context) = prefs(context).edit().putBoolean(GUIDE_SEEN, true).apply()

/** The camera button floats over the tested app, requiring "Display over other apps". */
fun ReviewViewModel.canFloat(context: Context) = Settings.canDrawOverlays(context)

fun ReviewViewModel.openOverlaySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A request opened and not yet taken: nothing on the server, nothing on this phone. */
val ReviewViewModel.previewing: Boolean get() = job?.review?.id?.isEmpty() == true
