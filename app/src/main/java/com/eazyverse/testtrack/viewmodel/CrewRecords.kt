package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiCrewRecord
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.answerProdAccess
import com.eazyverse.testtrack.net.claimPerfect
import com.eazyverse.testtrack.net.crewRecords
import com.eazyverse.testtrack.net.lastCrewRecords
import com.eazyverse.testtrack.ui.CrewRecord
import com.eazyverse.testtrack.ui.RecordBusy
import com.eazyverse.testtrack.ui.RecordOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Record tab's list: one kept record per finished crew, read from GET /me/crew-records.
 *
 * Listed directly rather than joined onto the crews, because a member whose app was moved or taken
 * out cannot open that crew any more and still has a record of it. The crew list only adds whether
 * its full detail is still there.
 *
 * Also the two things a record asks: the production question and a perfect run's claim. Each
 * answer comes back as the updated record and replaces it in place. Kept apart from the Crew
 * page's view model, which [say] leads back to.
 */
class CrewRecords(
    private val scope: CoroutineScope,
    private val say: (String) -> Unit,
) {
    /** Newest crew first; null until the first answer. */
    var items by mutableStateOf<List<CrewRecord>?>(null)
        private set

    /** The last read failed and nothing is kept to show instead. */
    var failed by mutableStateOf(false)
        private set

    /** Which button is sending, per crew. One action per crew at a time. */
    var busy by mutableStateOf<Map<String, RecordBusy>>(emptyMap())
        private set

    private var loads = 0
    private var previewing = false

    init {
        Api.lastCrewRecords()?.let { kept -> items = kept.items.map { it.toRecord() } }
    }

    /** Read them again. Signed out there are none to read. */
    fun load(signedOut: Boolean) {
        if (previewing) return
        if (signedOut) {
            items = emptyList()
            failed = false
            return
        }
        val mine = ++loads
        scope.launch {
            try {
                val answer = Api.crewRecords()
                if (mine != loads) return@launch
                items = answer.items.map { it.toRecord() }
                failed = false
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (mine != loads) return@launch
                if (items == null) {
                    items = Api.lastCrewRecords(afterFailure = true)?.items?.map { it.toRecord() }
                }
                failed = items == null
            }
        }
    }

    /** "Got production access?": Yes or No, once. */
    fun answerProdAccess(
        crewId: String,
        granted: Boolean,
    ) = act(crewId, if (granted) RecordBusy.YES else RecordBusy.NO) { Api.answerProdAccess(crewId, granted) }

    /** A perfect run's tix, claimed once. */
    fun claimPerfect(crewId: String) = act(crewId, RecordBusy.CLAIM) { Api.claimPerfect(crewId) }

    private fun act(
        crewId: String,
        what: RecordBusy,
        block: suspend () -> ApiCrewRecord,
    ) {
        if (crewId in busy) return
        busy = busy + (crewId to what)
        scope.launch {
            try {
                val updated = block().toRecord()
                items = items?.map { if (it.crewId == updated.crewId) updated else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                say(COULD_NOT_SEND)
                // Answered or claimed already, maybe from another phone: show what the server has.
                if (e is ApiError && e.status == 409) load(signedOut = false)
            } finally {
                busy = busy - crewId
            }
        }
    }

    /** Records put in directly, for the screen gallery. Nothing in the app calls this. */
    internal fun preview(records: List<CrewRecord>) {
        previewing = true
        items = records
        failed = false
        busy = emptyMap()
    }

    private companion object {
        const val COULD_NOT_SEND = "Couldn't send. Try again."
    }
}

private fun ApiCrewRecord.toRecord() =
    CrewRecord(
        crewId = crewId,
        crewNumber = crewNumber,
        crewName = crewName,
        startedAt = startedAt,
        endedAt = endedAt,
        runDays = runDays,
        appName = appName,
        packageName = packageName,
        appIcon = appIcon,
        outcome = RecordOutcome.of(outcome),
        leftDay = leftDay,
        testersEveryDay = testersEveryDay,
        counted = counted,
        floor = floor,
        movedToCrewNumber = movedToCrewNumber,
        outReason = outReason,
        doneAt = doneAt,
        prodAccess = prodAccess,
        prodAccessUntil = prodAccessUntil,
        prodAccessReward = prodAccessReward,
        perfectClaimAmount = perfectClaimAmount,
        perfectClaimedAt = perfectClaimedAt,
        perfectClaimable = perfectClaimable,
        pruned = pruned,
    )
