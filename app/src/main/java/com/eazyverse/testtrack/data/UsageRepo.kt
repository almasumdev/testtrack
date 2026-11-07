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
import com.eazyverse.testtrack.domain.UsageSessions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * How long an app was actually on screen.
 *
 * A screenshot proves the app opened. It cannot prove anyone stayed, and Play is not satisfied by
 * a launch either, so the daily report carries real foreground time.
 *
 * This is the one thing TestTrack asks for that costs the tester a trip to Settings.
 * `PACKAGE_USAGE_STATS` cannot be granted by a runtime prompt: it is an appop, toggled by hand
 * under Special access. There is no cheaper source: anything measured inside TestTrack only sees
 * the session TestTrack itself started, which is exactly the number a tester could game by
 * waiting.
 */
object UsageRepo {
    /**
     * True once the tester has switched TestTrack on under Settings > Special access.
     *
     * Two spellings of one call. `unsafeCheckOpNoThrow` only exists from API 29, and this runs on
     * 26: reaching for it unguarded is a `NoSuchMethodError` on Android 8 and 9, on the first
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
     * The per-app deep link is unreliable across manufacturers, so fall back to the plain list:
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
     * The window is the visit, handed in by the caller. Summed from raw events rather than
     * `queryUsageStats`, whose daily buckets are written late. The rules for adding the events up
     * live in [UsageSessions], where every ordering a phone produces is tested; this only reads
     * the events, from [LOOK_BACK] before the window so a visit already under way is seen opening.
     */
    fun foregroundMsSince(
        context: Context,
        pkg: String,
        since: Long,
    ): Long = read(context, pkg, since).ms

    /**
     * A reading for a visit that has just ended, taken the way a returning screen should take it.
     *
     * Some phones hand usage events to the system a moment late, so a reading taken the instant
     * somebody comes back can be missing the end, or the whole visit. If the first reading is
     * under [needMs] it waits [SETTLE_MS] and reads once more, keeping the larger. Each reading is
     * also recorded (how long, how many events, which phone) so a phone that still reads short
     * says why.
     */
    suspend fun measure(
        context: Context,
        pkg: String,
        since: Long,
        needMs: Long,
        source: String,
        settle: Boolean = true,
    ): Long = measured(context, pkg, since, needMs, source, settle).ms

    /**
     * The reading behind [measure] and [measureUse]. Off the main thread: days of usage events for
     * every app is a lot to walk on a busy phone, and this runs on every return to the review.
     */
    private suspend fun measured(
        context: Context,
        pkg: String,
        since: Long,
        needMs: Long,
        source: String,
        settle: Boolean,
    ): Reading {
        var reading = withContext(Dispatchers.Default) { read(context, pkg, since) }
        var retried = false
        if (settle && reading.ms < needMs && reading.access) {
            delay(SETTLE_MS)
            val again = withContext(Dispatchers.Default) { read(context, pkg, since) }
            if (again.ms > reading.ms) reading = again
            retried = true
        }
        Telemetry.usageRead(source, reading.ms, reading.events, reading.access, retried)
        return reading
    }

    /** Time in an app since a moment, the calendar days of it, and when its first day's use began. */
    class Use(
        val ms: Long,
        val days: Int,
        val firstAt: Long? = null,
    )

    /**
     * [measure], with the calendar days the app was used on as well: what a live review's second
     * day is counted from, so a day's use counts even when TestTrack was not opened that day.
     */
    suspend fun measureUse(
        context: Context,
        pkg: String,
        since: Long,
        needMs: Long,
        source: String,
        settle: Boolean = true,
    ): Use {
        val reading = measured(context, pkg, since, needMs, source, settle)
        return Use(reading.ms, reading.days, reading.firstAt)
    }

    private class Reading(
        val ms: Long,
        val events: Int,
        val access: Boolean,
        val days: Int = 0,
        val firstAt: Long? = null,
    )

