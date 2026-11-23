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
     * How soon after the round opens an app a resume of TestTrack is still TestTrack's own. Android
     * hands over the screen-share yes just before TestTrack resumes, so the round starts first and
     * that resume follows it; nobody comes back from an app this quickly.
     */
    const val OWN_RESUME_MS = 1_000L

    /**
     * Whether a resume at [now] is TestTrack's own, a moment after the app on screen was asked to
     * open at [beganAt] (both on the clock since boot), rather than a way back from that app. A
     * round found on disk has no start in hand ([beganAt] 0), so its resume always counts.
     */
    fun ownResume(
        beganAt: Long,
        now: Long,
    ) = beganAt > 0 && now >= beganAt && now - beganAt < OWN_RESUME_MS

    /** The round's last line: tonight done, or how many were left out and why the first was. */
    fun lastWord(skipped: List<String>): String =
        if (skipped.isEmpty()) "That is tonight done." else "${skipped.size} left. ${skipped.first()}"
}
