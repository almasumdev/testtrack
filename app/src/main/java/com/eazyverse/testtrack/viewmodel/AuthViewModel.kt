package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AdminEvents
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.NotGmailException
import com.eazyverse.testtrack.data.Repo
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.claimDevice
import com.eazyverse.testtrack.data.friendly
import com.eazyverse.testtrack.data.upsertUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Signing in, without a screen around it.
 *
 * The screen this came from was the app's front door: a logo, a three point pitch and a button,
 * shown before anybody had seen anything. The app opens on its own content now and asks for a name
 * only where it cannot answer without one, so what is left of that screen is the part that does
 * the work.
 */
class AuthViewModel : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    fun signIn(
        activity: Activity,
        onDone: () -> Unit,
    ) {
        busy = true
        message = null
        viewModelScope.launch {
            try {
                val account = AuthRepo.signIn(activity)
                Session.updateEmail(account.email)

                // Everything that happened before this moment is history, not news. Without this
                // a returning tester is greeted by every placement decision ever made about them.
                AdminEvents.markCaughtUp(activity)

                // Bounded rather than unawaited. A Firestore write does not report done until
                // the server acknowledges it, so waiting without a limit is how this screen
                // hangs; but launching it and walking away is worse, because navigating clears
                // this ViewModel and cancels the scope, and the row quietly never gets written.
                // It is the tester's name in twelve other people's grids, so it is worth six
                // seconds and worth being sure about.
                AuthRepo.uid?.let { uid ->
                    withTimeoutOrNull(6_000) {
                        runCatching {
                            Repo.upsertUser(uid, account.email, account.name)
                        }
                    }

                    // Alongside it, and just as quietly. Nothing on this screen depends on the
                    // answer: it is a fact an admin reads later, not a gate anybody passes.
                    Device.fingerprint(activity)?.let { phone ->
                        withTimeoutOrNull(6_000) {
                            runCatching { Repo.claimDevice(uid, phone) }
                        }
                    }
                }

                message = AuthRepo.firebaseError

                // Last, and with nothing after it. This navigates, which clears the ViewModel and
                // cancels the scope underneath us; any work left below would be killed mid-flight
                // and reported as a failure by the catch.
                onDone()
            } catch (e: NotGmailException) {
                message = "${e.email} isn't a Gmail account. Pick the Gmail you test with."
            } catch (e: CancellationException) {
                // Navigating away cancels this scope, and CancellationException is an Exception,
                // so the catch below was turning a completely normal sign-in into a failure
                // message on the way out. Cancellation is not a failure and must not be
                // swallowed.
                throw e
            } catch (e: Exception) {
                // Short, because friendly() appends the real cause when it has no better
                // sentence of its own, and two guesses in a row read as noise.
                message = e.friendly("Sign-in didn't work.")
            }
            busy = false
        }
    }
}