    @Suppress("DEPRECATION")
    private fun read(
        context: Context,
        pkg: String,
        since: Long,
    ): Reading {
        if (!hasAccess(context)) return Reading(0L, 0, access = false)
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        if (since <= 0L || since >= now) return Reading(0L, 0, access = true)

        return runCatching {
            val helpers = browsers(context) - pkg
            val events = events(manager, since - LOOK_BACK, now)
            val mine = events.count { it.pkg == pkg }
            val zone = java.time.ZoneId.systemDefault()
            val dayOf = { at: Long ->
                java.time.Instant
                    .ofEpochMilli(at)
                    .atZone(zone)
                    .toLocalDate()
                    .toEpochDay()
            }
            Reading(
                UsageSessions.foregroundMs(events, pkg, since, now, helpers),
                mine,
                access = true,
                days = UsageSessions.daysUsed(events, pkg, since, now, dayOf, helpers = helpers),
                firstAt = UsageSessions.firstUse(events, pkg, since, now, helpers = helpers),
            )
        }.getOrDefault(Reading(0L, 0, access = true))
    }

    /**
     * Whether [pkg] is on screen now, by the phone's own record: the app itself, or a browser it
     * handed over to. Read before and after the night's picture so the picture is of the app and
     * never of TestTrack. True when there is no usage access, since nothing could be checked and
     * the visit will not count without it anyway.
     */
    suspend fun inFront(
        context: Context,
        pkg: String,
        since: Long,
    ): Boolean =
        withContext(Dispatchers.Default) {
            if (!hasAccess(context)) return@withContext true
            val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            runCatching {
                val events = events(manager, since - LOOK_BACK, now)
                UsageSessions.inFront(events, pkg, since, now, browsers(context) - pkg)
            }.getOrDefault(true)
        }

    /** The phone's usage events between [from] and [to], in the terms [UsageSessions] works in. */
    private fun events(
        manager: UsageStatsManager,
        from: Long,
        to: Long,
    ): List<UsageSessions.Event> {
        val raw = manager.queryEvents(from, to)
        val event = UsageEvents.Event()
        val events = ArrayList<UsageSessions.Event>()
        while (raw.hasNextEvent()) {
            raw.getNextEvent(event)
            @Suppress("DEPRECATION")
            val kind =
                when (event.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageSessions.Kind.RESUMED
                    UsageEvents.Event.MOVE_TO_BACKGROUND, ACTIVITY_STOPPED -> UsageSessions.Kind.PAUSED
                    SCREEN_NON_INTERACTIVE, KEYGUARD_SHOWN, DEVICE_SHUTDOWN -> UsageSessions.Kind.SCREEN_OFF
                    else -> null
                } ?: continue
            events += UsageSessions.Event(kind, event.packageName ?: "", event.className ?: "", event.timeStamp)
        }
        return events
    }

    /**
     * The browsers on this phone: whatever opens a web link, and whatever offers Custom Tabs. An
     * app that is a web page in a browser's frame is in front as the browser, and its time is
     * counted through these (see [UsageSessions]).
     */
    private fun browsers(context: Context): Set<String> {
        val pm = context.packageManager
        val web =
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE)
        val links = runCatching { pm.queryIntentActivities(web, PackageManager.MATCH_ALL) }.getOrDefault(emptyList())
        val tabs = runCatching { pm.queryIntentServices(Intent(CUSTOM_TABS), 0) }.getOrDefault(emptyList())
        return (links.mapNotNull { it.activityInfo?.packageName } + tabs.mapNotNull { it.serviceInfo?.packageName })
            .toSet() - context.packageName
    }

    private const val CUSTOM_TABS = "android.support.customtabs.action.CustomTabsService"

    /** How far before the window events are read, to catch a session already under way. */
    private const val LOOK_BACK = 6 * 60 * 60 * 1000L

    /** How long to wait before the second reading, for phones that report events late. */
    private const val SETTLE_MS = 1_500L

    // Event types added after our minSdk; the values are fixed in the platform.
    private const val SCREEN_NON_INTERACTIVE = 16
    private const val KEYGUARD_SHOWN = 17
    private const val ACTIVITY_STOPPED = 23
    private const val DEVICE_SHUTDOWN = 26
}
