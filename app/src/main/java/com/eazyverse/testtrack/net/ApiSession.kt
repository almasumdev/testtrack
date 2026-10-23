package com.eazyverse.testtrack.net

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    private const val HEADER = "header"
    private const val REVOCATIONS = "pending_revocations"
    private var app: Context? = null
    private var header: HeaderSnapshot? = null

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
        app = context.applicationContext
        ApiCache.load(context)
        cachedAccess = store.getString(ACCESS, null)
        cachedRefresh = store.getString(REFRESH, null)
        header = store.getString(HEADER, null)?.let { runCatching { Json.decodeFromString<HeaderSnapshot>(it) }.getOrNull() }
        ready.value = cachedAccess != null
        if (pendingRevocations().isNotEmpty()) SessionLogout.enqueue(context)
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
    fun cachedHeader(account: String?): HeaderSnapshot? = header?.takeIf { signedIn && it.account == account }

    @Synchronized
    fun rememberHeader(
        snapshot: HeaderSnapshot,
        expectedGeneration: Long,
    ): Boolean {
        if (expectedGeneration != generation || !signedIn) return false
        header = snapshot
        prefs?.edit { putString(HEADER, Json.encodeToString(snapshot)) }
        return true
    }

    @Synchronized
    fun clear() {
        generation++
        cachedAccess = null
        cachedRefresh = null
        header = null
        prefs?.edit {
            remove(ACCESS)
            remove(REFRESH)
            remove(HEADER)
        }
        ApiCache.clear()
        ready.value = false
    }

    /**
     * The server refused the refresh token: the pair is dead, though the account is not. Only the
     * pair goes, so the next [ApiAuth.ensure] trades the Google sign-in for a new one; the
     * account's cache and header stay, being the same person's.
     */
    @Synchronized
    fun dropTokens(expectedGeneration: Long) {
        if (expectedGeneration != generation) return
        cachedAccess = null
        cachedRefresh = null
        prefs?.edit {
            remove(ACCESS)
            remove(REFRESH)
        }
        ready.value = false
    }

    val access: String? get() = cachedAccess
    val refresh: String? get() = cachedRefresh
    val signedIn: Boolean get() = cachedAccess != null

    @Synchronized
    fun queueRevocation() {
        val token = cachedRefresh ?: return
        val pending = pendingRevocations() + token
        prefs?.edit(commit = true) { putStringSet(REVOCATIONS, pending) }
        app?.let(SessionLogout::enqueue)
    }

    @Synchronized
    fun pendingRevocations(): Set<String> = prefs?.getStringSet(REVOCATIONS, emptySet())?.toSet().orEmpty()

    @Synchronized
    fun revoked(token: String) {
        prefs?.edit(commit = true) { putStringSet(REVOCATIONS, pendingRevocations() - token) }
    }
}

/** Display-only cache. Posting and spending still use server-authoritative balances. */
@Serializable
data class HeaderSnapshot(
    val account: String,
    val level: Int,
    val intoLevel: Int,
    val forNext: Int,
    val tix: Int,
)
