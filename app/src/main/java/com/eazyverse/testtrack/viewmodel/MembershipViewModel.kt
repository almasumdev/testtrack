package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.DebugOverrides
import com.eazyverse.testtrack.data.GROUP_OFFLINE
import com.eazyverse.testtrack.data.GateResult
import com.eazyverse.testtrack.data.GroupGate
import com.eazyverse.testtrack.data.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * What the testers group sheet shows. One value, so the sheet is one window whose inside changes.
 *
 * A failed check is its own face and never a [Join] or a [StillOut]: telling somebody who joined
 * an hour ago that they are not in the group is the answer most likely to make them give up.
 */
sealed interface GroupUi {
    /** Asking the server, in front of an action that needs the group. */
    data object Checking : GroupUi

    /** Not in the group (or not known yet): the way to join. [opened] once the group page was opened. */
    data class Join(
        val opened: Boolean,
    ) : GroupUi

    /** The way to join is still up and a check is running behind it. */
    data object Rechecking : GroupUi

    /** The server said yes. The sheet shows it for a moment, then closes. */
    data object Joined : GroupUi

    /** Checked again after a no, and the answer was still no. */
    data object StillOut : GroupUi

    /** The server could not be asked. [retrying] while another try is running. */
    data class Failed(
        val reason: String,
        val retrying: Boolean = false,
    ) : GroupUi
}

/** The failure was a dropped connection, not a message the server sent. */
val GroupUi.Failed.offline: Boolean get() = reason == GROUP_OFFLINE

/** What the view model needs from the outside: the account, the answer and where a yes is kept. */
internal interface GroupPort {
    val email: String?
    val member: Boolean

    suspend fun check(): GateResult

    fun saveMember(value: Boolean)
}

/** The real thing. A yes reads as a no while the debug switch "Force not in testers group" is on. */
internal object LivePort : GroupPort {
    override val email: String? get() = Session.email
    override val member: Boolean get() = Session.isGroupMember

    override suspend fun check(): GateResult = DebugOverrides.group(GroupGate.check(""))

    override fun saveMember(value: Boolean) = Session.updateMember(value)
}

/** The Checking face stays at least this long, so a fast answer does not flash by unread. */
internal const val CHECKING_HOLD_MS = 600L

/**
 * A dismiss that arrives this soon after the app came back from being away is the window settling,
 * not somebody putting the sheet away. See [MembershipViewModel.dismiss].
 */
internal const val DISMISS_GRACE_MS = 800L

/** Saved with the activity: the account the sheet was left open for while the group page is in front. */
private const val SAVED_WAITING = "group_sheet_waiting_for"

/**
 * Whether this account is on the testers group, and the one sheet that gets it there.
 *
 * Being on the group is what lets an owner add somebody to a closed test, so a review or a crew
 * test from outside it cannot be done properly. Signing in never opens the sheet. It is asked in
 * front of every action that needs it (see [require]), and from the places that are about joining
 * (see [startJoin]). Once the server says yes it is remembered on the phone and nobody is asked
 * again; a no is never remembered, so joining takes effect the next time anything is tapped.
 *
 * Every check has an id. Only the newest check of the sheet that is open may change what it shows;
 * an older answer, or one that arrives after the sheet was closed, never lands in a sheet. A late
 * yes is still true, so it is kept on the phone quietly; a late no or failure is dropped.
 *
 * One per activity, so the sheet one button opens is the same one every other button opens.
 */
