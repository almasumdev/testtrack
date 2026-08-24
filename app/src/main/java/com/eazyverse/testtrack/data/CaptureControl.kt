package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.content.ContextCompat

fun CaptureService.Companion.startSession(
    context: Context,
    code: Int,
    data: Intent,
) {
    status = "Starting…"
    send(
        context,
        Intent(context, CaptureService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_CODE, code)
            putExtra(EXTRA_DATA, data)
        },
    )
}

/**
 * Runs a round: each app in turn, [VISIT_MS] apiece, frame grabbed at an unannounced
 * moment inside each visit, no stop back at the list in between. A round of one is how a
 * single app's Open button works, so both paths behave identically.
 */
fun CaptureService.Companion.startRound(
    context: Context,
    packages: List<String>,
    notes: Map<String, String> = emptyMap(),
) {
    if (packages.isEmpty()) return
    send(
        context,
        Intent(context, CaptureService::class.java).apply {
            action = ACTION_ROUND
            putStringArrayListExtra(EXTRA_QUEUE, ArrayList(packages))
            if (notes.isNotEmpty()) {
                putExtra(EXTRA_NOTES, Bundle().apply { notes.forEach { (pkg, note) -> putString(pkg, note) } })
            }
        },
    )
}

/** Abandons whatever is left of the round and comes back. */
fun CaptureService.Companion.abortRound(context: Context) {
    if (!roundActive) return
    send(context, Intent(context, CaptureService::class.java).apply { action = ACTION_ABORT })
}

/**
 * The tester came back to TestTrack in the middle of a round, so the round is over.
 *
 * A round is meant to be one uninterrupted stretch, and the service is deliberately deaf
 * to where the tester actually is — it opens the next app on a timer whether or not
 * anybody is still following it. Walking back here used to leave it running, so the next
 * app would open a few seconds later and take the phone off them again.
 *
 * Safe to call on every resume. `roundActive` is already false by the time a round that
 * ended on its own brings the app forward, so this only ever fires on a departure.
 */
fun CaptureService.Companion.leftRound(context: Context) {
    if (!roundActive) return
    send(
        context,
        Intent(context, CaptureService::class.java).apply {
            action = ACTION_ABORT
            putExtra(EXTRA_RETURN, false)
        },
    )
}

/**
 * Ends screen sharing. A no-op when there is nothing to end — starting a service purely to
 * stop it is wasteful, and it is the path that used to crash.
 */
fun CaptureService.Companion.endSession(context: Context) {
    if (!sessionActive) return
    send(context, Intent(context, CaptureService::class.java).apply { action = ACTION_STOP })
}

private fun send(
    context: Context,
    intent: Intent,
) = ContextCompat.startForegroundService(context, intent)
