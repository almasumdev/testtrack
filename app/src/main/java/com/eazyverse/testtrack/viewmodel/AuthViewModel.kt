package com.eazyverse.testtrack.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AuthRepo
import com.eazyverse.testtrack.data.Connectivity
import com.eazyverse.testtrack.data.Session
import com.eazyverse.testtrack.data.StarterNotice
import com.eazyverse.testtrack.domain.completeSignIn
import com.eazyverse.testtrack.net.ApiAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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

    /**
     * What the sheet shows. Once there is an account it carries it, and the sheet shows it for a
     * beat before it goes, so somebody who has three Google accounts on the phone can see which of
     * them they just picked. Signing in and having the sheet vanish leaves that unanswered, and the
     * only way to check would be the profile tab.
     */
    var ui by mutableStateOf<SignInUi>(SignInUi.Idle)
        private set

    fun signIn(activity: Activity) {
        if (ui is SignInUi.Working) return
        val context = activity.applicationContext
        // Asked first, so a phone with no network is told so at once instead of after Google's
        // chooser has opened and failed. Only the loose check: a network that is not VALIDATED may
        // still work, so it is never a reason to refuse to try.
        if (!Connectivity.hasNetwork(context)) {
            ui = SignInUi.Offline
            return
        }
        ui = SignInUi.Working
        signInJob =
            viewModelScope.launch {
                try {
                    completeSignIn(
                        authenticate = { AuthRepo.signIn(activity) },
                        establishSession = { ApiAuth.authenticate(context) },
                        publish = { account ->
                            Session.updateEmail(account.email)
                            ui =
                                SignInUi.SignedIn(
                                    email = account.email,
                                    name = account.name,
                                    photo = account.photo,
                                    starterUsed = StarterNotice.take(context, AuthRepo.uid),
                                )
                        },
                    )
                } catch (e: TimeoutCancellationException) {
                    // Caught before CancellationException on purpose: a timeout is one, and it is
                    // a failure to sort, not somebody navigating away.
                    fail(e, context)
                } catch (e: CancellationException) {
                    // Navigating away cancels this scope, and CancellationException is an Exception,
                    // so the catch below was turning a completely normal sign-in into a failure
                    // message on the way out. Cancellation is not a failure and must not be
                    // swallowed.
                    AuthRepo.undoHalfSignIn()
                    throw e
                } catch (e: Exception) {
                    fail(e, context)
                } finally {
                    // A cancelled job never reaches a result, and the sheet must not be left
                    // working (and unclosable) behind it.
                    if (ui is SignInUi.Working) ui = SignInUi.Idle
                }
            }
    }

    private fun fail(
        e: Exception,
        context: Context,
    ) {
        AuthRepo.undoHalfSignIn()
        Log.w("TestTrack", "Sign-in did not finish", e)
        ui = sortSignInFailure(e) { !Connectivity.isOnline(context) }
    }

    fun resetResult() {
        signInJob?.cancel()
        ui = SignInUi.Idle
    }
}
