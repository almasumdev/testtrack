package com.eazyverse.testtrack.domain

/**
 * How long an app was in front, worked out from the phone's usage events.
 *
 * Pure on purpose. Every way this has gone wrong on somebody's phone is an ordering of events, and
 * an ordering can be written down in a test and run in milliseconds, where the phone that produced
 * it cannot be. The Android side only translates its events into these and hands them over.
 */
object UsageSessions {
    enum class Kind {
        /** An activity of some app came to the front. */
        RESUMED,

        /** An activity went behind something, or stopped. */
        PAUSED,

        /** The screen went off, the phone locked or shut down: nothing is being used. */
        SCREEN_OFF,
    }

    data class Event(
        val kind: Kind,
        val pkg: String,
        val activity: String,
        val at: Long,
    )

    /**
     * Milliseconds [pkg] was in front between [since] and [now].
     *
     * The app is in front while any of its activities is resumed, not from one "resumed" to the
     * next "paused": a splash screen handing over to the main screen can log the main screen's
     * resume before its own pause, and pairing them blindly closed the visit at the splash's pause
     * and lost everything after it. Events from before [since] are expected (the caller reads a
     * little earlier so a visit already under way is seen opening); each session is clipped to the
     * window, so time before [since] never counts. A screen going off ends whatever was open. A
     * session still open at the end runs to [now], which is the normal case when the reading is
     * taken mid-visit.
     */
    fun foregroundMs(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
    ): Long = sessions(events, pkg, since, now).sumOf { it.last - it.first }

    /**
     * How many calendar days [pkg] was really used on between [since] and [now]: the days a sitting
     * of at least [minMs] started on, by [dayOf] (the phone's own calendar, handed in so this stays
     * free of time zones). What a live review's second day of use is counted from.
     */
    fun daysUsed(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
        dayOf: (Long) -> Long,
        minMs: Long = 15_000L,
    ): Int =
        sessions(events, pkg, since, now)
            .filter { it.last - it.first >= minMs }
            .map { dayOf(it.first) }
            .distinct()
            .size

    /** Each sitting of [pkg] in front, clipped to the window, as its start and end. */
    private fun sessions(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
    ): List<LongRange> {
        if (since >= now) return emptyList()
        val resumed = HashSet<String>()
        var openedAt = 0L
        val found = ArrayList<LongRange>()

        // Keyed on [openedAt], not on [resumed]: the last pause empties [resumed] just before
        // this is called, and checking that instead counted every finished visit as nothing.
        fun close(at: Long) {
            if (openedAt > 0L) {
                val start = maxOf(openedAt, since)
                val end = minOf(at, now)
                if (end > start) found += start..end
            }
            resumed.clear()
            openedAt = 0L
        }

        for (event in events.sortedBy { it.at }) {
            when (event.kind) {
                Kind.SCREEN_OFF -> close(event.at)
                Kind.RESUMED ->
                    if (event.pkg == pkg) {
                        if (resumed.isEmpty()) openedAt = event.at
                        resumed += event.activity
                    }
                Kind.PAUSED ->
                    if (event.pkg == pkg && resumed.remove(event.activity) && resumed.isEmpty()) {
                        close(event.at)
                    }
            }
        }
        close(now)
        return found
    }
}
