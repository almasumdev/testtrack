package com.eazyverse.testtrack.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.eazyverse.testtrack.data.GateResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The group sheet's rules: which face each answer leads to, and that an answer which is old, late
 * or about somebody else never changes a sheet. Every check here is held open by the test, so the
 * order things arrive in is the test's, not the clock's.
 */
class MembershipViewModelTest {
    private class FakePort : GroupPort {
        override var email: String? = "a@x.com"
        override var member = false
        val saved = mutableListOf<Boolean>()
        val asks = mutableListOf<CompletableDeferred<GateResult>>()

        override suspend fun check(): GateResult {
            val ask = CompletableDeferred<GateResult>()
            asks += ask
            return ask.await()
        }

        override fun saveMember(value: Boolean) {
            saved += value
            member = value
        }

        fun answer(
            n: Int,
            result: GateResult,
        ) {
            asks[n].complete(result)
        }
    }

    private val port = FakePort()
    private val yes = GateResult.Member("a@x.com")
    private val no = GateResult.NotMember("a@x.com")
    private val offline = GateResult.Failed("Couldn't check the testers group. Check your connection and try again.")
    private var ran = 0

    private var now = 10_000L
    private var saved = SavedStateHandle()

    private fun vm(holdMs: Long = 0) =
        MembershipViewModel(port, CoroutineScope(Dispatchers.Unconfined + SupervisorJob()), holdMs, saved) { now }

    private fun MembershipViewModel.gate() = require { ran++ }

    @Test
    fun aGateOpensOnCheckingAndAYesRunsTheActionOnlyAfterTheSheetCloses() {
        val vm = vm()
        vm.gate()
        assertEquals(GroupUi.Checking, vm.ui)
        port.answer(0, yes)
        assertEquals(GroupUi.Joined, vm.ui)
        assertEquals(listOf(true), port.saved)
        assertEquals(0, ran)
        vm.proceed()
        assertNull(vm.ui)
        assertEquals(1, ran)
    }

    @Test
    fun aMemberOrASignedOutPhoneRunsTheActionAtOnceWithNoSheet() {
        val vm = vm()
        port.member = true
        vm.gate()
        port.member = false
        port.email = null
        vm.gate()
        assertEquals(2, ran)
        assertNull(vm.ui)
    }

    @Test
    fun aFirstNoShowsTheWayToJoinAndASecondNoSaysStillOut() {
        val vm = vm()
        vm.gate()
        port.answer(0, no)
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        vm.openGroup()
        assertEquals(GroupUi.Join(opened = true), vm.ui)
        vm.check()
        assertEquals(GroupUi.Rechecking, vm.ui)
        port.answer(1, no)
        assertEquals(GroupUi.StillOut, vm.ui)
        vm.check()
        port.answer(2, no)
        assertEquals(GroupUi.StillOut, vm.ui)
        assertEquals(0, ran)
    }

    @Test
    fun aFailureIsItsOwnFaceAndNeverANo() {
        val vm = vm()
        vm.gate()
        port.answer(0, offline)
        assertEquals(GroupUi.Failed(offline.reason), vm.ui)
        assertTrue(port.saved.isEmpty())
        vm.check()
        assertEquals(GroupUi.Failed(offline.reason, retrying = true), vm.ui)
        port.answer(1, yes)
        assertEquals(GroupUi.Joined, vm.ui)
    }

    @Test
    fun aNoAfterAFailureIsStillTheFirstNo() {
        val vm = vm()
        vm.gate()
        port.answer(0, offline)
        vm.check()
        port.answer(1, no)
        assertEquals(GroupUi.Join(opened = false), vm.ui)
    }

    @Test
    fun theCheckingFaceStaysAtLeastTheHoldTime() {
        val vm = vm(holdMs = 250)
        vm.gate()
        port.answer(0, yes)
        assertEquals(GroupUi.Checking, vm.ui)
        runBlocking {
            repeat(40) { if (vm.ui == GroupUi.Checking) delay(25) }
        }
        assertEquals(GroupUi.Joined, vm.ui)
    }

    // ---------------------------------------------------------------- safety rules

    @Test
    fun aLateYesAfterTheSheetWasClosedIsKeptQuietlyAndRunsNothing() {
        val vm = vm()
        vm.gate()
        vm.close()
        assertNull(vm.ui)
        port.answer(0, yes)
        assertNull(vm.ui)
        assertEquals(listOf(true), port.saved)
        assertEquals(0, ran)
        vm.proceed()
        assertEquals(0, ran)
    }

