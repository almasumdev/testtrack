package com.eazyverse.testtrack.data

/**
 * How long Play requires testers to stay opted in, and the length every run starts at.
 *
 * A starting point rather than a fixed rule: an admin can extend a cohort past it, so anything
 * reading a run's length must ask the group ([TestGroup.runDays]) instead of this constant. Kept
 * for the default and for the width of a grid nobody has extended.
 */
const val RUN_DAYS = 14

/**
 * When a member has stopped holding up their end, and the arithmetic that decides it.
 *
 * Miss one completed day and you are warned. Miss two in a row and your app leaves the group.
 *
 * The check runs on testers' phones, and needs neither a server nor an admin. Every developer can
 * already read every proof about their own app and nothing else, which happens to be exactly the
 * evidence required: whether a given member opened *their* app on a given day. Thirteen apps in a
 * group means thirteen devices each watching their own, and between them the whole cohort is
 * covered.
 *
 * The database checks the same sums before it accepts a removal, so these are not the only place
 * they are enforced — see the `groups` and `apps` blocks in firestore.rules. Change one and the
 * other has to move with it, or evictions start coming back PERMISSION_DENIED.
 */
object Compliance {

    /** Consecutive completed days missed before a member's app leaves the group. */
    const val MISSES_TO_REMOVE = 2

    private const val DAY_MS = 86_400_000L

    /**
     * The most recent day whose result is final, or null if there isn't one yet.
     *
     * Today is never judged: there are hours left to open the app, so a gap in it is not a miss.
     * Which means nobody can be removed before day three, the first day on which two completed
     * days exist to be missed.
     */
    fun lastCompletedDay(dayIndex: Int): Int? = (dayIndex - 1).takeIf { it >= 0 }

    /**
     * The first day this member can be held to.
     *
     * An app placed into a running group on day nine has no proofs for days one to eight. Read
     * literally that is eight missed days, and the first device to open the app would evict its
     * owner on the spot. So the count starts the day *after* they arrived: being added at 23:00 is
     * not a day's notice either.
     *
     * Founding members were placed before the clock started and are held to all of it.
     */
    fun firstJudgedDay(placedAt: Long, startDate: Long): Int =
        if (placedAt <= 0L || startDate <= 0L || placedAt <= startDate) 0
        else ((placedAt - startDate) / DAY_MS).toInt() + 1

    /**
     * The completed days one member owed one app, most recent first.
     *
     * Both arrivals bound it, and the later of the two wins. A tester cannot be marked down for
     * the days before they joined, and equally cannot be marked down for days before the app
     * itself was placed — there was nothing there to open.
     *
     * Shorter than [count] means there is not yet enough finished run to judge, which is the
     * answer that keeps a new member safe.
     */
    fun judgedDays(
        dayIndex: Int,
        startDate: Long,
        testerPlacedAt: Long,
        appPlacedAt: Long,
        count: Int
    ): List<Int> {
        val last = lastCompletedDay(dayIndex) ?: return emptyList()
        val first = maxOf(
            firstJudgedDay(testerPlacedAt, startDate),
            firstJudgedDay(appPlacedAt, startDate)
        )
        return (0 until count).map { last - it }.filter { it >= first }
    }
}

/**
 * A swap cohort.
 *
 * Fourteen members, one app each. Google requires twelve testers opted in and does not count an
 * app's own developer, so a cohort of twelve leaves every app one short — hence [THRESHOLD] of
 * thirteen before the clock starts, and [CAPACITY] of fourteen for margin.
 *
 * The run belongs here rather than to the app. Every app in a group is on the same day, which is
 * what makes a group readable as a cohort at all.
 */
data class TestGroup(
    val id: String = "",
    val name: String = "",
    val memberUids: List<String> = emptyList(),
    val appIds: List<String> = emptyList(),
    /** Epoch millis. Zero until the group first reached [THRESHOLD]; set once, by an admin. */
    val startDate: Long = 0L,
    /**
     * How many days this run lasts.
     *
     * [RUN_DAYS] unless an admin has extended it. Nothing happens on its own when a run reaches
     * the end: the days simply stop being owed, and testing carries on for anyone who wants to.
     * Extending is the only way obligation continues.
     */
    val runDays: Int = RUN_DAYS,
    val status: String = STATUS_FORMING
) {
    val size: Int get() = memberUids.size
    val running: Boolean get() = startDate > 0L

    /** Short of the threshold. Before the run starts that is normal; during it, it is a problem. */
    val underStrength: Boolean get() = size < THRESHOLD

    /** How many more members before the clock can start. */
    val stillNeeded: Int get() = (THRESHOLD - size).coerceAtLeast(0)

    /**
     * Which day of the run today is, or null before it starts and after it ends.
     *
     * Whole days since [startDate] rather than calendar days: a calendar boundary would let a
     * tester post at 23:59 and again at 00:01 and call it two days.
     */
    fun dayIndex(now: Long = System.currentTimeMillis()): Int? {
        if (startDate <= 0L) return null
        val day = ((now - startDate) / 86_400_000L).toInt()
        return day.takeIf { it in 0 until runDays }
    }

    /**
     * Losing a member mid-run does not rewind the count.
     *
     * Play Console will have reset its own, so the two can disagree — the group screen says so
     * outright rather than letting a grid that reads "day nine" quietly imply otherwise.
     */
    val atRisk: Boolean get() = running && underStrength

    companion object {
        const val CAPACITY = 14
        const val THRESHOLD = 13

        const val STATUS_FORMING = "forming"
        const val STATUS_RUNNING = "running"
        const val STATUS_FINISHED = "finished"
    }
}

