package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import com.eazyverse.testtrack.data.Session
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/**
 * When TestTrack asks for a rating: only after a value moment, and
 * only when every limit allows it. Play decides whether a card is shown and whether it counts; this
 * decides only when it is worth spending the one chance we get.
 *
 * TestTrack's value moment is a test cycle completed: a crew of yours reaching the end of its
 * fourteen nights. The crew list is the only place that is known, so [noteFinished] is fed from it
 * and remembers which finished crews it has already counted; the first list it ever sees is taken
 * as the starting point, so an update does not count every crew somebody finished before it.
 *
 * The limits are Remote Config values with the doc's defaults compiled in, so a cold start with no
 * network behaves correctly and a limit can be changed without a release.
 */
object ReviewMoment {
    private const val PREFS = "testtrack.review"
    private const val SESSIONS = "review_session_count"
    private const val VALUE_EVENTS = "review_value_events"
    private const val LAST_ASKED = "review_last_asked_at"
    private const val ASKS = "review_asks"
    private const val ASKED = "review_asked"
    private const val OPTED_OUT = "review_opted_out"
    private const val SEEN = "review_seen_finished"

    private const val DAY = 24L * 60 * 60 * 1000

    private val defaults: Map<String, Any> =
        mapOf(
            "review_enabled" to true,
            "review_min_sessions" to 5L,
            "review_min_days_installed" to 3L,
            "review_min_value_events" to 2L,
            "review_cooldown_days" to 120L,
            "review_max_per_year" to 2L,
        )

    private val due = MutableStateFlow(false)

    /** True when a value moment has earned an ask that every limit allows; the shell shows it. */
    val askDue = due.asStateFlow()

    /** Something failed this session, which is no moment to ask for anything. */
    @Volatile private var troubled = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val config: FirebaseRemoteConfig? by lazy {
        runCatching {
            FirebaseRemoteConfig.getInstance().apply {
                setConfigSettingsAsync(remoteConfigSettings { minimumFetchIntervalInSeconds = 12 * 60 * 60 })
                setDefaultsAsync(defaults)
            }
        }.getOrNull()
    }

    private fun long(key: String) = config?.getLong(key) ?: (defaults[key] as Long)

    /** Once per process start: a session, and the limits fetched for next time. */
    fun onSessionStart(context: Context) {
        val store = prefs(context)
        store.edit { putInt(SESSIONS, store.getInt(SESSIONS, 0) + 1) }
        runCatching { config?.fetchAndActivate() }
    }

    /** A page could not load or an action failed: no ask for the rest of this session. */
    fun noteTrouble() {
        troubled = true
    }

    /** The finished crews in the latest crew list; any not seen before is a value moment. */
    fun noteFinished(
        context: Context,
        finished: Set<String>,
    ) {
        val store = prefs(context)
        val seen = store.getStringSet(SEEN, null)
        if (seen == null) {
            // The first list is the starting point, not a burst of moments.
            store.edit { putStringSet(SEEN, finished) }
            return
        }
        val fresh = finished - seen
        if (fresh.isEmpty()) return
        store.edit {
            putStringSet(SEEN, seen + fresh)
            putInt(VALUE_EVENTS, store.getInt(VALUE_EVENTS, 0) + fresh.size)
        }
        if (allowed(context)) due.value = true
    }

    /** Every condition in the doc's "never ask when" list, and every threshold. */
    private fun allowed(context: Context): Boolean {
        if (config?.getBoolean("review_enabled") == false) return false
        if (troubled || !Session.onboardingDone) return false
        val store = prefs(context)
        if (store.getBoolean(ASKED, false) || store.getBoolean(OPTED_OUT, false)) return false
        if (store.getInt(SESSIONS, 0) < long("review_min_sessions")) return false
        if (store.getInt(VALUE_EVENTS, 0) < long("review_min_value_events")) return false
        val info = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull() ?: return false
        val now = System.currentTimeMillis()
        if (now - info.firstInstallTime < long("review_min_days_installed") * DAY) return false
        if (now - info.lastUpdateTime < DAY) return false
        val last = store.getLong(LAST_ASKED, 0L)
        if (last > 0 && now - last < long("review_cooldown_days") * DAY) return false
        val asks = store.getStringSet(ASKS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        return asks.count { now - it < 365 * DAY } < long("review_max_per_year")
    }

    /**
     * Play's own card, with nothing asked before it. True if the flow ran cleanly, which is the only
     * signal there is and counts as asked; false means no card could be had, and the caller shows the
     * app's own dialog instead.
     */
    suspend fun askWithCard(activity: Activity): Boolean {
        due.value = false
        if (!allowed(activity)) return true
        return runCatching {
            val manager = ReviewManagerFactory.create(activity)
            manager.launchReviewFlow(activity, manager.requestReviewFlow().await()).await()
            markAsked(activity)
            true
        }.getOrElse {
            // Leaving the screen is not "no card": let the cancellation through.
            if (it is CancellationException) throw it
            false
        }
    }

    /**
     * Sent to the listing: the fifth star or the Rate row. Somebody who went there to rate is never
     * asked automatically again.
     */
    fun markRated(context: Context) {
        prefs(context).edit { putBoolean(ASKED, true) }
        recordAsk(context)
    }

    /**
     * Asked, but not sent to rate: Play's card, `Not now`, a low-star mail. Counts toward the cooldown
     * and the yearly limit, so somebody who has just written in does not meet a card a week later.
     */
    fun markAsked(context: Context) = recordAsk(context)

    /** `Never`, in the dialog that came unbidden: nothing automatic asks again. */
    fun optOut(context: Context) {
        prefs(context).edit {
            putBoolean(ASKED, true)
            putBoolean(OPTED_OUT, true)
        }
        recordAsk(context)
    }

    private fun recordAsk(context: Context) {
        val now = System.currentTimeMillis()
        val store = prefs(context)
        val asks = store.getStringSet(ASKS, emptySet()).orEmpty().filter { (it.toLongOrNull() ?: 0) > now - 365 * DAY }
        store.edit {
            putLong(LAST_ASKED, now)
            putStringSet(ASKS, (asks + now.toString()).toSet())
        }
    }
}
