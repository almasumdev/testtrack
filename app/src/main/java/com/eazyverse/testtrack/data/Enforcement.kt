package com.eazyverse.testtrack.data

import android.content.Context
import android.util.Log

/**
 * The run's rules, enforced by the phones in the run.
 *
 * Miss one completed day and you are warned. Miss [Compliance.MISSES_TO_REMOVE] in a row and your
 * app leaves the group.
 *
 * There is no server here and no admin in the loop, and that is the point rather than a
 * compromise. Two facts make it work:
 *
 *  - **Everyone can see their own app's attendance.** The proof rules already let a developer read
 *    every proof about the app they own, and nothing else. That is precisely the evidence needed
 *    to say whether a member opened *their* app on a given day, and it is the only evidence this
 *    needs. Thirteen apps in a cohort means thirteen devices each watching their own; between them
 *    nobody goes unwatched.
 *  - **The database checks the sums itself.** Every removal is re-derived from the proof documents
 *    by the security rules before either half of it is accepted. A patched client that skipped
 *    everything below would be refused, so the safety does not rest on this file being correct.
 *
 * What that costs is immediacy. Nothing happens while every phone is shut, and a cohort where
 * nobody opens the app enforces nothing on anybody — which is the harmless direction for the
 * failure to go, since the run is already over in that case.
 */
object Enforcement {

    /**
     * Where this tester stands in one cohort, when it is somewhere worth telling them about.
     *
     * Only ever raised for the last completed day. There is nothing to warn about while a day is
     * still running: the apps are still openable, so the tester has not missed anything yet.
     */
    data class Standing(
        val groupId: String,
        val groupName: String,
        val missedDay: Int,
        /** Labels of the apps left unopened. Named, because "you missed three" invites a guess. */
        val missed: List<String>,
        /** They also missed the day before. One more and the group loses them. */
        val finalWarning: Boolean
    ) {
        /** The apps, named. "You missed three" only invites the question of which three. */
        val names: String get() = missed.joinToString(", ")

        /** Whether to say "open it" or "open them", which is nearly always one app. */
        val it: String get() = if (missed.size == 1) "it" else "them"
    }

    /**
     * An app that was in a cohort last time this device looked and is not any more.
     *
     * Derived by comparing against a local snapshot rather than read from anywhere. Removal is a
     * write that happens on somebody else's phone, and there is nothing to send a message with —
     * so each device works out for itself what changed while it was away.
     */
    data class Departed(val groupName: String, val label: String)

    data class Sweep(
        val standings: List<Standing> = emptyList(),
        val departed: List<Departed> = emptyList(),
        /** This tester's own app was removed from a cohort. Their name for the group. */
        val evictedFrom: List<String> = emptyList()
    )

    private const val TAG = "Enforcement"
    private const val PREFS = "testtrack.enforcement"
    private const val KEY_SEEN = "seen"

    /**
     * Runs the whole check for one account and acts on it.
     *
     * Safe to call often, and called from both the home screen and the daily worker. Removals are
     * idempotent by construction: the transaction behind [Repo.evict] is a no-op once the member
     * is already gone, so thirteen devices reaching the same verdict within a minute of each other
     * costs one write and twelve reads.
     *
     * Never throws. This runs alongside a screen refresh that has its own error handling, and an
     * enforcement pass that could take the home screen down with it would be a poor trade for a
     * feature nobody is waiting on.
     */
    suspend fun sweep(context: Context, uid: String): Sweep = runCatching {
        val groups = Repo.myGroups(uid)
        val standings = mutableListOf<Standing>()
        val departed = mutableListOf<Departed>()

        val seen = lastSeen(context)
        val now = mutableMapOf<String, SeenGroup>()

        for (group in groups) {
            val apps = Repo.appsInGroup(group.id)
            now[group.id] = SeenGroup(group.name, apps.associate { it.id to it.label })

            // Anything this device knew about and can no longer find has been taken out from under
            // it, by an admin or by another member's sweep.
            seen[group.id]?.apps.orEmpty().forEach { (id, label) ->
                if (apps.none { it.id == id }) departed += Departed(group.name, label)
            }

            val day = group.dayIndex() ?: continue
            val mine = apps.firstOrNull { it.ownerUid == uid }
            val others = apps.filter { it.ownerUid != uid }

            standingFor(uid, group, day, others, mine)?.let { standings += it }
            if (mine != null) enforceOn(uid, group, day, mine, apps)
        }

        // A cohort that has vanished from under this account is one it is no longer a member of,
        // which is what being removed looks like from the inside. Nobody sends word of it: the
        // write happened on another member's phone, so this is the only way to find out.
        val evictedFrom = seen.keys
            .filter { id -> groups.none { it.id == id } }
            .mapNotNull { seen[it]?.name }

        saveSeen(context, now)
        Log.i(
            TAG,
            "swept ${groups.size} group(s): ${standings.size} standing(s), " +
                "${departed.size} departed, ${evictedFrom.size} eviction(s) of this account"
        )
        Sweep(standings, departed, evictedFrom)
    }.getOrElse {
        // Swallowed on purpose — this runs beside a screen refresh that has its own error
        // handling — but not silently. A sweep that stops finding anything is indistinguishable
        // from a cohort with nothing to find, and that is the one failure nobody would notice.
        Log.w(TAG, "sweep failed", it)
        Sweep()
    }