/**
 * An app someone has put on a closed testing track.
 *
 * Registered by its owner, placed into a group by an admin. Those are one action: there is no
 * approved-but-unassigned state, because approval *is* the placement.
 */
data class TestApp(
    val id: String = "",
    val ownerUid: String = "",
    val ownerEmail: String = "",
    val name: String = "",
    val packageName: String = "",
    /** Null until an admin places it. */
    val groupId: String? = null,
    val submittedAt: Long = 0L,
    /**
     * Epoch millis of the placement that put this app in its current group. Zero if it has never
     * been placed, or was placed before the console started recording it.
     *
     * [Compliance] will not act against an app without one. Membership of a running group says
     * nothing about *when* someone joined, and a member placed on day nine has no proofs for the
     * eight days before they arrived — which is indistinguishable from having skipped them.
     */
    val placedAt: Long = 0L,
    val status: String = STATUS_PENDING
) {
    val placed: Boolean get() = status == STATUS_ASSIGNED && !groupId.isNullOrBlank()

    val label: String get() = name.ifBlank { packageName }

    /**
     * Where a tester joins this app's closed test.
     *
     * Derived rather than stored: Play gives every track this address, and it is the package name
     * with a fixed prefix. Nothing has to be supplied at submission and no existing app document
     * needs a new field.
     *
     * The opt-in page, deliberately, not the store listing. Opting in is the step Play's fourteen
     * days are actually counted from, and it is the step people skip — and until someone has taken
     * it the listing shows them nothing at all, so sending them there first is sending them to a
     * dead end.
     */
    val optInUrl: String get() = "https://play.google.com/apps/testing/$packageName"

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ASSIGNED = "assigned"
    }
}

/**
 * One tester's proof for one app on one day of one run.
 *
 * The document id carries the run as well as the day. Without it a restart was cosmetic: day
 * numbering begins again at zero, the previous run's proofs are still sitting under those numbers,
 * and a cohort restarted for having bled out would open on day one already showing everybody
 * present. The run is identified by the `startDate` it began on, which changes every time the
 * clock is set — so the old fortnight keeps its record and stops counting towards the new one.
 */
data class Proof(
    val appId: String = "",
    val groupId: String = "",
    /**
     * The owner of [appId], copied in at write time.
     *
     * Carried on the proof so reading one needs no lookup: the two people entitled to see it — the
     * tester who posted it and the owner of the app it is about — are both named in the document.
     * A rule that had to fetch the group instead did not survive a 45-document query.
     */
    val ownerUid: String = "",
    val testerUid: String = "",
    val testerEmail: String = "",
    val day: Int = 0,
    val fileId: String = "",
    val imageUrl: String = "",
    val capturedAt: Long = 0L,
    /** Foreground time for that app that day, from UsageStatsManager. */
    val usageMs: Long = 0L,
    /**
     * The `startDate` of the run this belongs to.
     *
     * Part of the identity, not decoration: every query for a day has to name it, or a restarted
     * cohort reads its predecessor's attendance as its own.
     */
    val runStartedAt: Long = 0L
) {
    /** A screenshot proves the app opened. The half-minute of use is what proves it was used. */
    val meetsBar: Boolean get() = usageMs >= MIN_USAGE_MS

    companion object {
        /** The tester is asked to stay for thirty seconds, so that is what a day has to show. */
        const val MIN_USAGE_MS = 30_000L

        /**
         * Same tester, same app, same day of the same run always writes the same document, so
         * posting twice overwrites rather than duplicating.
         */
        fun id(appId: String, testerUid: String, day: Int, runStartedAt: Long) =
            "${appId}__${testerUid}__${runStartedAt}__$day"
    }
}

/** A member of the group, as far as a grid is concerned. */
data class Tester(
    val uid: String = "",
    val email: String = "",
    val displayName: String = ""
) {
    /** `someone@gmail.com` -> `someone`, which is all a grid column has room for. */
    val shortName: String
        get() = displayName.takeIf { it.isNotBlank() } ?: email.substringBefore('@')

    val initial: String get() = shortName.firstOrNull()?.uppercase() ?: "?"
}

/** Milliseconds as `4m 12s` / `45s`, for a stat line. */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds < 60) "${seconds}s" else "${seconds / 60}m ${seconds % 60}s"
}
