package com.eazyverse.testtrack.data

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * What the app says about itself once it is on somebody else's phone.
 *
 * Everything here is about the round, and nothing here is about the person. No uid, no address, no
 * package name of anything they have installed, no app they were testing. A cohort is people
 * checking on each other's work, and telemetry that named who did what would be a different
 * product to the one described in the README.
 *
 * Two halves, and they answer different questions:
 *
 *  - **Crashlytics** takes the failures the app swallows on purpose. Nearly every failure in a
 *    round is caught and turned into a sentence the tester can act on, which is right for them and
 *    leaves nobody any way to know it happened. A landscape crash lived through a whole release
 *    because the only person who saw it was the one it happened to.
 *  - **Analytics** takes four events, which is enough to know whether rounds are being finished
 *    and not enough to reconstruct anybody's evening.
 *
 * Both are off in debug builds. See the manifest.
 */
object Telemetry {

    /**
     * Analytics needs a context and this is an object, so it is handed one at startup rather than
     * reaching for a static. Null until then, which reads as "say nothing" — the same answer a
     * disabled build gives, so there is one quiet path instead of two.
     */
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        if (analytics == null) {
            analytics = runCatching { FirebaseAnalytics.getInstance(context.applicationContext) }
                .getOrNull()
        }
    }

    /** A round was started from a group screen. [size] is how many apps it covers. */
    fun roundStarted(size: Int) = log("round_started", "apps" to size.toLong())

    /**
     * A round ended. [captured] and [missed] add up to the round, and the pair is the whole point:
     * a run of rounds ending with misses is the shape the five-lost-captures bug made, and it was
     * invisible from here at the time.
     */
    fun roundFinished(captured: Int, missed: Int) =
        log("round_finished", "captured" to captured.toLong(), "missed" to missed.toLong())

    /** One proof reached Drive and Firestore. */
    fun proofPosted() = log("proof_posted")

    /** An app was put forward for placement. */
    fun appSubmitted() = log("app_submitted")

    /**
     * Something went wrong that the tester was told about in their own words and nobody else would
     * ever hear.
     *
     * [where] is a fixed string from the calling site, never a message and never a value, so
     * nothing about the person or their apps can be carried in on it by accident.
     */
    fun broke(where: String, cause: Throwable) {
        runCatching {
            FirebaseCrashlytics.getInstance().log(where)
            FirebaseCrashlytics.getInstance().recordException(cause)
        }
    }

    private fun log(event: String, vararg params: Pair<String, Long>) {
        val sink = analytics ?: return
        runCatching {
            sink.logEvent(event, Bundle().apply {
                params.forEach { (key, value) -> putLong(key, value) }
            })
        }
    }
}
