package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.friendly
import com.eazyverse.testtrack.domain.completeSignIn
import com.eazyverse.testtrack.net.ApiAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Signing in, without a screen around it.
 *
 * The screen this came from was the app's front door: a logo, a three point pitch and a button,
 * shown before anybody had seen anything. The app opens on its own content now and asks for a name
 * only where it cannot answer without one, so what is left of that screen is the part that does
 * the work.
 */
class AuthViewModel : ViewModel() {
    private var signInJob: Job? = null
    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /**
     * The account, once there is one. Null until then.
     *
     * The sheet shows it for a beat before it goes, so somebody who has three Google accounts on
     * the phone can see which of them they just picked. Signing in and having the sheet vanish
     * leaves that unanswered, and the only way to check would be the profile tab.
     */
    var signedInAs by mutableStateOf<String?>(null)
        private set

    fun signIn(activity: Activity) {
        if (busy) return
        busy = true
        message = null
        signedInAs = null
        signInJob =
            viewModelScope.launch {
                try {
                    completeSignIn(
                        authenticate = {
                            AuthRepo.signIn(activity).also {
                                if (AuthRepo.firebaseError != null) error("Sign-in could not be completed. Please try again.")
                            }
                        },
                        establishSession = { ApiAuth.authenticate(activity.applicationContext) },
                        publish = {
                            Session.updateEmail(it.email)
                            signedInAs = it.email
                        },
                    )
                } catch (_: GetCredentialCancellationException) {
                    // Somebody shut the account picker. That is an answer, not a fault, and it was
                    // being reported back to them as "you closed the sign-in", which is the app
                    // narrating what they had just done as though it were a problem.
                } catch (e: CancellationException) {
                    // Navigating away cancels this scope, and CancellationException is an Exception,
                    // so the catch below was turning a completely normal sign-in into a failure
                    // message on the way out. Cancellation is not a failure and must not be
                    // swallowed.
                    throw e
                } catch (e: Exception) {
                    // Short, because friendly() appends the real cause when it has no better
                    // sentence of its own, and two guesses in a row read as noise.
                    message = e.friendly("Sign-in could not be completed.")
                } finally {
                    busy = false
                }
            }
    }

    fun resetResult() {
        signInJob?.cancel()
        signedInAs = null
        message = null
        busy = false
    }
}
