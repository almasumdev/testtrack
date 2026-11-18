package com.eazyverse.testtrack.ui

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val end = (instantOf(r.endedAt) ?: instantOf(r.doneAt))?.atZone(zone)
    val started = instantOf(r.startedAt)
    if (r.outcome == RecordOutcome.CANCELLED || started == null || end == null) {
        return (end ?: started?.atZone(zone))?.format(MONTH_DAY).orEmpty()
    }
    val last = end.minusMinutes(1).toLocalDate()
    val first = last.minusDays((r.runDays - 1).coerceAtLeast(0).toLong())
    return when {
        first == last -> last.format(MONTH_DAY)
        first.month == last.month -> "${first.format(MONTH_DAY)} to ${last.dayOfMonth}"
        else -> "${first.format(MONTH_DAY)} to ${last.format(MONTH_DAY)}"
    }
}

/** The production question is open: a counted run, not answered, and still inside its days. */
fun asksProdAccess(
    r: CrewRecord,
    now: Instant = Instant.now(),
): Boolean = r.counted && r.prodAccess == null && instantOf(r.prodAccessUntil)?.let { now < it } == true

/** The question's days ran out with no answer. */
fun prodAccessMissed(
    r: CrewRecord,
    now: Instant = Instant.now(),
): Boolean = r.counted && r.prodAccess == null && instantOf(r.prodAccessUntil)?.let { now >= it } == true

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

/** The team's words, without a closing full stop, or null when there are none. */
private fun cleanReason(reason: String?): String? {
    val words = reason?.trim() ?: return null
    return words.trimEnd('.').ifEmpty { null }
}

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

private fun instantOf(iso: String?): Instant? =
    iso?.takeIf { it.isNotBlank() }?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(it) }.getOrNull()
    }
