package com.eazyverse.testtrack.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.BuildConfig

/**
 * Debug-only switches for testing the Crew Open join checklist from a phone that is already in the
 * testers group and already has Drive granted: the first is remembered in [Session], the second
 * is a grant on the Google account that resolves silently.
 *
 * Every getter is gated on [BuildConfig.DEBUG], a constant false in a release build, so a release
 * ignores the stored values and the switches compile down to nothing. Both default off. Kept in a
 * file of their own so they survive [Session.signOut], which clears the main prefs.
 */
object DebugOverrides {
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        prefs = context.applicationContext.getSharedPreferences("testtrack_debug", Context.MODE_PRIVATE)
        forceNoGroup = prefs.getBoolean(KEY_NO_GROUP, false)
        forceNoDrive = prefs.getBoolean(KEY_NO_DRIVE, false)
    }

    private var forceNoGroup by mutableStateOf(false)
    private var forceNoDrive by mutableStateOf(false)

    /** The app treats the account as outside the testers group, whatever was remembered or answered. */
    val noGroup: Boolean get() = BuildConfig.DEBUG && forceNoGroup

    /** Drive reads as not connected, whatever Google says. */
    val noDrive: Boolean get() = BuildConfig.DEBUG && forceNoDrive

    fun updateNoGroup(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_NO_GROUP, value).apply()
        forceNoGroup = value
    }

    fun updateNoDrive(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_NO_DRIVE, value).apply()
        forceNoDrive = value
    }

    /** A yes from the server reads as a no while [noGroup] is on, so the whole flow behaves as outside. */
    fun group(answer: GateResult): GateResult = if (noGroup && answer is GateResult.Member) GateResult.NotMember(answer.email) else answer

    private const val KEY_NO_GROUP = "force_no_group"
    private const val KEY_NO_DRIVE = "force_no_drive"
}
