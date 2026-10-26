package com.eazyverse.testtrack.net

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The server saying this account was deleted by the TestTrack team.
 *
 * Unlike a lapsed session there is nothing to refresh and nothing to retry: every call from now on
 * is refused the same way. Left alone the app kept its dead tokens, looked signed in, and retried
 * each screen about once a minute behind a "check your connection". So the first refusal ends it
 * in one place: the tokens and the socket go here, the app's own teardown (Google, local state) is
 * the [onGone] the activity sets, and the server's sentence is kept for one dialog.
 */
object AccountGone {
    private val pending = MutableStateFlow<String?>(null)

    /** The server's own words, until somebody has read them. */
    val notice: StateFlow<String?> = pending.asStateFlow()

    /** The rest of signing out, which lives outside the network layer. Set once, by the activity. */
    @Volatile var onGone: (() -> Unit)? = null

    private val main = Handler(Looper.getMainLooper())

    /** The account was deleted. Safe to call from any thread, as often as refusals arrive. */
    fun deleted(message: String) {
        if (pending.value != null) return
        pending.value = message
        ApiSession.clear()
        Live.reset()
        main.post { onGone?.invoke() }
    }

    /** The dialog was read. */
    fun seen() {
        pending.value = null
    }

    const val CODE = "account_deleted"
}
