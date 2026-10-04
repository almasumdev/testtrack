package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.GateResult
import com.eazyverse.testtrack.data.GroupGate
import com.eazyverse.testtrack.data.Session
import kotlinx.coroutines.launch

/**
 * Whether this account is on the testers group, and the dialog that gets it there.
 *
 * Being on the group is what lets an owner add somebody to a closed test, so a review or a crew
 * test from outside it cannot be done properly. It is checked straight after signing in, and
 * again in front of every action that needs it: taking a review on, each of its steps and the
 * submit, and running a crew's apps. Once the server says yes it is remembered on the phone and
 * nobody is asked again; a no is never remembered, so joining takes effect the next time anything
 * is tapped.
 *
 * One per activity, so the dialog the sign-in opens and the one a button opens are the same.
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
    private var openedGroup = false
    private var pending: (() -> Unit)? = null

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

    /** Straight after signing in: checked in front of the person, with nothing waiting on it. */
    fun afterSignIn() {
        if (member) return
        pending = null
        ask()
    }

    /** On launch: a yes is remembered quietly, a no waits for the next action to say so. */
    fun quietly() {
        if (member || checking || !Session.signedIn) return
        viewModelScope.launch {
            if (GroupGate.check("") is GateResult.Member) Session.updateMember(true)
        }
    }

    fun check() {
        if (checking) return
        checking = true
        failure = null
        viewModelScope.launch {
            when (val answer = GroupGate.check("")) {
                is GateResult.Member -> {
                    Session.updateMember(true)
                    val then = pending
                    close()
                    then?.invoke()
                }
                is GateResult.NotMember -> {
                    Session.updateMember(false)
                    outside = true
                }
                is GateResult.Failed -> failure = answer.reason
            }
            checking = false
        }
    }

    fun openedGroup() {
        openedGroup = true
    }

    /** Back from the group page: look again without waiting to be asked. */
    fun returned() {
        if (asking && openedGroup && outside) check()
    }

    fun close() {
        asking = false
        outside = false
        failure = null
        openedGroup = false
        pending = null
    }

    private fun ask() {
        asking = true
        outside = false
        check()
    }
}