    @Test
    fun aLateNoOrFailureAfterClosingSavesNothing() {
        val vm = vm()
        vm.gate()
        vm.close()
        port.answer(0, no)
        vm.gate()
        vm.close()
        port.answer(1, offline)
        assertTrue(port.saved.isEmpty())
        assertNull(vm.ui)
    }

    @Test
    fun anOldAnswerNeverLandsInANewSheet() {
        val vm = vm()
        vm.gate()
        vm.close()
        vm.gate()
        assertEquals(GroupUi.Checking, vm.ui)
        // The first sheet's check answers no: the new sheet does not move.
        port.answer(0, no)
        assertEquals(GroupUi.Checking, vm.ui)
        port.answer(1, no)
        assertEquals(GroupUi.Join(opened = false), vm.ui)
    }

    @Test
    fun anOldYesIsKeptButDoesNotMoveTheNewSheet() {
        val vm = vm()
        vm.gate()
        vm.close()
        vm.gate()
        port.answer(0, yes)
        assertEquals(GroupUi.Checking, vm.ui)
        assertEquals(listOf(true), port.saved)
        assertEquals(0, ran)
    }

    @Test
    fun aCheckStartedLaterOutranksAnEarlierOneInTheSameSheet() {
        val vm = vm()
        vm.gate()
        port.answer(0, no)
        vm.check()
        vm.check()
        vm.startJoin()
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        vm.check()
        assertEquals(GroupUi.Rechecking, vm.ui)
        // asks: 0 gate, 1 recheck, 2 startJoin quiet, 3 recheck. Only the last may move the sheet.
        port.answer(1, no)
        port.answer(2, offline)
        assertEquals(GroupUi.Rechecking, vm.ui)
        port.answer(3, yes)
        assertEquals(GroupUi.Joined, vm.ui)
    }

    @Test
    fun closingStopsWhatWasWaitingAndItNeverRunsEvenAfterAYes() {
        val vm = vm()
        vm.gate()
        port.answer(0, yes)
        assertEquals(GroupUi.Joined, vm.ui)
        // Closed during the moment the joined face is up.
        vm.close()
        vm.proceed()
        assertEquals(0, ran)
        assertEquals(listOf(true), port.saved)
        assertTrue(port.member)
    }

    @Test
    fun anAnswerForAnotherAccountClosesTheSheetAndKeepsNothing() {
        val vm = vm()
        vm.gate()
        port.email = "b@x.com"
        port.answer(0, yes)
        assertNull(vm.ui)
        assertTrue(port.saved.isEmpty())
        assertEquals(0, ran)
    }

    @Test
    fun aLateYesForAnotherAccountIsNotKept() {
        val vm = vm()
        vm.gate()
        vm.close()
        port.email = "b@x.com"
        port.answer(0, yes)
        assertTrue(port.saved.isEmpty())
    }

    @Test
    fun theSheetClosesWhenTheAccountChangesOrSignsOut() {
        val vm = vm()
        vm.startJoin()
        port.email = "b@x.com"
        vm.accountChanged()
        assertNull(vm.ui)
        vm.startJoin()
        vm.signedOut()
        assertNull(vm.ui)
        // The check that was running is cancelled, so its yes is not kept for anybody.
        port.answer(1, yes)
        assertTrue(port.saved.isEmpty())
    }

    // ---------------------------------------------------------------- the entry points

    @Test
    fun startJoinOpensOnJoinAndAQuietNoOrFailureLeavesItAlone() {
        val vm = vm()
        vm.startJoin()
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        port.answer(0, no)
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        vm.startJoin()
        port.answer(1, offline)
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        assertTrue(port.saved.isEmpty())
    }

    @Test
    fun startJoinMovesToJoinedOnAYesAndRunsNothing() {
        val vm = vm()
        vm.startJoin()
        port.answer(0, yes)
        assertEquals(GroupUi.Joined, vm.ui)
        vm.proceed()
        assertNull(vm.ui)
        assertEquals(0, ran)
    }

    @Test
    fun startJoinWhileSignedOutDoesNothing() {
        val vm = vm()
        port.email = null
        vm.startJoin()
        assertNull(vm.ui)
    }

    @Test
    fun comingBackFromTheGroupChecksAgainOnceAndNotWithoutHavingGone() {
        val vm = vm()
        vm.startJoin()
        vm.returned()
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        vm.openGroup()
        vm.returned()
        assertEquals(GroupUi.Rechecking, vm.ui)
        port.answer(1, no)
        assertEquals(GroupUi.StillOut, vm.ui)
        // A second resume without going to the group again does not look again.
        vm.returned()
        assertEquals(GroupUi.StillOut, vm.ui)
    }

