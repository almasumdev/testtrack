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
    private const val KEY_NOTICES = "notices"
    private const val KEY_EXPLAINED = "explained"
    private const val KEY_DISMISSED = "dismissed_standings"

    /**
     * Field separator for the stored lines.
     *
     * The ASCII unit separator, written as a code rather than typed, because a control
     * character sitting in a source file is invisible to review and survives a copy-paste
     * only by luck. No group name or app label can contain it.
     */
    private val SEP = Char(31)

    /**
     * News from a sweep, kept until the tester has actually seen it.
     *
     * A notification is not enough on its own. It can be swiped away on the lock screen, silenced
     * by a Do Not Disturb the tester forgot was on, or never shown at all if they declined the
     * permission — and "your app was removed from its group" is not something to deliver only on a
     * channel that any of those quietly disposes of. So the same news is also parked here and
     * shown on the home screen until it is dismissed.
     */
    data class Notice(val id: String, val title: String, val body: String)

    /** Everything raised but not yet acknowledged, oldest first. */
    fun notices(context: Context): List<Notice> =
        prefs(context).getStringSet(KEY_NOTICES, emptySet()).orEmpty()
            .mapNotNull { line ->
                val parts = line.split(SEP, limit = 3)
                if (parts.size == 3) Notice(parts[0], parts[1], parts[2]) else null
            }
            .sortedBy { it.id }

    fun dismiss(context: Context, id: String) {
        val kept = prefs(context).getStringSet(KEY_NOTICES, emptySet()).orEmpty()
            .filterNot { it.startsWith("$id$SEP") }
        prefs(context).edit().putStringSet(KEY_NOTICES, kept.toSet()).apply()
    }

    private fun standingId(groupId: String, missedDay: Int) = "$groupId|$missedDay"

    /**
     * Closed by the tester, and gone until there is something new to say.
     *
     * Keyed on the group and the missed day it's about, not just the group: a warning for day 3
     * closed today must not swallow day 4's, whether that one turns out milder or the final one.
     */
    fun dismissStanding(context: Context, standing: Standing) {
        val id = standingId(standing.groupId, standing.missedDay)
        val kept = prefs(context).getStringSet(KEY_DISMISSED, emptySet()).orEmpty() + id
        prefs(context).edit().putStringSet(KEY_DISMISSED, kept).apply()
    }

    /**
     * Records news for the home screen.
     *
     * Keyed so the same event landing twice is one entry, not two: the worker and the home screen
     * both sweep, and a tester who opens the app during the evening pass should not be told the
     * same thing twice over.
     */
    private fun remember(context: Context, notices: List<Notice>) {
        if (notices.isEmpty()) return
        val existing = prefs(context).getStringSet(KEY_NOTICES, emptySet()).orEmpty()
        val byId = existing.associateBy { it.substringBefore(SEP) }.toMutableMap()
        notices.forEach { byId[it.id] = "${it.id}$SEP${it.title}$SEP${it.body}" }
        prefs(context).edit().putStringSet(KEY_NOTICES, byId.values.toSet()).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Notes that an event has already told this tester why a cohort disappeared.
     *
     * Written by [AdminEvents.drain], read by the next sweep. Both run in the same pass and the
     * drain goes first, so the marker is always there before the sweep looks — see [ReminderWorker]
     * and the home screen, which both keep that order deliberately.
     */
    fun markExplained(context: Context, groupId: String) {
        val held = prefs(context).getStringSet(KEY_EXPLAINED, emptySet()).orEmpty()
        prefs(context).edit().putStringSet(KEY_EXPLAINED, held + groupId).apply()
    }

    private fun explainedGroups(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_EXPLAINED, emptySet()).orEmpty()

    /** Cleared once used. A marker outliving its sweep would silence a later, unrelated removal. */
    private fun clearExplained(context: Context) {
        prefs(context).edit().remove(KEY_EXPLAINED).apply()
    }

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
            val others = apps.filter { it.ownerUid != uid && it.active }

            standingFor(uid, group, day, others, mine)?.let { standings += it }

            // Nothing is enforced against an app that is already out of testing.
            //
            // Not leniency, arithmetic. Everyone was told to uninstall it, so nobody has been
            // reporting on it, so every day since counts as missed and the very next sweep would
            // remove it again for the absence its own removal caused. The days are forgiven
            // because they were never asked for; the grid still shows them empty, which is the
            // truth and is the admin's cue that something needs sorting out.
            if (mine != null && !mine.removed) enforceOn(uid, group, day, mine, apps)
        }

        // A cohort that has vanished from under this account is one it is no longer a member of,
        // which is what being removed looks like from the inside.
        //
        // Skipping any that an event has already accounted for. A removal writes one, and it is
        // the better of the two messages because it knows the cause — this path cannot tell a
        // removal from an admin dissolving the group, and saying both would be saying it twice.
        val explained = explainedGroups(context)
        val evictedFrom = seen.keys
            .filter { id -> groups.none { it.id == id } && id !in explained }
            .mapNotNull { seen[it]?.name }
        clearExplained(context)

        saveSeen(context, now)

        // Parked for the home screen as well as raised as notifications. Both kinds here are
        // things that happened to the tester rather than reminders for them, and neither should
        // depend on a notification having been seen.
        remember(
            context,
            evictedFrom.map {
                Notice(
                    id = "evicted:$it",
                    title = "Your app is out of $it",
                    // Both causes named, because this device cannot tell them apart. All it knows
                    // is that a cohort it was in has gone; whether that was two missed days, an
                    // admin unplacing the app, or the whole group being dissolved happened
                    // somewhere else. Asserting the first would be a guess presented as a fact.
                    body = "It's back in the queue. That happens after two days without opening " +
                        "the other apps, and it also happens when an admin takes an app out or " +
                        "dissolves a group. Submit it again when you're ready."
                )
            } + departed.map { (group, label) ->
                Notice(
                    id = "departed:$group:$label",
                    title = "Stop testing $label",
                    body = "It has left ${group.ifBlank { "your group" }}. Uninstall it and " +
                        "leave its test, and don't count it towards your day any more."
                )
            }
        )

        val dismissed = prefs(context).getStringSet(KEY_DISMISSED, emptySet()).orEmpty()
        val visible = standings.filterNot { standingId(it.groupId, it.missedDay) in dismissed }

        Log.i(
            TAG,
            "swept ${groups.size} group(s): ${standings.size} standing(s), " +
                "${departed.size} departed, ${evictedFrom.size} eviction(s) of this account"
        )
        Sweep(visible, departed, evictedFrom)
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
            count = Compliance.MISSES_TO_REMOVE,
            reinstatedAt = mine?.reinstatedAt ?: 0L
        )
        val last = days.firstOrNull() ?: return null

        val cleared = Repo.myProofsForDay(uid, group.id, last, group.startDate, requireBar = false)
        // A reported app is excused outright rather than day by day here: this warning is about
        // the shared pair of days everybody in [others] is judged against at once, and a day
        // nobody could get into has no business inside that pair for any of them. An app this
        // caller has personally skipped is excused the same way — their own choice, not a fact
        // about the app, so it only ever removes it from their own [missed], nobody else's.
        val missed = others.filter { it.id !in cleared && it.reportedAt <= 0L }
            .filterNot { Repo.hasSkipped(it.id, uid) }
        if (missed.isEmpty()) return null

        // A second consecutive gap is not a warning any more, it is the last one they get.
        val previous = days.getOrNull(1)
        val alsoMissedBefore = previous != null &&
            Repo.myProofsForDay(uid, group.id, previous, group.startDate, requireBar = false)
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
     * Flags anyone who has stopped opening this device owner's app, for an admin to see.
     *
     * Judged only against [mine], because that is the only app whose proofs this account is
     * entitled to read. Every other member is running the same pass over theirs.
     *
     * This used to remove the app from testing on the spot. It doesn't any more: with thirteen
     * devices each reaching the same verdict independently, an admin who put somebody back in
     * testing had it undone by whichever phone next happened to sweep, sometimes within the hour
     * and never by anyone who knew a decision had just been made. So a device raises this once
     * and stops — [TestApp.flaggedAt] is set-once by the rules, which is what keeps a dozen
     * devices from raising a dozen pushes about the same two days, and only an admin's own hand
     * on [TestApp.removed] actually takes anyone out of testing.
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

        // A reported app judges nobody until an admin clears the report. All or nothing, not day
        // by day: the point is that a day nobody could get into stops counting the moment it is
        // known about, which a report filed today has to cover the very check running today.
        if (mine.reportedAt > 0L) return

        val attended = Repo.proofsForApp(mine.id, uid)
            .map { it.testerUid to it.day }
            .toSet()

        for (app in apps) {
            if (app.ownerUid == uid || app.placedAt <= 0L) continue

            // Already raised. Checked here rather than left to the rules refusing a second write,
            // so a member who stays absent does not cost every device in the cohort a failed
            // write attempt on every sweep until an admin gets to it.
            if (app.flaggedAt > 0L) continue

            // This member's own choice, made because [mine] is skippable — not evidence of
            // truancy, so it never reaches the flag check below at all.
            if (Repo.hasSkipped(mine.id, app.ownerUid)) continue

            val days = Compliance.judgedDays(
                dayIndex = day,
                startDate = group.startDate,
                testerPlacedAt = app.placedAt,
                appPlacedAt = mine.placedAt,
                count = Compliance.MISSES_TO_REMOVE,
                graceDays = group.graceDays,
                reinstatedAt = app.reinstatedAt
            )
            // Only the days in question. Logging the whole attendance set put several thousand
            // characters and every member's uid into logcat, once per member per sweep.
            val covered = days.filter { (app.ownerUid to it) in attended }
            Log.d(TAG, "judging ${app.ownerUid} on day $day: owes $days, covered $covered")
            // Too little finished run to hold them to. Not the same as having attended.
            if (days.size < Compliance.MISSES_TO_REMOVE) continue
            if (days.any { (app.ownerUid to it) in attended }) continue

            // One member's flag failing must not stop the rest of the pass, so this is caught
            // here rather than left to the caller. Logged for the same reason as above: a refusal
            // means this device and the rules disagree about the sums, and that is worth seeing.
            runCatching {
                Repo.flagAtRisk(targetAppId = app.id, byAppId = mine.id)
                Log.i(TAG, "flagged ${app.ownerUid} in ${group.id} on day $day")
                runCatching { Notify.flagged(app.label, app.packageName) }
            }.onFailure { Log.w(TAG, "could not flag ${app.ownerUid} in ${group.id}", it) }
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
