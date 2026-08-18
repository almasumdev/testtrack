package com.eazyverse.testtrack.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Everything that survives a restart. SharedPreferences rather than DataStore — the payload is a
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
        email = prefs.getString(KEY_EMAIL, null)
        isGroupMember = prefs.getBoolean(KEY_MEMBER, false)
        driveConnected = prefs.getBoolean(KEY_DRIVE, false)
        remindersAsked = prefs.getBoolean(KEY_REMINDERS_ASKED, false)
        nudgedBuild = prefs.getInt(KEY_NUDGED_BUILD, 0)
        refreshUsageAccess(context)
        refreshNotifications(context)
    }

    var onboardingDone by mutableStateOf(false)
        private set

    var email by mutableStateOf<String?>(null)
        private set

    var isGroupMember by mutableStateOf(false)
        private set

    var driveConnected by mutableStateOf(false)
        private set

    /**
     * Whether Settings → Special access → Usage access is on for TestTrack.
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

    /** Live, for the same reason as [usageAccessGranted] — the switch is the system's, not ours. */
    var notificationsGranted by mutableStateOf(false)
        private set

    /**
     * The tester has been asked about reminders and answered, either way.
     *
     * Persisted, unlike the grant itself. Reminders are the one thing on the checklist that is
     * genuinely optional — a tester who declines can still do every part of the job — so setup has
     * to be able to finish without them. What it must not do is finish without *asking*: a missed
     * day costs thirteen other people their run, and silence is a poor reason for it.
     */
    var remindersAsked by mutableStateOf(false)
        private set

    fun refreshNotifications(context: Context) {
        notificationsGranted = PushRepo.granted(context)
    }

    fun updateRemindersAsked() {
        prefs.edit().putBoolean(KEY_REMINDERS_ASKED, true).apply()
        remindersAsked = true
    }

    val signedIn: Boolean get() = email != null

    /** Answered, one way or the other. Granting counts as answering. */
    val remindersSettled: Boolean get() = notificationsGranted || remindersAsked

    /**
     * Everything setup can settle.
     *
     * Submitting an app is deliberately not part of it — placement into a group is an admin's
     * call and can take days, so apps live on their own screen rather than as a rung of a
     * one-time checklist. Usage access *is* part of it: without it a daily report carries no
     * proof anyone stayed, which is the whole point of the report.
     */
    val setupComplete: Boolean
        get() = signedIn && isGroupMember && driveConnected && usageAccessGranted && remindersSettled

    /**
     * The newest build they have already waved an update prompt away for.
     *
     * Per build rather than a flag, so dismissing one offer does not silence the next one. See
     * [UpdateGate].
     */
    var nudgedBuild by mutableStateOf(0)
        private set

    fun updateNudgedBuild(value: Int) {
        prefs.edit().putInt(KEY_NUDGED_BUILD, value).apply()
        nudgedBuild = value
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
        isGroupMember = value
    }

    /**
     * Cached, not authoritative.
     *
     * The Drive grant belongs to the Google account and outlives this install, so the real answer
     * always comes from [AuthRepo.hasDriveAccess]. This only exists so the setup screen has
     * something to draw before that answer arrives.
     */
    fun updateDriveConnected(value: Boolean) {
        prefs.edit().putBoolean(KEY_DRIVE, value).apply()
        driveConnected = value
    }

    /**
     * Clears everything except onboarding — nobody wants to watch the intro twice — and except the
     * update prompt, which is about this copy of the app rather than about whoever is signed into
     * it. Signing out and back in is not a reason to be asked again.
     */
    fun signOut() {
        prefs.edit().clear()
            .putBoolean(KEY_ONBOARDED, true)
            .putInt(KEY_NUDGED_BUILD, nudgedBuild)
            .apply()
        email = null
        isGroupMember = false
        driveConnected = false
        remindersAsked = false
        onboardingDone = true
    }

    private const val KEY_ONBOARDED = "onboarding_done"
    private const val KEY_EMAIL = "email"
    private const val KEY_MEMBER = "is_member"
    private const val KEY_DRIVE = "drive_connected"
    private const val KEY_REMINDERS_ASKED = "reminders_asked"
    private const val KEY_NUDGED_BUILD = "nudged_build"
}
