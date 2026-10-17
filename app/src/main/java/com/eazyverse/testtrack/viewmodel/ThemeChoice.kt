package com.eazyverse.testtrack.viewmodel

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/** Light, dark, or whichever the phone is in: the three choices on the You page. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * The theme somebody chose, kept on the phone, as Value Guard and ListKin keep theirs.
 *
 * From Android 12 the choice is also handed to the system as this app's own night mode, so the
 * splash and the window under the first frame are already in it, and the phone's dark setting
 * reaching Compose says the same thing. Before 12 only Compose follows it.
 */
object ThemeChoice {
    private const val PREFS = "testtrack.theme"
    private const val KEY = "theme_mode"

    var mode by mutableStateOf(ThemeMode.SYSTEM)
        private set

    /** Once per process start, before the first frame. */
    fun init(context: Context) {
        val saved = prefs(context).getString(KEY, null)
        mode = ThemeMode.entries.firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun choose(
        context: Context,
        next: ThemeMode,
    ) {
        if (next == mode) return
        mode = next
        prefs(context).edit { putString(KEY, next.name) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
                when (next) {
                    // AUTO, for an app's own night mode, is "as the phone is".
                    ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
                    ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
                },
            )
        }
    }

    /** Whether to draw dark, given what the phone itself is in. */
    fun isDark(systemDark: Boolean) =
        when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
