package com.eazyverse.testtrack.domain

/**
 * How a night's round treats an app the server will not count, apart from the phone.
 *
 * Open all leaves out what it knows will be refused, but a refusal can still come (an update the
 * owner announced a moment ago). That app is skipped and the round finishes the rest; it stays on
 * its row with its own button, and the round's last line says how many were left.
 */
object RoundRules {
    /**
     * A refusal of this one app's open, which the round skips: anything the server says no to on
     * purpose. A lost session, too many tries or the server down stop the round instead.
     */
    fun refusedOpen(
        status: Int,
        auth: Boolean = false,
    ) = status in 400..499 && !auth && status != 401 && status != 429

    /**
     * Whether a round found on disk after Android closed TestTrack carries on to its next app: only
     * one without pictures, or whose camera is somehow still on, and only on the day it started.
     * Otherwise it records what is in hand and ends, rather than opening the next app without its
     * picture, or counting last night's use as tonight's.
     */
    fun resumes(
        pictures: Boolean,
        cameraOn: Boolean,
        startedAt: Long,
        startOfToday: Long,
    ) = (!pictures || cameraOn) && startedAt >= startOfToday

    /** Where a restored round's visit is measured from: never before today began. */
    fun measureFrom(
        since: Long,
        startOfToday: Long,
    ) = maxOf(since, startOfToday)

    /**
     * How soon after Android's screen-share yes the resume that follows it is still TestTrack's own.
     * Android hands over the yes just before TestTrack resumes.
     */
    const val OWN_RESUME_MS = 1_000L

    /** How many apps a round left out. */
    fun skippedLead(count: Int) = if (count == 1) "1 app skipped." else "$count apps skipped."

    /** The round's last line when apps were left out: how many, and why the first was. */
    fun lastWord(skipped: List<String>): String = if (skipped.isEmpty()) "" else "${skippedLead(skipped.size)} ${skipped.first()}"
}

/**
 * TestTrack's own resume after Android's screen-share yes. The yes comes just before that resume,
 * so the first resume within [RoundRules.OWN_RESUME_MS] of it is TestTrack's own and the round goes
 * on. Only that one: a later resume, or one after the window, is the tester back from the app, even
 * a second after it opened (it closed at once, or Back was pressed). A round found on disk, or one
 * started without the yes (a private test), never has a yes in hand.
 */
class ConsentResume {
    private var yesAt = 0L

    /** Android said yes at [at], on the clock since boot. */
    fun yes(at: Long) {
        yesAt = at
    }

    /** Whether the resume at [now] is TestTrack's own. The yes is used up by this, either way. */
    fun swallows(now: Long): Boolean {
        val at = yesAt
        yesAt = 0L
        return at > 0 && now >= at && now - at < RoundRules.OWN_RESUME_MS
    }
}
