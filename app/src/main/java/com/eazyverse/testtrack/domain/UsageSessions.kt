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
        helpers: Set<String> = emptySet(),
    ): Long = sessions(events, pkg, since, now, helpers).sumOf { it.last - it.first }

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
        helpers: Set<String> = emptySet(),
    ): Int =
        sessions(events, pkg, since, now, helpers)
            .filter { it.last - it.first >= minMs }
            .map { dayOf(it.first) }
            .distinct()
            .size

    /**
     * When [pkg]'s first sitting of at least [minMs] between [since] and [now] began, or null when
     * there was none: the phone's own record of the first day of use, which a live review's second
     * day is counted from. Taken from the same sittings as [daysUsed], so the two always agree.
     */
    fun firstUse(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
        minMs: Long = 15_000L,
        helpers: Set<String> = emptySet(),
    ): Long? =
        sessions(events, pkg, since, now, helpers)
            .filter { it.last - it.first >= minMs }
            .minOfOrNull { it.first }

    /**
     * Whether [pkg] is in front at [now], itself or through a browser it handed over to: what the
     * night's picture waits for, so it is never a picture of TestTrack or of whatever came next.
     */
    fun inFront(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
        helpers: Set<String> = emptySet(),
    ): Boolean = sessions(events, pkg, since, now, helpers).lastOrNull()?.last == now

    /**
     * Each sitting of [pkg] in front, clipped to the window, as its start and end.
     *
     * [helpers] are the browsers on the phone. Some apps are a web page in a browser's frame (a
     * Custom Tab or a Trusted Web Activity): the app's own screen shows for a moment and hands
     * over to the browser's, which Android logs as the browser in front. Time in a browser counts
     * as the app's only when it takes over straight from the app, within [HANDOVER_MS], and stops
     * when anything else comes to the front. A browser opened any other way counts nothing.
     *
     * A pause does not close the sitting at once: Android logs the leaving screen's pause before
     * the next screen's resume, so the sitting waits for that next resume to say who took over.
     */
    private fun sessions(
        events: List<Event>,
        pkg: String,
        since: Long,
        now: Long,
        helpers: Set<String> = emptySet(),
    ): List<LongRange> {
        if (since >= now) return emptyList()
        val resumed = HashSet<String>()
        var openedAt = 0L
        // When the last screen of the sitting paused; 0 while one is still in front.
        var leftAt = 0L
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
            leftAt = 0L
        }

        for (event in events.sortedBy { it.at }) {
            val ours = event.pkg == pkg
            val helper = !ours && event.pkg in helpers
            val key = "${event.pkg}/${event.activity}"
            when (event.kind) {
                Kind.SCREEN_OFF -> close(if (leftAt > 0L) leftAt else event.at)
                Kind.RESUMED -> {
                    val handedOver = leftAt > 0L && event.at - leftAt <= HANDOVER_MS
                    when {
                        // Back in the app, or a browser taking over from it: the sitting goes on.
                        (ours || helper) && (resumed.isNotEmpty() || handedOver) -> {
                            leftAt = 0L
                            resumed += key
                        }
                        ours -> {
                            close(leftAt)
                            openedAt = event.at
                            resumed += key
                        }
                        // Some other app came to the front: the sitting ended when the app left.
                        leftAt > 0L -> close(leftAt)
                    }
                }
                Kind.PAUSED ->
                    if (resumed.remove(key) && resumed.isEmpty()) leftAt = event.at
            }
        }
        close(if (leftAt > 0L) leftAt else now)
        return found
    }

    /** How soon after the app's last screen pauses a browser's must resume to be the app's. */
    const val HANDOVER_MS = 3_000L
}
