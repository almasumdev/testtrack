package com.eazyverse.testtrack.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.util.Calendar

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
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ops.unsafeCheckOpNoThrow(op, uid, pkg)
        else
            ops.checkOpNoThrow(op, uid, pkg)

        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Opens the usage-access list.
     *
     * The per-app deep link is unreliable across manufacturers, so fall back to the plain list —
     * a wrong-but-open Settings page beats an ActivityNotFoundException.
     */
    fun openSettings(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val fallback = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching { context.startActivity(direct) }
            .recoverCatching { context.startActivity(fallback) }
    }

    /**
     * Foreground milliseconds for [pkg] since midnight, across every session however it was
     * opened — not only the one TestTrack launched.
     *
     * Summed from raw events rather than read off `queryUsageStats`. The daily buckets that method
     * returns are written periodically, so a visit that ended seconds ago is still reported as
     * zero — which is exactly when we ask. Events are exact and include the session in progress,
     * which matters because the reading is taken while the app under test is still on screen.
     *
     * The aggregate is still consulted and the larger of the two wins: some manufacturers trim the
     * event stream, and under-reporting a tester's day is worse than an approximate figure.
     */
    fun foregroundMsToday(context: Context, pkg: String): Long {
        if (!hasAccess(context)) return 0L
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val start = startOfToday()
        val now = System.currentTimeMillis()

        val fromEvents = runCatching { sumSessions(manager, pkg, start, now) }.getOrDefault(0L)
        val fromBuckets = runCatching {
            manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, now)
                .orEmpty()
                .filter { it.packageName == pkg }
                .sumOf { it.totalTimeInForeground }
        }.getOrDefault(0L)

        return maxOf(fromEvents, fromBuckets)
    }

    @Suppress("DEPRECATION")
    private fun sumSessions(
        manager: UsageStatsManager,
        pkg: String,
        start: Long,
        now: Long
    ): Long {
        val events = manager.queryEvents(start, now)
        val event = UsageEvents.Event()
        var total = 0L
        var openedAt = 0L

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName != pkg) continue
            when (event.eventType) {
                // ACTIVITY_RESUMED / ACTIVITY_PAUSED under their pre-29 names, which carry the
                // same values and are the ones available at our minSdk.
                UsageEvents.Event.MOVE_TO_FOREGROUND -> openedAt = event.timeStamp
                UsageEvents.Event.MOVE_TO_BACKGROUND -> if (openedAt > 0L) {
                    total += event.timeStamp - openedAt
                    openedAt = 0L
                }
            }
        }

        // Still open. This is the normal case: the figure is read on the way out of a visit, while
        // the app under test is on screen and has not been paused yet.
        if (openedAt > 0L) total += now - openedAt
        return total
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
