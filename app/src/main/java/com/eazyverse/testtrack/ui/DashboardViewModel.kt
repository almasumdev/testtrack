package com.eazyverse.testtrack.ui

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.*
import kotlinx.coroutines.launch

class DashboardViewModel : ViewModel() {
    var app by mutableStateOf<TestApp?>(null)
        private set
    var group by mutableStateOf<TestGroup?>(null)
        private set
    var testers by mutableStateOf<List<Tester>>(emptyList())
        private set
    var proofs by mutableStateOf<Map<Pair<String, Int>, Proof>>(emptyMap())
        private set

    /** Day-one notes testers have left on this app — see [Repo.submitFeedback], newest first. */
    var feedback by mutableStateOf<List<Feedback>>(emptyList())
        private set

    /** Every tester's own [TestApp.placedAt], for [stateOf] to tell "not a member yet" from "missed". */
    var placedAt by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    /**
     * Testers who posted here before leaving the cohort — kicked, or their own app taken out of
     * testing. Kept separate from [testers] rather than appended to it, so a departed member's
     * old days still show but they never count toward "expected" in [Headline] or [behind] — both
     * of those mean "owes a day today," which is exactly what someone no longer here does not.
     */
    var departed by mutableStateOf<List<Tester>>(emptyList())
        private set

    /**
     * The app, its group, the roster and every proof are all in hand.
     *
     * One flag for the page — see [SkeletonPage]. Here the headline is the casualty: a roster
     * without its proofs reads "0 of 13 reported today" and every cell of the grid draws as a
     * miss. Sticky once set.
     */
    var ready by mutableStateOf(false)
        private set

    /** A refresh in flight over content already on screen — see [HomeViewModel.refreshing]. */
    var refreshing by mutableStateOf(false)
        private set

