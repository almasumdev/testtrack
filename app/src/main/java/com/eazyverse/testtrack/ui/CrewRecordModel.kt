package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.instantOf
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.domain.shortDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/*
 * A finished crew as the Record tab draws it, and the plain rules for what its tile says.
 *
 * The server decides every one of these facts; this only words them. Pure functions over values,
 * so each is checked on a desk without a device.
 */

/** How one member's run in one crew came to. Times are ISO strings, null where there are none. */
data class CrewRecord(
    val crewId: String,
    val crewNumber: Int = 0,
    val crewName: String = "",
    val startedAt: String? = null,
    val endedAt: String? = null,
    val runDays: Int = CrewRules.RUN_DAYS,
    /** Nights they owed something and the night was over: how long their app was tested. */
    val memberNights: Int = 0,
    val appName: String = "",
    val packageName: String = "",
    val appIcon: String = "",
    val outcome: RecordOutcome = RecordOutcome.FINISHED,
    val leftDay: Int? = null,
    /** Testers who opened the app every night it was owed: what Google's 12 is about. */
    val testersEveryDay: Int = 0,
    val counted: Boolean = false,
    val floor: Int = CrewRules.FLOOR,
    val movedToCrewNumber: Int? = null,
    val movedToCrewId: String? = null,
    val outReason: String? = null,
    val doneAt: String? = null,
    /** The member's one answer, null until given; [prodAccessUntil] is the last moment to give it. */
    val prodAccess: Boolean? = null,
    val prodAccessUntil: String? = null,
    val prodAccessReward: Int = 0,
    val perfectClaimAmount: Int = 0,
    val perfectClaimedAt: String? = null,
    val perfectClaimable: Boolean = false,
    val pruned: Boolean = false,
    /** The seat cost tix (false for one the team placed free), and how many came back. */
    val charged: Boolean = false,
    val tixSpent: Int = 0,
    val tixRefunded: Int = 0,
    /** The member's nights in order; empty for a record kept before they were. */
    val nights: List<NightMark> = emptyList(),
    /** The same nights as counts, which stay once the crew is pruned. */
    val nightCounts: Map<NightMark, Int> = emptyMap(),
    /** The other apps in the crew, never the reader's own. */
    val crewApps: List<RecordApp> = emptyList(),
)

/** How one of the member's nights went. */
enum class NightMark {
    KEPT,
    SHORT,
    MISSED,
    LET_OFF,
    REPORTED,
    AFTER_LEFT,
    NOT_IN,
    ;

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.name.equals(wire, ignoreCase = true) } ?: NOT_IN
    }
}

/** Another member's app in the crew; [left] when it did not finish the run. */
data class RecordApp(
    val name: String,
    val packageName: String,
    val icon: String,
    val left: Boolean,
)

enum class RecordOutcome {
    FINISHED,
    LEFT,
    REMOVED,
    MOVED,
    CANCELLED,
    ;

    companion object {
        fun of(wire: String) = entries.firstOrNull { it.name.equals(wire, ignoreCase = true) } ?: FINISHED
    }
}

/** Which of a tile's buttons was tapped and is sending. */
enum class RecordBusy { YES, NO, CLAIM }

/** The tile's one line of what happened, and how loudly it says it. */
fun recordHeadline(r: CrewRecord): Pair<String, Tone> =
    when (r.outcome) {
        RecordOutcome.FINISHED ->
            "Finished with ${r.testersEveryDay} testers" to if (r.testersEveryDay >= r.floor) Tone.OK else Tone.WARN
        RecordOutcome.LEFT -> (r.leftDay?.let { "You left on day $it" } ?: "You left the run") to Tone.QUIET
        RecordOutcome.REMOVED -> {
            val base = r.leftDay?.let { "Taken out on day $it" } ?: "Taken out by the team"
            val reason = cleanReason(r.outReason)
            (if (reason != null) "$base: $reason" else base) to Tone.BAD
        }
        RecordOutcome.MOVED ->
            (r.movedToCrewNumber?.let { "Moved to ${crewNameOf(it)}" } ?: "Moved to another crew") to Tone.ACCENT
        RecordOutcome.CANCELLED -> "Crew cancelled" to Tone.QUIET
    }

/** The server's own crew name for a number: "Crew 06". */
fun crewNameOf(number: Int) = "Crew %02d".format(Locale.ROOT, number)

/**
 * The days the crew ran, in this phone's calendar: "Oct 6 to 19", "Sep 22 to Oct 5", or one day
 * for a crew cancelled before it ran. [CrewRecord.endedAt] is the midnight after the last night,
 * so the last day is the one before it.
 */
fun recordDates(
    r: CrewRecord,
    zone: ZoneId = ZoneId.systemDefault(),
): String {
    val endAt = instantOf(r.endedAt) ?: instantOf(r.doneAt)
    val started = instantOf(r.startedAt)
    if (r.outcome == RecordOutcome.CANCELLED || started == null || endAt == null) {
        return (endAt ?: started)?.let { shortDate(it, zone) }.orEmpty()
    }
    val day = { date: LocalDate -> shortDate(date.atStartOfDay(zone).toInstant(), zone) }
    val last = endAt.atZone(zone).minusMinutes(1).toLocalDate()
    val first = last.minusDays((r.runDays - 1).coerceAtLeast(0).toLong())
    return when {
        first == last -> day(last)
        first.month == last.month -> "${day(first)} to ${last.dayOfMonth}"
        else -> "${day(first)} to ${day(last)}"
    }
}

/** The production question is open: a finished run, not answered, and still inside its days. */
fun asksProdAccess(
    r: CrewRecord,
    now: Instant = Instant.now(),
): Boolean =
    r.outcome == RecordOutcome.FINISHED &&
        r.prodAccess == null &&
        instantOf(r.prodAccessUntil)?.let { now < it } == true

