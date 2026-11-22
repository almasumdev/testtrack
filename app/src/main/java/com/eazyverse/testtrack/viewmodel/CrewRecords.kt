package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiCrewRecord
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.answerProdAccess
import com.eazyverse.testtrack.net.claimPerfect
import com.eazyverse.testtrack.net.crewRecord
import com.eazyverse.testtrack.net.crewRecords
import com.eazyverse.testtrack.net.lastCrewRecords
import com.eazyverse.testtrack.ui.CrewRecord
import com.eazyverse.testtrack.ui.NightMark
import com.eazyverse.testtrack.ui.RecordApp
import com.eazyverse.testtrack.ui.RecordBusy
import com.eazyverse.testtrack.ui.RecordOutcome
import com.eazyverse.testtrack.ui.Snacks
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
 * page's view model; a failed answer is said in a snack, with Try again.
 */
class CrewRecords(
    private val scope: CoroutineScope,
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

    /** Single records read for a finished crew's page, by crew id. */
    var opened by mutableStateOf<Map<String, CrewRecord>>(emptyMap())
        private set

    /** Crews whose page record did not load. */
    var openFailed by mutableStateOf<Set<String>>(emptySet())
        private set

    private var loads = 0
    private var previewing = false

    /** One crew's record for its page: the one read on its own, else the list's while that loads. */
    fun record(crewId: String): CrewRecord? = opened[crewId] ?: items?.firstOrNull { it.crewId == crewId }

    /** Read one crew's record again, from GET /crews/{id}/record. */
    fun loadOne(crewId: String) {
        if (previewing) return
        openFailed = openFailed - crewId
        scope.launch {
            try {
                val record = Api.crewRecord(crewId).toRecord()
                opened = opened + (crewId to record)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                openFailed = openFailed + crewId
            }
        }
    }

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
                if (updated.crewId in opened) opened = opened + (updated.crewId to updated)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is ApiError && e.status == 409) {
                    // Answered or claimed already, maybe from another phone: say so, with no retry, and
                    // show what the server has, in the list and in the record that is open.
                    Snacks.error(e.message)
                    load(signedOut = false)
                    if (crewId in opened) loadOne(crewId)
                } else {
                    Snacks.error(COULD_NOT_SEND) { act(crewId, what, block) }
                }
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
        opened = emptyMap()
        openFailed = emptySet()
    }

    private companion object {
        const val COULD_NOT_SEND = "Couldn't send."
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
        memberNights = memberNights,
        appName = appName,
        packageName = packageName,
        appIcon = appIcon,
        outcome = RecordOutcome.of(outcome),
        leftDay = leftDay,
        testersEveryDay = testersEveryDay,
        counted = counted,
        floor = floor,
        movedToCrewNumber = movedToCrewNumber,
        movedToCrewId = movedToCrewId,
        outReason = outReason,
        doneAt = doneAt,
        prodAccess = prodAccess,
        prodAccessUntil = prodAccessUntil,
        prodAccessReward = prodAccessReward,
        perfectClaimAmount = perfectClaimAmount,
        perfectClaimedAt = perfectClaimedAt,
        perfectClaimable = perfectClaimable,
        pruned = pruned,
        charged = charged,
        tixSpent = tixSpent,
        tixRefunded = tixRefunded,
        nights = nights.orEmpty().sortedBy { it.night }.map { NightMark.of(it.state) },
        nightCounts =
            mapOf(
                NightMark.KEPT to nightsKept,
                NightMark.SHORT to nightsShort,
                NightMark.MISSED to nightsMissed,
                NightMark.LET_OFF to nightsLetOff,
                NightMark.REPORTED to nightsReported,
            ),
        crewApps =
            crewApps.orEmpty().map {
                RecordApp(it.appName, it.packageName, it.appIcon, left = !it.outcome.equals("finished", ignoreCase = true))
            },
    )
