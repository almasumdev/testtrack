package com.eazyverse.testtrack.viewmodel

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.ApiMyPrivateTest
import com.eazyverse.testtrack.net.PrivateAcceptIn
import com.eazyverse.testtrack.net.PrivateInviteIn
import com.eazyverse.testtrack.net.acceptPrivate
import com.eazyverse.testtrack.net.declinePrivate
import com.eazyverse.testtrack.net.leavePrivate
import com.eazyverse.testtrack.net.myPrivateTests
import com.eazyverse.testtrack.net.privateInvite
import com.eazyverse.testtrack.net.privateResults
import com.eazyverse.testtrack.net.removePrivate
import com.eazyverse.testtrack.ui.Snacks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Why an owner's list did not load: the server's words, and whether it was only the connection. */
data class ResultsFault(
    val message: String,
    val offline: Boolean,
)

/**
 * Private testers, both sides: the asks made of the reader (answer, open, stop) and, for an app of
 * the reader's own in a running crew, the people asked and their nights. Every rule is the
 * server's; refusals come back in its words. Shared by the Crew tab and the pages over it, so an
 * answer on one is the same answer on all of them.
 */
class PrivateTestsViewModel : ViewModel() {
    /** The reader's asks, or null before the first answer. */
    var mine by mutableStateOf<List<PrivateTest>?>(null)
        private set

    /** The ask whose sheet is open: accept or decline. */
    var picked by mutableStateOf<PrivateTest?>(null)
        private set

    /** The ask or tester an answer, a stop or a removal is being sent for. */
    var busy by mutableStateOf<String?>(null)
        private set

    /** The owner's lists, by seat, and why one did not load. */
    val results = mutableStateMapOf<String, PrivateResults>()
    val faults = mutableStateMapOf<String, ResultsFault>()

    /** An invite is on its way, and why the last one was refused, when the sheet says it. */
    var inviting by mutableStateOf(false)
        private set
    var refusal by mutableStateOf<InviteRefusal?>(null)
        private set

    private var previewing = false

    /** Asks and lists put in by hand, for the screen gallery. Nothing in the app calls this. */
    @VisibleForTesting
    fun preview(
        mine: List<PrivateTest> = emptyList(),
        results: List<PrivateResults> = emptyList(),
    ) {
        previewing = true
        this.mine = mine
        this.results.clear()
        results.forEach { this.results[it.seatId] = it }
    }

    fun loadMine() {
        if (previewing) return
        viewModelScope.launch {
            try {
                mine = mergeMine(mine, Api.myPrivateTests().tests.map { it.toTest() })
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Quiet: the rows stay as they were, and the next crew load asks again.
            }
        }
    }

    /** The owner's list for one app. [quiet] keeps a failure off the screen, for the crew page's row. */
    fun loadResults(
        seatId: String,
        quiet: Boolean = false,
    ) {
        if (previewing) return
        viewModelScope.launch {
            try {
                results[seatId] = Api.privateResults(seatId).toResults()
                faults.remove(seatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (!quiet) faults[seatId] = ResultsFault(e.message, offline = false)
            } catch (_: Exception) {
                if (!quiet) faults[seatId] = ResultsFault(OFFLINE, offline = true)
            }
        }
    }

    fun pick(test: PrivateTest?) {
        picked = test
    }

    /** Say yes: the reader's nights count from tonight, in this phone's time zone. */
    fun accept(
        test: PrivateTest,
        onDone: () -> Unit,
    ) = answer(test, "You're testing ${test.appName} for ${test.ownerName}.", onDone) {
        Api.acceptPrivate(test.id, PrivateAcceptIn(TimeZone.getDefault().id))
    }

    /** Say no. Final for this app in this run. */
    fun decline(
        test: PrivateTest,
        onDone: () -> Unit,
    ) = answer(test, "You said no to ${test.appName}.", onDone) { Api.declinePrivate(test.id) }

    /** Stop testing it. Its owner keeps the nights opened. */
    fun leave(
        test: PrivateTest,
        onDone: () -> Unit,
    ) = answer(test, "You stopped testing ${test.appName}.", onDone) { Api.leavePrivate(test.id) }

    private fun answer(
        test: PrivateTest,
        said: String,
        onDone: () -> Unit,
        call: suspend () -> ApiMyPrivateTest,
    ) {
        if (busy != null) return
        busy = test.id
        viewModelScope.launch {
            try {
                val got = call().toTest()
                mine = mine.orEmpty().mapNotNull { if (it.id == test.id) got.takeIf { got.state.open } else it }
                Snacks.done(said)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                Snacks.error(e.message)
                loadMine()
            } catch (_: Exception) {
                Snacks.error(OFFLINE) { answer(test, said, onDone, call) }
            } finally {
                busy = null
            }
        }
    }

    fun clearRefusal() {
        refusal = null
    }

    /**
     * Ask the account with exactly [email] to test the app on [seatId]. A refusal about the address
     * stays in the sheet; anything else is a snack. [onSent] closes the sheet.
     */
    fun invite(
        seatId: String,
        email: String,
        onSent: () -> Unit,
    ) {
        if (inviting) return
        val address = email.trim()
        val before = results[seatId]?.testers.orEmpty()
        inviting = true
        refusal = null
        viewModelScope.launch {
            try {
                val got = Api.privateInvite(PrivateInviteIn(seatId, address)).toTester()
                val known = before.firstOrNull { it.id == got.id }
                Snacks.done(
                    when {
                        known == null -> "Invite sent to ${got.name}"
                        got.state == PrivateState.ACCEPTED -> "${got.name} already tests it"
                        else -> "${got.name} is invited already"
                    },
                )
                onSent()
                loadResults(seatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                if (e.code in InviteRefusal.INLINE) refusal = InviteRefusal(address, e.code, e.message) else Snacks.error(e.message)
            } catch (_: Exception) {
                Snacks.error("Couldn't send the invite. Check your connection.") { invite(seatId, address, onSent) }
            } finally {
                inviting = false
            }
        }
    }

    /** Take somebody off, or take back an ask not answered yet. */
    fun remove(tester: PrivateTester) {
        if (busy != null) return
        busy = tester.id
        viewModelScope.launch {
            try {
                val got = Api.removePrivate(tester.id).toTester()
                results[tester.seatId]?.let { list ->
                    results[tester.seatId] = list.copy(testers = list.testers.map { if (it.id == got.id) got else it })
                }
                Snacks.done(if (tester.state == PrivateState.INVITED) "Invite to ${tester.name} cancelled" else "${tester.name} removed")
                loadResults(tester.seatId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                Snacks.error(e.message)
                loadResults(tester.seatId)
            } catch (_: Exception) {
                Snacks.error(OFFLINE) { remove(tester) }
            } finally {
                busy = null
            }
        }
    }

    private companion object {
        const val OFFLINE = "Couldn't reach TestTrack. Check your connection."
    }
}