    // ---------------------------------------------------------------- going to the group page

    @Test
    fun openingTheGroupStartsNoCheckAndTicksStepOne() {
        val vm = vm()
        vm.startJoin()
        // The one ask so far is the quiet look at opening; tapping Open the group adds none.
        assertEquals(1, port.asks.size)
        vm.openGroup()
        assertEquals(1, port.asks.size)
        assertEquals(GroupUi.Join(opened = true), vm.ui)
    }

    @Test
    fun comingBackAfterOpeningTheGroupStartsExactlyOneCheck() {
        val vm = vm()
        vm.startJoin()
        vm.openGroup()
        vm.paused()
        assertEquals(1, port.asks.size)
        vm.returned()
        assertEquals(GroupUi.Rechecking, vm.ui)
        assertEquals(2, port.asks.size)
        // Another resume while it runs, or after, adds none.
        vm.returned()
        assertEquals(2, port.asks.size)
        port.answer(1, no)
        assertEquals(GroupUi.StillOut, vm.ui)
        vm.returned()
        assertEquals(2, port.asks.size)
    }

    @Test
    fun comingBackWithoutOpeningTheGroupStartsNoCheck() {
        val vm = vm()
        vm.startJoin()
        vm.paused()
        vm.returned()
        assertEquals(GroupUi.Join(opened = false), vm.ui)
        assertEquals(1, port.asks.size)
    }

    @Test
    fun theReturnLandsOnJoinedOrStillOutOrFailed() {
        for ((answer, face) in listOf(yes to GroupUi.Joined, no to GroupUi.StillOut, offline to GroupUi.Failed(offline.reason))) {
            port.asks.clear()
            val vm = vm()
            vm.startJoin()
            vm.openGroup()
            vm.paused()
            vm.returned()
            assertEquals(GroupUi.Rechecking, vm.ui)
            port.answer(1, answer)
            assertEquals(face, vm.ui)
        }
    }

    @Test
    fun theSheetFaceSurvivesThePauseAndADismissFromTheWindowComingDown() {
        val vm = vm()
        vm.startJoin()
        vm.openGroup()
        vm.paused()
        // The window reporting a dismiss while the browser is in front is not a hand.
        assertFalse(vm.dismiss())
        assertEquals(GroupUi.Join(opened = true), vm.ui)
        now += 100
        vm.returned()
        // Nor is one in the moment right after coming back, and the check still runs.
        assertFalse(vm.dismiss())
        assertEquals(GroupUi.Rechecking, vm.ui)
        now += DISMISS_GRACE_MS + 1
        // A real swipe later closes it, and forgets the page was opened.
        assertTrue(vm.dismiss())
        assertNull(vm.ui)
    }

    @Test
    fun aDismissWithNoPauseInvolvedClosesTheSheet() {
        val vm = vm()
        vm.startJoin()
        assertTrue(vm.dismiss())
        assertNull(vm.ui)
    }

    @Test
    fun aKilledProcessComesBackToTheSameSheetAndLooksOnResume() {
        val first = vm()
        first.startJoin()
        first.openGroup()
        // The phone ends the process while the browser is up; the saved state is what survives.
        port.asks.clear()
        val second = vm()
        assertEquals(GroupUi.Join(opened = true), second.ui)
        assertEquals(0, port.asks.size)
        second.returned()
        assertEquals(GroupUi.Rechecking, second.ui)
        assertEquals(1, port.asks.size)
    }

    @Test
    fun aClosedSheetLeavesNothingToRestore() {
        val first = vm()
        first.startJoin()
        first.openGroup()
        first.close()
        assertNull(vm().ui)
        // And one saved for another account is dropped, not shown.
        val other = vm()
        other.startJoin()
        other.openGroup()
        port.email = "b@x.com"
        assertNull(vm().ui)
    }

    @Test
    fun quietlyRemembersAYesWithNoSheetAndDropsAnotherAccountsAnswer() {
        val vm = vm()
        vm.quietly()
        assertNull(vm.ui)
        port.answer(0, yes)
        assertEquals(listOf(true), port.saved)
        port.member = false
        port.saved.clear()
        vm.quietly()
        port.email = "b@x.com"
        port.answer(1, yes)
        assertTrue(port.saved.isEmpty())
    }
}
