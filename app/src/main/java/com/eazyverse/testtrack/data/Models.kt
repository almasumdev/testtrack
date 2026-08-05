package com.eazyverse.testtrack.data

/** How long Play requires testers to stay opted in. Every grid is this many columns wide. */
const val RUN_DAYS = 14

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
        return day.takeIf { it in 0 until RUN_DAYS }
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
    val status: String = STATUS_PENDING
) {
    val placed: Boolean get() = status == STATUS_ASSIGNED && !groupId.isNullOrBlank()

    val label: String get() = name.ifBlank { packageName }

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ASSIGNED = "assigned"
    }
}

/**
 * One tester's proof for one app on one day.
 *
 * The document id is `{appId}__{testerUid}__{day}`, which makes posting twice an overwrite rather
 * than a duplicate and lets a whole grid come back in a single query.
 */
data class Proof(
    val appId: String = "",
    val groupId: String = "",
    val testerUid: String = "",
    val testerEmail: String = "",
    val day: Int = 0,
    val fileId: String = "",
    val imageUrl: String = "",
    val capturedAt: Long = 0L,
    /** Foreground time for that app that day, from UsageStatsManager. */
    val usageMs: Long = 0L
) {
    /** A screenshot proves the app opened. The half-minute of use is what proves it was used. */
    val meetsBar: Boolean get() = usageMs >= MIN_USAGE_MS

    companion object {
        /** The tester is asked to stay for thirty seconds, so that is what a day has to show. */
        const val MIN_USAGE_MS = 30_000L

        fun id(appId: String, testerUid: String, day: Int) = "${appId}__${testerUid}__$day"
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
