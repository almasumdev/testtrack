package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class SetupViewModel : ViewModel() {
    var checkingGroup by mutableStateOf(false)
        private set

    /**
     * Google would not confirm the account without being asked, so the step offers to ask.
     *
     * Deliberately not a sign-out. The app's own session is intact and everything else works;
     * only the token the membership service verifies has aged out, and one press of Google's
     * button replaces it.
     */
    var needsGoogle by mutableStateOf(false)
        private set
    var connectingDrive by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)

    /**
     * Which step [message] is an answer to, so it can be shown beside the button that asked.
     *
     * [STEP_NONE] covers anything raised without a step to blame, which still has to appear
     * somewhere rather than be swallowed.
     */
    var messageStep by mutableStateOf(STEP_NONE)

    fun say(
        step: Int,
        text: String?,
    ) {
        message = text
        messageStep = step
    }

    companion object {
        const val STEP_NONE = -1
        const val STEP_GROUP = 1
        const val STEP_DRIVE = 2
    }

    /**
     * Abandons the account and hands back to navigate.
     *
     * Setup is where a tester lands on the wrong Gmail — the checklist is keyed to the account,
     * and every step below the first fails for an address that is not in the group. Without this
     * the only way back to the picker was to uninstall.
     */
    fun signOut(
        context: Context,
        then: () -> Unit,
    ) {
        viewModelScope.launch {
            releaseSession(context)
            then()
        }
    }

    /** Re-runs the server-side membership check for the account we already hold a token for. */
    fun verifyGroup(activity: Activity) {
        checkingGroup = true
        say(STEP_GROUP, null)
        viewModelScope.launch {
            try {
                val (_, gate) = AuthRepo.recheckGroup(activity)
                say(
                    STEP_GROUP,
                    when {
                        // Checked, but not about us. The old code adopted whatever address came back,
                        // which left the session showing one Gmail while every write still belonged to
                        // the other, and stamped that account's membership onto this one. Say so and
                        // change nothing; switching accounts is what Sign out is for.
                        gate.isAboutSomeoneElse(Session.email) ->
                            "Google answered for ${gate.address()}, not ${Session.email}. Sign out at " +
                                "the top of this page and sign back in with the Gmail you want to use."

                        gate is GateResult.Failed && gate.reason == AuthRepo.NEEDS_GOOGLE -> {
                            needsGoogle = true
                            "Google needs to confirm this account again before it can check."
                        }

                        gate is GateResult.Failed -> gate.reason

                        else -> {
                            Session.updateMember(gate is GateResult.Member)
                            (gate as? GateResult.NotMember)?.let { "${it.email} isn't in the group yet." }
                        }
                    },
                )
            } catch (e: CancellationException) {
                throw e // leaving the screen cancels this; it is not a failed check
            } catch (e: Exception) {
                say(STEP_GROUP, e.friendly("We couldn't check your group membership just now. Try again in a moment."))
            }
            checkingGroup = false
        }
    }

    /**
     * Asks Google once, then checks again.
     *
     * The full sign-in, which is the only thing that mints a fresh ID token, and it does show the
     * account chooser. That is fine here and was not fine before: this runs from a control that
     * says it will, rather than appearing under a button that asked about group membership.
     */
    fun signInAgain(activity: Activity) {
        checkingGroup = true
        say(STEP_GROUP, null)
        viewModelScope.launch {
            val ok =
                runCatching { AuthRepo.signIn(activity) }
                    .onFailure { say(STEP_GROUP, it.friendly("Google didn't finish signing you in.")) }
                    .isSuccess
            checkingGroup = false
            if (ok) {
                needsGoogle = false
                verifyGroup(activity)
            }
        }
    }

    fun connectDrive(
        activity: Activity,
        launchConsent: (IntentSenderRequest) -> Unit,
    ) {
        connectingDrive = true
        say(STEP_DRIVE, null)
        viewModelScope.launch {
            try {
                // Bounded, because the task underneath this never fails on its own when Play
                // services cannot service the call. It retries quietly and the button spins for
                // as long as somebody is willing to watch it.
                val result =
                    withTimeoutOrNull(AuthRepo.AUTH_TIMEOUT_MS) {
                        AuthRepo.authorizeDrive(activity)
                    }
                val pending = result?.pendingIntent
                when {
                    result == null ->
                        say(STEP_DRIVE, "Google Play services didn't answer. Try again.")

                    result.hasResolution() && pending != null ->
                        launchConsent(IntentSenderRequest.Builder(pending.intentSender).build())

                    else -> Session.updateDriveConnected(result.accessToken != null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(STEP_DRIVE, e.friendly("Google didn't complete the Drive connection. Try it again."))
            }
            connectingDrive = false
        }
    }
}
