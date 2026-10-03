package com.eazyverse.testtrack.data

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings

/**
 * How long an app was actually on screen.
 *
 * A screenshot proves the app opened. It cannot prove anyone stayed, and Play is not satisfied by
 * a launch either — so the daily report carries real foreground time.
 *
 * This is the one thing TestTrack asks for that costs the tester a trip to Settings.
 * `PACKAGE_USAGE_STATS` cannot be granted by a runtime prompt: it is an appop, toggled by hand
 * under Special access. There is no cheaper source — anything measured inside TestTrack only sees
 * the session TestTrack itself started, which is exactly the number a tester could game by
 * waiting.
 */
object UsageRepo {
    /**
     * True once the tester has switched TestTrack on under Settings → Special access.
     *
     * Two spellings of one call. `unsafeCheckOpNoThrow` only exists from API 29, and this runs on
     * 26 — reaching for it unguarded is a `NoSuchMethodError` on Android 8 and 9, on the first
     * screen that asks whether access was granted. `checkOpNoThrow` is the same query under the
     * older name and goes back to 19.
     */
    fun hasAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val op = AppOpsManager.OPSTR_GET_USAGE_STATS
        val uid = Process.myUid()
        val pkg = context.packageName

        @Suppress("DEPRECATION")
        val mode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ops.unsafeCheckOpNoThrow(op, uid, pkg)
            } else {
                ops.checkOpNoThrow(op, uid, pkg)
            }

        // MODE_DEFAULT means the appop defers to the permission itself, and several makers ship
        // exactly that state after the switch is turned on. Reading it as "no" told people with
        // access that they had none and counted nothing, on every visit, forever.
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }

    /**
     * Opens the usage-access list.
     *
     * The per-app deep link is unreliable across manufacturers, so fall back to the plain list —
     * a wrong-but-open Settings page beats an ActivityNotFoundException.
     */
    fun openSettings(context: Context) {
        val direct =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        val fallback =
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
    }

    /**
     * Foreground milliseconds for [pkg] between [since] and now.
     *
     * The window is the visit, handed in by the caller: one number, one meaning, no day boundary.
     * Summed from raw events rather than `queryUsageStats`, whose daily buckets are written late
     * and report a visit that ended seconds ago as zero.
     *
     * How the events are read is what the earlier version got wrong on some phones:
     *
     *  - **Per activity, not per app.** An app moving from its splash to its main screen can log
     *    "main resumed" before "splash paused". Treating them as one switch closed the session at
     *    the splash's pause and dropped everything the main screen was open for. The app is in
     *    front while any of its activities is resumed.
     *  - **A look back before the window.** An app already on screen when the window opens has
     *    its "resumed" before [since]. Reading from [since] only saw a lone "paused" and dropped
     *    that session. Events are read from [LOOK_BACK] earlier and every session is clipped to
     *    the window, so time before [since] still never counts.
     *  - **The screen going off ends it.** Some phones keep an activity "resumed" with the
     *    screen off; a phone left on a table was counted as a visit.
     */
    fun foregroundMsSince(
        context: Context,
        pkg: String,
        since: Long,
    ): Long {
        if (!hasAccess(context)) return 0L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        if (since <= 0L || since >= now) return 0L

        return runCatching { sumSessions(manager, pkg, since, now) }.getOrDefault(0L)
    }

    @Suppress("DEPRECATION")
    private fun sumSessions(
        manager: UsageStatsManager,
        pkg: String,
        since: Long,
        now: Long,
    ): Long {
        val events = manager.queryEvents(since - LOOK_BACK, now)
        val event = UsageEvents.Event()
        val resumed = HashSet<String>()
        var openedAt = 0L
        var total = 0L

        fun close(at: Long) {
            if (openedAt > 0L) {
                val start = maxOf(openedAt, since)
                if (at > start) total += at - start
            }
            openedAt = 0L
            resumed.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                // The screen went off or the phone locked: whatever is "in front" is not being used.
                SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> close(event.timeStamp)
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    if (event.packageName == pkg) {
                        if (resumed.isEmpty()) openedAt = event.timeStamp
                        resumed.add(event.className ?: "")
                    }
                UsageEvents.Event.MOVE_TO_BACKGROUND, ACTIVITY_STOPPED ->
                    if (event.packageName == pkg) {
                        resumed.remove(event.className ?: "")
                        if (resumed.isEmpty()) close(event.timeStamp)
                    }
            }
        }

        // Still open: the normal case, read while the app under test is on screen.
        if (resumed.isNotEmpty()) close(now)
        return total
    }

    /** How far before the window events are read, to catch a session already under way. */
    private const val LOOK_BACK = 6 * 60 * 60 * 1000L

    // Event types added after our minSdk; the values are fixed in the platform.
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17
    private const val ACTIVITY_STOPPED = 23
    private const val DEVICE_SHUTDOWN = 26
}
