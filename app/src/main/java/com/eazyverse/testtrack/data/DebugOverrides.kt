package com.eazyverse.testtrack.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.BuildConfig

/**
 * The debug-only switch for testing the Crew Open join checklist from a phone that is already in
 * the testers group, which is remembered in [Session].
 *
 * The getter is gated on [BuildConfig.DEBUG], a constant false in a release build, so a release
 * ignores the stored value and the switch compiles down to nothing. It defaults off. Kept in a
 * file of its own so it survives [Session.signOut], which clears the main prefs.
 */
object DebugOverrides {
    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (!BuildConfig.DEBUG) return
        prefs = context.applicationContext.getSharedPreferences("testtrack_debug", Context.MODE_PRIVATE)
        forceNoGroup = prefs.getBoolean(KEY_NO_GROUP, false)
    }

    private var forceNoGroup by mutableStateOf(false)

    /** The app treats the account as outside the testers group, whatever was remembered or answered. */
    val noGroup: Boolean get() = BuildConfig.DEBUG && forceNoGroup

    fun updateNoGroup(value: Boolean) {
        if (!BuildConfig.DEBUG) return
        prefs.edit().putBoolean(KEY_NO_GROUP, value).apply()
        forceNoGroup = value
    }

    /** A yes from the server reads as a no while [noGroup] is on, so the whole flow behaves as outside. */
    fun group(answer: GateResult): GateResult = if (noGroup && answer is GateResult.Member) GateResult.NotMember(answer.email) else answer

    private const val KEY_NO_GROUP = "force_no_group"
}
