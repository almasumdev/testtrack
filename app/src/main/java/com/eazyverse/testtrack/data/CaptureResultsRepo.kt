package com.eazyverse.testtrack.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray

private var restoredPending = false

/**
 * Re-hydrates [missed] from local storage, once per process.
 *
 * [missed] otherwise lives only in this companion's memory, which does not survive the
 * app being closed, killed by the OS to reclaim memory, or updated — any of which throws
 * away a visit that already happened and already earned its usage time, with no way back
 * short of opening the app again and hoping it fails a second time. Called from the group
 * screen on load; safe to call every time, since after the first call in a process this
 * is a no-op.
 */
fun CaptureService.Companion.restorePending(context: Context) {
    if (restoredPending) return
    restoredPending = true
    val raw =
        context
            .getSharedPreferences(PENDING_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MISSED, null) ?: return
    runCatching {
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            val visit = MissedVisit(o.getString("pkg"), o.getLong("usageMs"))
            if (missed.none { it.pkg == visit.pkg }) missed += visit
        }
    }
}

fun CaptureService.Companion.consume(pkg: String) {
    results.remove(pkg)
}

/** Called once the tester has been told about [newlyMissed], so it is not said twice. */
fun CaptureService.Companion.forgetNewlyMissed() {
    newlyMissed.clear()
}

/**
 * Hands a manually chosen picture to the visit in [missed] it belongs to.
 *
 * The usage figure is not re-earned here, only matched back up — [MissedVisit.usageMs]
 * already came from the real, OS-measured visit; picking a picture replaces the frame that
 * visit's own capture could not save, nothing about whether the tester was there for it.
 *
 * Feeds [results] exactly as an automated capture would, so the same drain-and-upload path
 * in GroupViewModel.publish handles it without knowing the difference.
 *
 * @return false if there is no longer a missed visit for [pkg] to attach this to (it was
 *   retried, or already supplied), or the picked image could not be read.
 */
fun CaptureService.Companion.supplyManualCapture(
    context: Context,
    pkg: String,
    source: Uri,
): Boolean {
    val visit = missed.firstOrNull { it.pkg == pkg } ?: return false
    val bitmap =
        runCatching {
            context.contentResolver.openInputStream(source)?.use(BitmapFactory::decodeStream)
        }.getOrNull() ?: return false

    val file = saveCapture(context, pkg, bitmap)
    bitmap.recycle()
    results[pkg] = Capture(file.absolutePath, visit.usageMs, manual = true)
    missed.remove(visit)
    persistMissed(context)
    return true
}