    /**
     * What to tell this tester about their own last completed day.
     *
     * Counted the same way a peer would count it, deliberately: anything else and the app warns
     * about a day nobody would have removed them for, or stays quiet about one they will be.
     */
    private suspend fun standingFor(
        uid: String,
        group: TestGroup,
        day: Int,
        others: List<TestApp>,
        mine: TestApp?
    ): Standing? {
        if (others.isEmpty()) return null

        val days = Compliance.judgedDays(
            dayIndex = day,
            startDate = group.startDate,
            testerPlacedAt = mine?.placedAt ?: 0L,
            appPlacedAt = 0L,
            count = Compliance.MISSES_TO_REMOVE
        )
        val last = days.firstOrNull() ?: return null

        val cleared = Repo.myProofsForDay(uid, group.id, last, requireBar = false)
        val missed = others.filter { it.id !in cleared }
        if (missed.isEmpty()) return null

        // A second consecutive gap is not a warning any more, it is the last one they get.
        val previous = days.getOrNull(1)
        val alsoMissedBefore = previous != null &&
            Repo.myProofsForDay(uid, group.id, previous, requireBar = false)
                .let { before -> missed.any { it.id !in before } }

        return Standing(
            groupId = group.id,
            groupName = group.name,
            missedDay = last,
            missed = missed.map { it.label },
            finalWarning = alsoMissedBefore
        )
    }

    /**
     * Removes anyone who has stopped opening this device owner's app.
     *
     * Judged only against [mine], because that is the only app whose proofs this account is
     * entitled to read. Every other member is running the same pass over theirs.
     */
    private suspend fun enforceOn(
        uid: String,
        group: TestGroup,
        day: Int,
        mine: TestApp,
        apps: List<TestApp>
    ) {
        // Without a placement date there is no way to tell a member who joined late from one who
        // has skipped every day since the start, and the rules will refuse the write anyway.
        if (mine.placedAt <= 0L) return

        val attended = Repo.proofsForApp(mine.id, uid)
            .map { it.testerUid to it.day }
            .toSet()

        for (app in apps) {
            if (app.ownerUid == uid || app.placedAt <= 0L) continue

            val days = Compliance.judgedDays(
                dayIndex = day,
                startDate = group.startDate,
                testerPlacedAt = app.placedAt,
                appPlacedAt = mine.placedAt,
                count = Compliance.MISSES_TO_REMOVE
            )
            Log.d(TAG, "judging ${app.ownerUid} on day $day: owes $days, attended $attended")
            // Too little finished run to hold them to. Not the same as having attended.
            if (days.size < Compliance.MISSES_TO_REMOVE) continue
            if (days.any { (app.ownerUid to it) in attended }) continue

            // One member's removal failing must not stop the rest of the pass, so this is caught
            // here rather than left to the caller. Logged for the same reason as above: a refusal
            // means this device and the rules disagree about the sums, and that is worth seeing.
            runCatching {
                Repo.evict(
                    groupId = group.id,
                    targetUid = app.ownerUid,
                    targetAppId = app.id,
                    byUid = uid,
                    byAppId = mine.id,
                    day = day
                )
                Log.i(TAG, "evicted ${app.ownerUid} from ${group.id} on day $day")
            }.onFailure { Log.w(TAG, "could not evict ${app.ownerUid} from ${group.id}", it) }
        }
    }

    // ---- what this device last saw --------------------------------------------------------

    private data class SeenGroup(val name: String, val apps: Map<String, String>)

    /**
     * What each cohort held, as of the last sweep.
     *
     * Stored as `groupId|groupName|appId|label` lines. A hand-rolled format for a few dozen short
     * strings beats taking a JSON dependency for it, and nothing outside this file reads it. The
     * group name is carried along because it is needed exactly when the group itself is gone and
     * there is nowhere left to look it up.
     *
     * Persisted, unlike [Cache]. The whole value of it is surviving the gap between two sessions,
     * which is where a removal happens.
     */
    private fun lastSeen(context: Context): Map<String, SeenGroup> =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_SEEN, emptySet())
            .orEmpty()
            .mapNotNull { line ->
                val parts = line.split('|', limit = 4)
                if (parts.size == 4) parts[0] to Triple(parts[1], parts[2], parts[3]) else null
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, rows) ->
                SeenGroup(
                    name = rows.first().first,
                    apps = rows.associate { it.second to it.third }
                )
            }

    private fun saveSeen(context: Context, state: Map<String, SeenGroup>) {
        val lines = state.flatMap { (groupId, group) ->
            group.apps.map { (id, label) -> "$groupId|${group.name}|$id|$label" }
        }.toSet()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_SEEN, lines).apply()
    }

    /** Signing out must not leave the next account inheriting this one's view of the cohort. */
    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
    }
}
