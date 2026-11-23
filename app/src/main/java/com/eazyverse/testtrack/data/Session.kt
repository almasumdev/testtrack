package com.eazyverse.testtrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything that survives a restart. SharedPreferences rather than DataStore: the payload is a
 * handful of flags, and the synchronous read lets the start destination resolve before first
 * compose, so no screen flashes before the right one appears.
 *
 * Each property mirrors into Compose state so screens recompose when it changes. Mutators are
 * named `updateX` rather than `setX` because the latter collides with the generated property
 * setter on the JVM.
 */
object Session {
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("testtrack", Context.MODE_PRIVATE)
        onboardingDone = prefs.getBoolean(KEY_ONBOARDED, false)
        pictureTipHidden = prefs.getBoolean(KEY_PICTURE_TIP_HIDDEN, false)
        feedbackTipHidden = prefs.getBoolean(KEY_FEEDBACK_TIP_HIDDEN, false)
        email = prefs.getString(KEY_EMAIL, null)
        DebugOverrides.init(context)
        memberCached = prefs.getBoolean(KEY_MEMBER, false)
        cachedUpdateAvailableBuild = prefs.getInt(KEY_UPDATE_CACHE_AVAILABLE, 0)
        cachedUpdateStalenessDays = prefs.getInt(KEY_UPDATE_CACHE_STALENESS, 0)
        cachedUpdateLatestBuild = prefs.getInt(KEY_UPDATE_CACHE_LATEST, 0)
        cachedUpdateLatestVersionName = prefs.getString(KEY_UPDATE_CACHE_LATEST_NAME, null) ?: ""
        cachedUpdateMinSupportedBuild = prefs.getInt(KEY_UPDATE_CACHE_MIN, 0)
        cachedUpdateNudgeAfterDays = prefs.getInt(KEY_UPDATE_CACHE_NUDGE_AFTER, 0)
        refreshUsageAccess(context)
        refreshNotifications(context)
    }

    var onboardingDone by mutableStateOf(false)
        private set

    /** "Don't show again" ticked on the picture explainer before a round: Android's own ask still comes. */
    var pictureTipHidden by mutableStateOf(false)
        private set

    fun hidePictureTip() {
        prefs.edit().putBoolean(KEY_PICTURE_TIP_HIDDEN, true).apply()
        pictureTipHidden = true
    }

    /** "Don't show again" ticked on the private feedback ask: Write goes straight to Android's own ask. */
    var feedbackTipHidden by mutableStateOf(false)
        private set

    fun hideFeedbackTip() {
        prefs.edit().putBoolean(KEY_FEEDBACK_TIP_HIDDEN, true).apply()
        feedbackTipHidden = true
    }

    var email by mutableStateOf<String?>(null)
        private set

    private var memberCached by mutableStateOf(false)

    /** The remembered yes, except while the debug switch pretends to be outside the group. */
    val isGroupMember: Boolean get() = memberCached && !DebugOverrides.noGroup

    /**
     * Whether Settings > Special access > Usage access is on for TestTrack.
     *
     * Not persisted. It can be revoked from Settings without telling us, so a stored `true` would
     * be a lie the moment someone toggled it off; [refreshUsageAccess] re-reads it on every
     * resume instead.
     */
    var usageAccessGranted by mutableStateOf(false)
        private set

    fun refreshUsageAccess(context: Context) {
        usageAccessGranted = UsageRepo.hasAccess(context)
    }

    /** Live, for the same reason as [usageAccessGranted]: the switch is the system's, not ours. */
    var notificationsGranted by mutableStateOf(false)
        private set

    fun refreshNotifications(context: Context) {
        notificationsGranted = PushRepo.granted(context)
    }

    val signedIn: Boolean get() = email != null

    /*
     * The inputs of the last update decision Play and Firestore agreed on, so it can be replayed
     * when either cannot be asked.
     *
     * Inputs, never the verdict: once the installed build reaches [cachedUpdateAvailableBuild] the
     * same inputs resolve to nothing, so an update installed offline releases its own prompt. Plain
     * properties rather than Compose state, nothing draws them, only [AppUpdateService] reads them.
     * 0 in [cachedUpdateAvailableBuild] means nothing is cached.
     */
    var cachedUpdateAvailableBuild = 0
        private set
    var cachedUpdateStalenessDays = 0
        private set
    var cachedUpdateLatestBuild = 0
        private set
    var cachedUpdateLatestVersionName = ""
        private set
    var cachedUpdateMinSupportedBuild = 0
        private set
    var cachedUpdateNudgeAfterDays = 0
        private set

    fun updateCachedUpdatePolicy(
        availableBuild: Int,
        stalenessDays: Int,
        latestBuild: Int,
        latestVersionName: String,
        minSupportedBuild: Int,
        nudgeAfterDays: Int,
    ) {
        prefs
            .edit()
            .putInt(KEY_UPDATE_CACHE_AVAILABLE, availableBuild)
            .putInt(KEY_UPDATE_CACHE_STALENESS, stalenessDays)
            .putInt(KEY_UPDATE_CACHE_LATEST, latestBuild)
            .putString(KEY_UPDATE_CACHE_LATEST_NAME, latestVersionName)
            .putInt(KEY_UPDATE_CACHE_MIN, minSupportedBuild)
            .putInt(KEY_UPDATE_CACHE_NUDGE_AFTER, nudgeAfterDays)
            .apply()
        cachedUpdateAvailableBuild = availableBuild
        cachedUpdateStalenessDays = stalenessDays
        cachedUpdateLatestBuild = latestBuild
        cachedUpdateLatestVersionName = latestVersionName
        cachedUpdateMinSupportedBuild = minSupportedBuild
        cachedUpdateNudgeAfterDays = nudgeAfterDays
    }

    fun clearCachedUpdatePolicy() {
        prefs
            .edit()
            .remove(KEY_UPDATE_CACHE_AVAILABLE)
            .remove(KEY_UPDATE_CACHE_STALENESS)
            .remove(KEY_UPDATE_CACHE_LATEST)
            .remove(KEY_UPDATE_CACHE_LATEST_NAME)
            .remove(KEY_UPDATE_CACHE_MIN)
            .remove(KEY_UPDATE_CACHE_NUDGE_AFTER)
            .apply()
        cachedUpdateAvailableBuild = 0
        cachedUpdateStalenessDays = 0
        cachedUpdateLatestBuild = 0
        cachedUpdateLatestVersionName = ""
        cachedUpdateMinSupportedBuild = 0
        cachedUpdateNudgeAfterDays = 0
    }

    fun updateOnboardingDone() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        onboardingDone = true
    }

    fun updateEmail(value: String?) {
        prefs.edit().putString(KEY_EMAIL, value).apply()
        email = value
    }

    fun updateMember(value: Boolean) {
        prefs.edit().putBoolean(KEY_MEMBER, value).apply()
        memberCached = value
    }

    /**
     * Clears everything except onboarding, nobody wants to watch the intro twice, the hidden
     * picture explainer and feedback ask, which are this phone's own choice, and except the
     * cached update policy, which is about this copy of the app rather than about whoever is signed
     * into it.
     */
    fun signOut() {
        prefs
            .edit()
            .clear()
            .putBoolean(KEY_ONBOARDED, true)
            .putBoolean(KEY_PICTURE_TIP_HIDDEN, pictureTipHidden)
            .putBoolean(KEY_FEEDBACK_TIP_HIDDEN, feedbackTipHidden)
            .putInt(KEY_UPDATE_CACHE_AVAILABLE, cachedUpdateAvailableBuild)
            .putInt(KEY_UPDATE_CACHE_STALENESS, cachedUpdateStalenessDays)
            .putInt(KEY_UPDATE_CACHE_LATEST, cachedUpdateLatestBuild)
            .putString(KEY_UPDATE_CACHE_LATEST_NAME, cachedUpdateLatestVersionName)
            .putInt(KEY_UPDATE_CACHE_MIN, cachedUpdateMinSupportedBuild)
            .putInt(KEY_UPDATE_CACHE_NUDGE_AFTER, cachedUpdateNudgeAfterDays)
            .apply()
        email = null
        memberCached = false
        onboardingDone = true
    }

    private const val KEY_ONBOARDED = "onboarding_done"
    private const val KEY_PICTURE_TIP_HIDDEN = "picture_tip_hidden"
    private const val KEY_FEEDBACK_TIP_HIDDEN = "feedback_tip_hidden"
    private const val KEY_EMAIL = "email"
    private const val KEY_MEMBER = "is_member"
    private const val KEY_UPDATE_CACHE_AVAILABLE = "update_cache_available_build"
    private const val KEY_UPDATE_CACHE_STALENESS = "update_cache_staleness_days"
    private const val KEY_UPDATE_CACHE_LATEST = "update_cache_latest_build"
    private const val KEY_UPDATE_CACHE_LATEST_NAME = "update_cache_latest_version_name"
    private const val KEY_UPDATE_CACHE_MIN = "update_cache_min_supported_build"
    private const val KEY_UPDATE_CACHE_NUDGE_AFTER = "update_cache_nudge_after_days"
}