    /** The reader pulled for this themselves — see [HomeViewModel.pulling]. */
    var pulling by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)

    val day: Int? get() = group?.dayIndex()

    fun load(
        appId: String,
        fromPull: Boolean = false,
    ) {
        showCached(appId)

        viewModelScope.launch {
            refreshing = ready // only worth flagging over content that's already on screen
            if (fromPull) pulling = true
            runCatching {
                val found = Repo.app(appId)
                val cohort = found?.groupId?.let { Repo.group(it) }

                // The owner never tests their own app, so they are not a row in their grid.
                val roster =
                    if (found == null || cohort == null) {
                        emptyList()
                    } else {
                        Repo.testers(cohort.memberUids).filter { it.uid != found.ownerUid }
                    }
                val marks =
                    if (found == null || cohort == null) {
                        emptyMap()
                    } else {
                        Repo
                            .proofsForApp(appId, found.ownerUid)
                            .associateBy { it.testerUid to it.day }
                    }
                // Each tester's own arrival, so a day before it reads as "not a member yet"
                // rather than "missed" — see Compliance.firstJudgedDay.
                val arrivals =
                    if (cohort == null) {
                        emptyMap()
                    } else {
                        Repo.appsInGroup(cohort.id).associate { it.ownerUid to it.placedAt }
                    }
                // Anyone who posted a proof here but is not a current member any more — kicked,
                // or their app taken out of testing. Best-effort, the same reasoning [notes]
                // already gives: a name that failed to resolve is not worth losing the grid over.
                val left =
                    if (found == null || cohort == null) {
                        emptyList()
                    } else {
                        runCatching {
                            val goneUids =
                                marks.keys.map { it.first }.toSet() -
                                    cohort.memberUids.toSet() - found.ownerUid
                            Repo.testers(goneUids)
                        }.getOrDefault(emptyList())
                    }
                // Best-effort, the same reasoning [roster] already gives: a note that failed to
                // load is not worth taking the grid down with it.
                val notes =
                    if (found == null) {
                        emptyList()
                    } else {
                        runCatching {
                            Repo.feedbackForApp(appId, found.ownerUid)
                        }.getOrDefault(emptyList())
                    }

                // Published in one go, after every query has answered — see [ready].
                app = found
                group = cohort
                testers = roster
                proofs = marks
                placedAt = arrivals
                departed = left
                feedback = notes

                found?.let { Cache.put(Cache.app(appId), it) }
                if (cohort != null) {
                    Cache.put(Cache.group(cohort.id), cohort)
                    Cache.put(Cache.testers(cohort.id), roster)
                    Cache.put(Cache.proofs(appId), marks)
                    Cache.put(Cache.arrivals(cohort.id), arrivals)
                }
                Cache.put(Cache.departedTesters(appId), left)
                Cache.put(Cache.feedbackFor(appId), notes)
            }.onFailure {
                message =
                    it.friendly(
                        "We couldn't load this dashboard. Check your connection and try again.",
                        denied = "This app isn't yours to see any more. It may have been withdrawn.",
                    )
            }
            ready = true
            refreshing = false
            pulling = false
        }
    }

    /** Complete hit or nothing: proofs missing behind a full roster reads as thirteen no-shows. */
    private fun showCached(appId: String) {
        val cached = Cache.get<TestApp>(Cache.app(appId)) ?: return
        val gid = cached.groupId ?: return
        val cohort = Cache.get<TestGroup>(Cache.group(gid)) ?: return
        val roster = Cache.get<List<Tester>>(Cache.testers(gid)) ?: return
        val marks = Cache.get<Map<Pair<String, Int>, Proof>>(Cache.proofs(appId)) ?: return
        val arrivals = Cache.get<Map<String, Long>>(Cache.arrivals(gid)) ?: return

        app = cached
        group = cohort
        testers = roster
        proofs = marks
        placedAt = arrivals
        Cache.get<List<Tester>>(Cache.departedTesters(appId))?.let { departed = it }
        Cache.get<List<Feedback>>(Cache.feedbackFor(appId))?.let { feedback = it }
        ready = true
    }

    fun stateOf(
        uid: String,
        forDay: Int,
    ): DayState {
        val today = day
        val proof = proofs[uid to forDay]
        // Someone no longer in the cohort has no day it would be fair to call missed — a day they
        // covered still shows above, from [proof] alone, and every other day reads as "not a
        // member" rather than a false accusation. This can't tell "left after genuinely missing
        // two days" from "left the day after joining," which is the right trade: never show a
        // miss that isn't earned.
        val firstJudged =
            if (departed.any { it.uid == uid }) {
                Int.MAX_VALUE
            } else {
                group?.let { Compliance.firstJudgedDay(placedAt[uid] ?: 0L, it.startDate) } ?: 0
            }
        // All or nothing, the same as Enforcement.enforceOn: while the app carries an open
        // report nobody could have tested it, so a day with no proof reads as excused rather
        // than missed for every tester at once, not just the one who filed it.
        val reported = (app?.reportedAt ?: 0L) > 0L
        return when {
            proof != null && proof.meetsBar -> DayState.POSTED
            proof != null -> DayState.SHORT
            forDay < firstJudged -> DayState.NOT_JOINED
            reported && (today == null || forDay < today) -> DayState.REPORTED
            today == null || forDay < today -> DayState.MISSED
            else -> DayState.UPCOMING
        }
    }

    /** Reported today and stayed the full ten seconds. */
    fun reportedToday(): List<Pair<Tester, Proof>> {
        val today = day ?: return emptyList()
        return testers.mapNotNull { tester ->
            proofs[tester.uid to today]?.takeIf { it.meetsBar }?.let { tester to it }
        }
    }

    fun behind(): List<Tester> {
        val today = day ?: return emptyList()
        return testers.filter {
            stateOf(it.uid, today) !in setOf(DayState.POSTED, DayState.NOT_JOINED, DayState.REPORTED)
        }
    }

    /** As wide as this run actually is, which an admin may have extended past [RUN_DAYS]. */
    val runDays: Int get() = group?.runDays ?: RUN_DAYS

    fun daysPosted(uid: String): Int = (0 until runDays).count { stateOf(uid, it) == DayState.POSTED }

    fun totalUsage(uid: String): Long = (0 until runDays).sumOf { proofs[uid to it]?.usageMs ?: 0L }
}