/** The question's days ran out with no answer. */
fun prodAccessMissed(
    r: CrewRecord,
    now: Instant = Instant.now(),
): Boolean =
    r.outcome == RecordOutcome.FINISHED &&
        r.prodAccess == null &&
        instantOf(r.prodAccessUntil)?.let { now >= it } == true

/** A perfect run's tix wait to be claimed. They never run out. */
fun canClaimPerfect(r: CrewRecord): Boolean = r.perfectClaimable && r.perfectClaimedAt == null

/**
 * "Details clear in 2 days", or "Details clear tonight" on the last day (true for warn), while a
 * crew's nights and pictures can still be opened. Null when they are gone or were never kept.
 */
fun detailsClear(
    detailsUntil: String?,
    pruned: Boolean,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<String, Boolean>? {
    if (pruned) return null
    val until = instantOf(detailsUntil)?.takeIf { now < it } ?: return null
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), until.atZone(zone).toLocalDate())
    return when {
        days <= 0 -> "Details clear tonight" to true
        days == 1L -> "Details clear in 1 day" to false
        else -> "Details clear in $days days" to false
    }
}

/**
 * When Uninstall all opens: [CrewRules.UNINSTALL_AFTER_DAYS] days after the whole crew finished
 * ([CrewRecord.doneAt]), or after its end when that is missing. Null when neither is known.
 */
fun uninstallOpensAt(r: CrewRecord): Instant? =
    (instantOf(r.doneAt) ?: instantOf(r.endedAt))?.plus(CrewRules.UNINSTALL_AFTER_DAYS.toLong(), ChronoUnit.DAYS)

/** Uninstall all is open. A record with no known end is not held back: the crew is over. */
fun uninstallOpen(
    r: CrewRecord,
    now: Instant = Instant.now(),
): Boolean = uninstallOpensAt(r)?.let { now >= it } ?: true

/**
 * "Opens Nov 24, in 2 days" while Uninstall all is locked, worded like [detailsClear]: the days
 * are whole calendar days on this phone, and the last one is "Opens today". Null once it is open.
 */
fun uninstallOpensLine(
    r: CrewRecord,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): String? {
    val opens = uninstallOpensAt(r)?.takeIf { now < it } ?: return null
    val days = ChronoUnit.DAYS.between(now.atZone(zone).toLocalDate(), opens.atZone(zone).toLocalDate())
    return when {
        days <= 0 -> "Opens today"
        days == 1L -> "Opens ${shortDate(opens, zone)}, in 1 day"
        else -> "Opens ${shortDate(opens, zone)}, in $days days"
    }
}

/** The member's nights as counts: from the nights while they are there, else from the kept counts. */
fun nightCountsOf(r: CrewRecord): Map<NightMark, Int> =
    if (r.nights.isNotEmpty()) r.nights.groupingBy { it }.eachCount() else r.nightCounts.filterValues { it > 0 }

/** How long the reader's app was tested: "5 nights". */
private fun testedFor(r: CrewRecord) = "${r.memberNights} ${pluralOf(r.memberNights, "night")}"

/** The line under the reader's app on a finished crew's page. */
fun yourAppLine(r: CrewRecord): String =
    when (r.outcome) {
        RecordOutcome.LEFT -> "${testedFor(r)}, then you left"
        RecordOutcome.REMOVED -> "${testedFor(r)}, then taken out"
        else -> "${r.testersEveryDay} every night"
    }

/** The chip over the reader's nights, and how loudly it says it. */
fun nightsSummary(r: CrewRecord): Pair<String, Tone> {
    if (r.outcome == RecordOutcome.LEFT) return "${testedFor(r)}, then you left" to Tone.QUIET
    if (r.outcome == RecordOutcome.REMOVED) return (r.leftDay?.let { "Taken out on day $it" } ?: "Taken out") to Tone.BAD
    val counts = nightCountsOf(r)
    val owed = counts.filterKeys { it != NightMark.AFTER_LEFT && it != NightMark.NOT_IN }.values.sum()
    val kept = counts[NightMark.KEPT] ?: 0
    return if (owed > 0 && kept == owed) "Every night kept" to Tone.OK else "$kept of $owed kept" to Tone.QUIET
}

/** What a night's square stands for, in the legend under the nights. */
fun nightLabel(
    mark: NightMark,
    outcome: RecordOutcome,
): String =
    when (mark) {
        NightMark.KEPT -> "kept"
        NightMark.SHORT -> "short"
        NightMark.MISSED -> "missed"
        NightMark.LET_OFF -> "let off"
        NightMark.REPORTED -> "reported"
        NightMark.AFTER_LEFT -> if (outcome == RecordOutcome.REMOVED) "after it was taken out" else "after you left"
        NightMark.NOT_IN -> "not in your run"
    }

/** The day a crew's details were cleared, "Nov 20", or null when it is not known. */
fun clearedOn(
    detailsUntil: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): String? = instantOf(detailsUntil)?.let { shortDate(it, zone) }

/** Whole days between a crew's end and its details clearing, or null when either is not known. */
fun keptDays(
    endedAt: String?,
    detailsUntil: String?,
): Long? {
    val end = instantOf(endedAt) ?: return null
    val until = instantOf(detailsUntil) ?: return null
    return ChronoUnit.HOURS
        .between(end, until)
        .takeIf { it > 0 }
        ?.let { (it + 12) / 24 }
}

/** The team's words, without a closing full stop, or null when there are none. */
private fun cleanReason(reason: String?): String? {
    val words = reason?.trim() ?: return null
    return words.trimEnd('.').ifEmpty { null }
}
