package com.eazyverse.testtrack.net

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The token pair, kept across launches.
 *
 * Plain preferences rather than the encrypted variant. What is stored is a bearer token that
 * expires in fifteen minutes and a refresh token the server can revoke, in a file only this app
 * can read; wrapping that in a keystore-backed cipher buys very little and costs a dependency that
 * has been deprecated once already. What actually protects a session is that it can be revoked,
 * which is why sessions are a table on the server.
 *
 * The preferences handle is held after [load] so that a token refresh, which happens on a
 * background thread in the middle of somebody else's request, does not need a Context passed down
 * to it through every call.
 */
object ApiSession {
    private const val FILE = "testtrack.api"
    private const val ACCESS = "access"
    private const val REFRESH = "refresh"

    @Volatile private var prefs: SharedPreferences? = null

    @Volatile private var cachedAccess: String? = null

    @Volatile private var cachedRefresh: String? = null

    private val ready = MutableStateFlow(false)
    val signedInState = ready.asStateFlow()

    @Volatile var generation: Long = 0
        private set

    @Synchronized
    fun load(context: Context) {
        if (prefs != null) return
        val store = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        prefs = store
        cachedAccess = store.getString(ACCESS, null)
        cachedRefresh = store.getString(REFRESH, null)
        ready.value = cachedAccess != null
    }

    /** Keep a freshly minted pair. Safe to call from anywhere once [load] has run. */
    @Synchronized
    fun remember(
        tokens: Tokens,
        expectedGeneration: Long = generation,
    ): Boolean {
        if (expectedGeneration != generation) return false
        cachedAccess = tokens.accessToken
        cachedRefresh = tokens.refreshToken
        prefs?.edit {
            putString(ACCESS, tokens.accessToken)
            putString(REFRESH, tokens.refreshToken)
        }
        ready.value = true
        return true
    }

    @Synchronized
    fun clear() {
        generation++
        cachedAccess = null
        cachedRefresh = null
        prefs?.edit { clear() }
        ready.value = false
    }

    val access: String? get() = cachedAccess
    val refresh: String? get() = cachedRefresh
    val signedIn: Boolean get() = cachedAccess != null
}