class MembershipViewModel internal constructor(
    private val port: GroupPort,
    private val injected: CoroutineScope?,
    private val holdMs: Long,
    private val saved: SavedStateHandle = SavedStateHandle(),
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {
    /** What the framework builds, handing the activity's saved state so a killed process can come back to the sheet. */
    constructor(saved: SavedStateHandle) : this(LivePort, null, CHECKING_HOLD_MS, saved)

    private val scope: CoroutineScope get() = injected ?: viewModelScope

    val member: Boolean get() = port.member
    val email: String get() = port.email.orEmpty()

    /** What the sheet shows; null while it is closed. */
    var ui by mutableStateOf<GroupUi?>(null)
        private set

    /** The sheet is up. */
    val asking: Boolean get() = ui != null

    private var pending: (() -> Unit)? = null

    /**
     * The group page was opened and nothing has looked since. Kept in the saved state too: the phone
     * may end this process while the browser is in front, and the sheet must still be there, on the
     * same step, when the person comes back.
     */
    private var visited = false
        set(value) {
            field = value
            saved[SAVED_WAITING] = if (value) sheetFor else null
        }

    /** The app is in the background (paused), so a dismiss now was not somebody's hand. */
    private var away = false

    /** When the app last came back from being away. */
    private var backAt: Long? = null

    /** A no has been shown (or the sheet opened on the way to join), so the next no is "still". */
    private var sawOut = false

    /** The account the open sheet was opened for. */
    private var sheetFor: String? = null

    /** The newest check of the open sheet. Anything with another id is old. */
    private var checkId = 0
    private val jobs: MutableSet<Job> = ConcurrentHashMap.newKeySet()
    private var quietJob: Job? = null
    private var quietFor: String? = null

    init {
        // Back after the process was ended while the group page was in front: the same sheet, on
        // the same step. The resume that follows looks again, as it does after any return.
        val waiting = saved.get<String>(SAVED_WAITING)
        if (waiting != null && sameAccount(waiting, port.email)) {
            sheetFor = waiting
            sawOut = true
            visited = true
            ui = GroupUi.Join(opened = true)
        } else if (waiting != null) {
            saved[SAVED_WAITING] = null
        }
    }

    /** Run [action] if this account is on the group; otherwise check, and run it once it is. */
    fun require(action: () -> Unit) {
        // Signed out, the action leads to signing in first, and this runs again after that.
        if (member || port.email == null) {
            action()
            return
        }
        open(GroupUi.Checking, sawOutAlready = false)
        pending = action
        runCheck(hold = true)
    }

    /**
     * From the places about joining: straight to the way to join, with a check running behind it.
     * A yes moves the sheet on; a no or a failure leaves it as it is.
     */
    fun startJoin() {
        if (port.email == null) return
        open(GroupUi.Join(opened = false), sawOutAlready = true)
        runCheck(quiet = true)
    }

    /** On launch and after signing in, with no sheet: a yes is remembered, a no waits for the next action to say so. */
    fun quietly() {
        if (member || ui != null || port.email == null) return
        val who = port.email
        // One at a time, but a run for another account is dropped, not waited for.
        if (quietJob?.isActive == true) {
            if (quietFor == who) return
            cancelQuiet()
        }
        quietFor = who
        quietJob =
            scope.launch {
                val answer = port.check()
                // The answer belongs to whoever asked. If the account changed meanwhile, saying
                // yes would hand the next account a membership it never proved.
                if (answer is GateResult.Member && sameAccount(who, port.email)) port.saveMember(true)
            }
    }

    /** The silent check is dropped, so nothing it would have said is remembered. */
    fun cancelQuiet() {
        quietJob?.cancel()
        quietJob = null
        quietFor = null
    }

    /** Signed out: no check of any kind may outlive the account it was asked for. */
    fun signedOut() {
        cancelQuiet()
        jobs.forEach { it.cancel() }
        jobs.clear()
        close()
    }

    /** The signed in account is not the one the sheet was opened for: close it. */
    fun accountChanged() {
        if (ui != null && !sameAccount(sheetFor, port.email)) close()
    }

    /** From "I already joined", "Check again" and "Try again". */
    fun check() {
        ui =
            when (val now = ui) {
                is GroupUi.Join, GroupUi.StillOut -> GroupUi.Rechecking
                is GroupUi.Failed -> if (now.retrying) return else now.copy(retrying = true)
                // Nothing to look again at, or a look already under way.
                else -> return
            }
        runCheck()
    }

    /**
     * "Open the group" was tapped. It only notes that the page is open (step one ticked); no check
     * starts until the app is back in front, see [returned].
     */
    fun openGroup() {
        if (ui == null) return
        visited = true
        if (ui is GroupUi.Join) ui = GroupUi.Join(opened = true)
    }

    /** The app left the foreground, for the group page or for anything else. */
    fun paused() {
        away = true
    }

    /**
     * The app is in front again. If the group page was opened from the sheet, look now: this is the
     * only place that check starts. Without that visit a resume does nothing.
     */
    fun returned() {
        if (away) {
            away = false
            backAt = clock()
        }
        if (!visited) return
        if (ui is GroupUi.Join || ui is GroupUi.StillOut) {
            visited = false
            check()
        }
    }

    /**
     * The sheet reports it was dismissed (swipe, Back, tap outside). True when that was somebody's
     * hand and the sheet is now closed. False when the app was in the background or had only just
     * come back: a window being torn down and rebuilt around the browser also reports a dismiss,
     * and acting on it would lose the sheet. The caller then shows the sheet again from [ui].
     */
    fun dismiss(): Boolean {
        if (ui == null) return true
        if (away || backAt?.let { clock() - it < DISMISS_GRACE_MS } == true) return false
        close()
        return true
    }

    /**
     * The Joined face has had its moment: whatever was waiting on the check goes ahead. The sheet
     * is already gone when this is called. Only a yes that was shown and not closed runs anything.
     */
    fun proceed() {
        val then = pending.takeIf { ui == GroupUi.Joined }
        close()
        then?.invoke()
    }

    /**
     * Closed, by Not now, a swipe, a tap outside or Back, at any point. What was waiting is
     * forgotten. A check still running is not stopped: if it ends in a yes that is kept quietly,
     * and nothing is shown for it.
     */
    fun close() {
        // Whatever is in flight is old from here on.
        checkId++
        ui = null
        visited = false
        sawOut = false
        pending = null
        sheetFor = null
    }

    private fun open(
        first: GroupUi,
        sawOutAlready: Boolean,
    ) {
        checkId++
        visited = false
        sawOut = sawOutAlready
        pending = null
        sheetFor = port.email
        ui = first
    }

    /** One check for the open sheet. [hold] keeps the Checking face up, [quiet] shows nothing unless it is a yes. */
    private fun runCheck(
        hold: Boolean = false,
        quiet: Boolean = false,
    ) {
        val id = ++checkId
        val who = port.email
        val started = System.nanoTime()
        val job =
            scope.launch {
                val answer = port.check()
                if (hold && id == checkId) {
                    val left = holdMs - (System.nanoTime() - started) / 1_000_000
                    if (left > 0) delay(left)
                }
                land(id, who, answer, quiet)
            }
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }

    /** An answer arrived. */
    private fun land(
        id: Int,
        who: String?,
        answer: GateResult,
        quiet: Boolean,
    ) {
        val live = id == checkId && ui != null
        if (!sameAccount(who, port.email)) {
            // Another account is signed in now; this answer was not about it, and keeps nothing.
            if (live) close()
            return
        }
        if (!live) {
            // Closed or replaced meanwhile. A yes is still true; nothing is shown for it.
            if (answer is GateResult.Member) port.saveMember(true)
            return
        }
        when (answer) {
            is GateResult.Member -> {
                port.saveMember(true)
                ui = GroupUi.Joined
            }
            is GateResult.NotMember ->
                if (!quiet) {
                    port.saveMember(false)
                    ui = if (sawOut) GroupUi.StillOut else GroupUi.Join(opened = visited)
                    sawOut = true
                }
            is GateResult.Failed -> if (!quiet) ui = GroupUi.Failed(answer.reason)
        }
    }
}

/** The same account is still signed in as when a check began. Signed out is never the same. */
internal fun sameAccount(
    started: String?,
    now: String?,
): Boolean = started != null && started == now
