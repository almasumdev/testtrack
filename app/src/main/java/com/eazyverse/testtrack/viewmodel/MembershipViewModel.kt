package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.DebugOverrides
import com.eazyverse.testtrack.data.GateResult
import com.eazyverse.testtrack.data.GroupGate
import com.eazyverse.testtrack.data.Session
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Whether this account is on the testers group, and the dialog that gets it there.
 *
 * Being on the group is what lets an owner add somebody to a closed test, so a review or a crew
 * test from outside it cannot be done properly. Signing in never opens the dialog. It is asked
 * in front of every action that needs it: taking a review on, each of its steps and the submit,
 * and taking a crew seat, running a crew's apps, installing or updating them and sending their
 * feedback. Once the server says yes it is remembered on the phone, the dialog says so and waits
 * for Continue, and nobody is asked again; a no is never remembered, so joining takes effect
 * the next time anything is tapped.
 *
 * One per activity, so the dialog one button opens is the same one every other button opens.
 */
class MembershipViewModel : ViewModel() {
    val member: Boolean get() = Session.isGroupMember
    val email: String get() = Session.email.orEmpty()

    /** The dialog is up. */
    var asking by mutableStateOf(false)
        private set
    var checking by mutableStateOf(false)
        private set

    /** The server answered, and the answer was no. */
    var outside by mutableStateOf(false)
        private set

    /** The server could not be asked. Never treated as a no. */
    var failure by mutableStateOf<String?>(null)
        private set

    /** The server said yes. The dialog says so and stays until Continue. */
    var confirmed by mutableStateOf(false)
        private set

    /** Checked again after a no, and the answer was still no. */
    var stillOut by mutableStateOf(false)
        private set
    private var openedGroup = false
    private var pending: (() -> Unit)? = null
    private var job: Job? = null
    private var quietJob: Job? = null
    private var quietFor: String? = null

    /** Run [action] if this account is on the group; otherwise check, and run it once it is. */
    fun require(action: () -> Unit) {
        // Signed out, the action leads to signing in first, and this runs again after that.
        if (member || !Session.signedIn) {
            action()
            return
        }
        pending = action
        ask()
    }

    /** From the Me page: check now, in front of the person, whatever was remembered. */
    fun show() {
        pending = null
        ask()
    }

    /** On launch and after signing in, with no dialog: a yes is remembered, a no waits for the next action to say so. */
    fun quietly() {
        if (member || checking || !Session.signedIn) return
        val who = Session.email
        // One at a time, but a run for another account is dropped, not waited for.
        if (quietJob?.isActive == true) {
            if (quietFor == who) return
            cancelQuiet()
        }
        quietFor = who
        quietJob =
            viewModelScope.launch {
                val answer = DebugOverrides.group(GroupGate.check(""))
                // The answer belongs to whoever asked. If the account changed meanwhile, saying
                // yes would hand the next account a membership it never proved.
                if (answer is GateResult.Member && sameAccount(who, Session.email)) Session.updateMember(true)
            }
    }

    /** The silent check is dropped, so nothing it would have said is remembered. */
    fun cancelQuiet() {
        quietJob?.cancel()
        quietJob = null
        quietFor = null
    }

    /** Signed out: no check of either kind may outlive the account it was asked for. */
    fun signedOut() {
        cancelQuiet()
        close()
    }

    fun check() {
        if (checking) return
        checking = true
        failure = null
        val again = outside
        val who = Session.email
        job =
            viewModelScope.launch {
                val answer = DebugOverrides.group(GroupGate.check(""))
                if (!sameAccount(who, Session.email)) {
                    // Another account is signed in now; this answer was not about it.
                    checking = false
                    return@launch
                }
                when (answer) {
                    is GateResult.Member -> {
                        Session.updateMember(true)
                        confirmed = true
                    }
                    is GateResult.NotMember -> {
                        Session.updateMember(false)
                        stillOut = again
                        outside = true
                    }
                    is GateResult.Failed -> failure = answer.reason
                }
                checking = false
            }
    }

    /** Continue, from "you're all set": whatever was waiting on the check goes ahead. */
    fun proceed() {
        val then = pending
        close()
        then?.invoke()
    }

    fun openedGroup() {
        openedGroup = true
    }

    /** Back from the group page: look again without waiting to be asked. */
    fun returned() {
        if (asking && openedGroup && outside && !checking) check()
    }

    /**
     * Closed, by Cancel, Not now or Back. A check still running is dropped, so nothing it would
     * have said is remembered: the account stays unverified until it is checked again, from the
     * next action that needs it or from the Me page.
     */
    fun close() {
        job?.cancel()
        job = null
        checking = false
        confirmed = false
        stillOut = false
        asking = false
        outside = false
        failure = null
        openedGroup = false
        pending = null
    }

    private fun ask() {
        asking = true
        outside = false
        confirmed = false
        stillOut = false
        check()
    }
}

/** The same account is still signed in as when a check began. Signed out is never the same. */
internal fun sameAccount(
    started: String?,
    now: String?,
): Boolean = started != null && started == now
