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
        packageName = prefs.getString(KEY_PACKAGE, null)
        trackSubmitted = prefs.getBoolean(KEY_SUBMITTED, false)
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
     * The owner's own app under test.
     *
     * Not machine-verified. Reading Play track state needs developer-level authorization from
     * every owner — a service account invited to their Play Console, a linked Cloud project, or a
     * sensitive scope requiring Google verification. All three push setup cost onto every owner,
     * so submissions go to an admin for approval instead.
     */
    var packageName by mutableStateOf<String?>(null)
        private set

    /** Sent for review. Approval arrives from the admin later; it does not block setup. */
    var trackSubmitted by mutableStateOf(false)
        private set

    val signedIn: Boolean get() = email != null

    /** Every gate cleared. Approval is deliberately not required — it is asynchronous. */
    val setupComplete: Boolean
        get() = signedIn && isGroupMember && driveConnected && trackSubmitted

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

    fun updateDriveConnected(value: Boolean) {
        prefs.edit().putBoolean(KEY_DRIVE, value).apply()
        driveConnected = value
    }

    fun submitTrack(pkg: String) {
        prefs.edit().putString(KEY_PACKAGE, pkg).putBoolean(KEY_SUBMITTED, true).apply()
        packageName = pkg
        trackSubmitted = true
    }

    /** Clears everything except onboarding — nobody wants to watch the intro twice. */
    fun signOut() {
        prefs.edit().clear().putBoolean(KEY_ONBOARDED, true).apply()
        email = null
        isGroupMember = false
        driveConnected = false
        packageName = null
        trackSubmitted = false
        onboardingDone = true
    }

    private const val KEY_ONBOARDED = "onboarding_done"
    private const val KEY_EMAIL = "email"
    private const val KEY_MEMBER = "is_member"
    private const val KEY_DRIVE = "drive_connected"
    private const val KEY_PACKAGE = "package_name"
    private const val KEY_SUBMITTED = "track_submitted"
}
