package com.eazyverse.testtrack.viewmodel

import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The Play check's Google sign-in, for the team.
 *
 * The server looks crew apps up on Play through a browser signed in as an account on the testers
 * group. Google sometimes stops that browser with a "verify it's you" screen that only a person
 * can answer, and this is how the person does it: a picture of the server's page, refreshed every
 * second or so, with taps, typing and a few keys sent back. Admins only; the server refuses
 * everybody else, and the row that opens this is not drawn for them.
 */
class PlayCheckViewModel : ViewModel() {
    /** Whether this account is on the team. Only then is the row shown at all. */
    var admin by mutableStateOf(false)
        private set

    /** Signed in, signed out, or not known yet. */
    var signedIn by mutableStateOf<Boolean?>(null)
        private set

    /** The server's reason it is signed out, in its own code. */
    var reason by mutableStateOf("")
        private set

    /** The remote browser is open for a sign-in. */
    var open by mutableStateOf(false)
        private set

    var screen by mutableStateOf<ImageBitmap?>(null)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set

    private var watching: Job? = null

    /** Whether to show the row, and what it says. Quiet on failure: it is a convenience. */
    fun load() {
        if (!ApiSession.signedIn) return
        viewModelScope.launch {
            runCatching { admin = Api.me().isAdmin }
            if (admin) status()
        }
    }

    private suspend fun status() {
        runCatching { Api.playSession() }.onSuccess {
            signedIn = it.signedIn
            reason = it.reason
        }
    }

    /**
     * Let the server sign itself in again, with the password and authenticator code it holds.
     * For after Google's extra question has been switched off for a few minutes in the
     * Workspace admin console.
     */
    fun retry() =
        act {
            val after = Api.playRetry()
            signedIn = after.signedIn
            reason = after.reason
            message =
                if (after.signedIn) {
                    "Signed in. Crew apps are checked on Play again."
                } else {
                    "Still signed out (${after.reason}). Turn off the login challenge for the account, then try again."
                }
        }

    /** Put the server's browser on Google's sign-in, and start showing it. */
    fun start() =
        act {
            Api.playOpen()
            open = true
            watch()
        }

    fun tap(
        x: Int,
        y: Int,
    ) = act(quiet = true) { Api.playClick(x, y) }

    fun type(text: String) = act(quiet = true) { Api.playType(text) }

    fun key(name: String) = act(quiet = true) { Api.playKey(name) }

    /** Check the sign-in took, and hand the browser back to the crew check. */
    fun finish() =
        act {
            val after = Api.playFinish()
            stop()
            signedIn = after.signedIn
            reason = after.reason
            message = if (after.signedIn) "Signed in. Crew apps are checked on Play again." else "Not signed in yet."
        }

    fun cancel() =
        act {
            Api.playCancel()
            stop()
            status()
        }

    fun clearMessage() {
        message = null
    }

    private fun watch() {
        watching?.cancel()
        watching =
            viewModelScope.launch {
                while (isActive && open) {
                    runCatching { Api.playScreen() }.onSuccess { bytes ->
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { screen = it.asImageBitmap() }
                    }
                    delay(1_200)
                }
            }
    }

    private fun stop() {
        open = false
        watching?.cancel()
        screen = null
    }

    private fun act(
        quiet: Boolean = false,
        block: suspend () -> Unit,
    ) {
        if (!quiet) busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "The server did not answer."
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        // A browser held open with nobody looking blocks every crew check until it times out.
        if (open) CoroutineScope(Dispatchers.IO).launch { runCatching { Api.playCancel() } }
        super.onCleared()
    }
}
