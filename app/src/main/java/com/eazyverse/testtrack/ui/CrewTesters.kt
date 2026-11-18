package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.MemberNights
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime

/*
 * The testers of the reader's own app, in the shape the testers page draws them: one square per
 * night for the grid and the ring, one short line for the tile, and the last three nights' pictures.
 *
 * Built from two answers: the crew (who each member is, what their app is, when it left) and the
 * pictures (every night, as the server judged it). Pure values, so all of it is testable on a desk.
 */

/** What one square says. Each has its own fill and edge, so it reads without the colour too. */
internal enum class Square {
    PICTURE,
    BY_HAND,

    /** Opened, on a finished crew whose picture was cleared: only the last three nights keep one. */
    OPENED,
    WAITING,
    WAITING_LONG,
    MISSED,
    NOT_OWED,
    TONIGHT,
    TO_COME,

    /** Before their first night with the app: not joined yet. */
    BEFORE,
    ;

    /** The night counted for the reader's app. */
    val counted: Boolean get() = this == PICTURE || this == BY_HAND || this == OPENED
}

/** One tester, as the grid, the tile and the profile popup show them. */
internal data class CrewTester(
    val id: String,
    val name: String,
    val photo: String = "",
    val email: String = "",
    val appName: String = "",
    val packageName: String = "",
    val icon: String = "",
    /** This account's own seat. */
    val mine: Boolean = false,
    /** Their own day of the run, and whether it differs from the reader's (another time zone). */
    val day: Int = 0,
    val otherDay: Boolean = false,
    val out: Boolean = false,
    /** The night their app left the run, when it can be told. */
    val outDay: Int? = null,
    /** Nights they opened the reader's app on. */
    val days: Int = 0,
    /** The short line under their name, and how loudly it speaks. */
    val line: String = "",
    val tone: Tone = Tone.QUIET,
    /** One square per night of the run. */
    val squares: List<Square> = emptyList(),
    /** Their pictures of the last three nights, oldest first. */
    val pictures: List<NightPicture> = emptyList(),
    /** Opened the reader's app tonight, which puts them first on a running crew. */
    val openedTonight: Boolean = false,
    /** Their run is over: the crew finished, or their own last night is behind them. */
    val over: Boolean = false,
)

/** The first letter of a name, for a face that has no photo. */
internal fun initialOf(name: String) = name.trim().take(1).uppercase()

/**
 * What one tester's night was, as the server judges it: a night they did not owe (reported, let
 * off, or a quiet skip) or one before their first is neutral, never a miss. On a finished crew an
 * opened night without its picture is [Square.OPENED], since only the last nights keep one.
 */
internal fun squareOf(
    tester: MemberNights,
    day: Int,
    offDays: Set<Int>,
    finished: Boolean = false,
    outDay: Int? = null,
    now: Instant = Instant.now(),
): Square {
    val night = tester.nights.firstOrNull { it.day == day }
    return when {
        day < tester.firstNight -> Square.BEFORE
        night != null && night.counts && night.byHand -> Square.BY_HAND
        night != null && night.counts && finished && night.proof.isBlank() -> Square.OPENED
        night != null && night.counts -> Square.PICTURE
        night != null && night.waitingLong(now) -> Square.WAITING_LONG
        night != null -> Square.WAITING
        tester.notOwed(day, offDays) -> Square.NOT_OWED
        outDay != null && day >= outDay -> Square.TO_COME
        !finished && day == tester.day -> Square.TONIGHT
        day > tester.day -> Square.TO_COME
        else -> Square.MISSED
    }
}

/** The nights over and short of Google's 12, by number. */
internal fun shortNights(pictures: AppNights): List<Int> {
    val last = pictures.testers.maxOfOrNull { it.day - 1 } ?: 0
    return (1..minOf(last, pictures.runDays)).filter { day ->
        day !in pictures.offDays &&
            pictures.testers.count { t -> !t.out && t.nights.any { it.day == day && it.counts } } < CrewRules.FLOOR
    }
}

/** How many testers each night counted for, or null for a night nobody has reached yet. */
internal fun nightCounts(pictures: AppNights): List<Int?> =
    (1..pictures.runDays).map { day ->
        if (pictures.testers.any { day <= it.day }) {
            pictures.testers.count { tester -> tester.nights.any { it.day == day && it.counts } }
        } else {
            null
        }
    }

/**
 * The night an app taken out left the run, in its owner's own calendar: their day now, less the
 * whole days since it left. Null while it is in, or when the server did not say when.
 */
internal fun outDayOf(
    day: Int,
    outAt: String,
    runDays: Int,
    now: Instant = Instant.now(),
): Int? {
    val left = runCatching { OffsetDateTime.parse(outAt).toInstant() }.getOrNull() ?: return null
    val daysSince = Duration.between(left, now).toDays().coerceAtLeast(0)
    return (day - daysSince.toInt()).coerceIn(1, runDays)
}

/**
 * Every tester of the reader's app, in the grid's order: as the server lists them, with those
 * out of the run last. [crew] adds who each one is (face, email, app); without it the pictures
 * alone are drawn, with initials.
 */
internal fun testersOf(
    pictures: AppNights,
    crew: Crew? = null,
    now: Instant = Instant.now(),
): List<CrewTester> {
    val finished = crew?.phase == CrewPhase.RECORD
    val members = crew?.members.orEmpty().associateBy { it.id }
    val seats = crew?.seats.orEmpty().associateBy { it.id }
    return pictures.testers
        .map { tester ->
            val member = members[tester.seatId]
            val seat = seats[tester.seatId]
            val outDay = if (tester.out) member?.outAt?.let { outDayOf(tester.day, it, pictures.runDays, now) } else null
            val squares =
                (1..pictures.runDays).map { day -> squareOf(tester, day, pictures.offDays, finished, outDay, now) }
            val over = finished || tester.day > pictures.runDays
            val tonight = squares.getOrNull(tester.day - 1)
            val (line, tone) = lineOf(tester, member, squares, over, outDay, now)
            val otherDay = !over && crew != null && crew.day > 0 && tester.day != crew.day
            val days = squares.count { it.counted }
            CrewTester(
                id = tester.seatId,
                name = tester.name,
                photo = tester.photo.ifBlank { member?.photo.orEmpty() },
                email = tester.email.ifBlank { member?.email.orEmpty() },
                appName = member?.appName ?: seat?.appName.orEmpty(),
                packageName = member?.packageName?.ifBlank { null } ?: seat?.packageName.orEmpty(),
                icon = member?.icon?.ifBlank { null } ?: seat?.icon.orEmpty(),
                mine = member?.mine == true,
                day = tester.day,
                otherDay = otherDay,
                out = tester.out,
                outDay = outDay,
                days = days,
                line = if (otherDay && !tester.out) "$line, $days ${pluralOf(days, "day")}" else line,
                tone = tone,
                squares = squares,
                pictures = lastPictures(tester, pictures, if (over) pictures.runDays else tester.day, outDay),
                openedTonight = !over && !tester.out && tonight?.counted == true,
                over = over,
            )
        }.sortedBy { it.out }
}

/**
 * The tiles' order: those still in before those out, and on a running crew those who opened the
 * reader's app tonight first, as the members page always listed them.
 */
internal fun tileOrder(
    testers: List<CrewTester>,
    finished: Boolean,
): List<CrewTester> =
    if (finished) {
        testers.sortedBy { it.out }
    } else {
        testers.sortedWith(compareBy({ it.out }, { !it.openedTonight }))
    }

/** What their tile says under their name: tonight on a running crew, the whole run on a finished one. */
private fun lineOf(
    tester: MemberNights,
    member: CrewMember?,
    squares: List<Square>,
    over: Boolean,
    outDay: Int?,
    now: Instant,
): Pair<String, Tone> {
    if (tester.out) {
        val missedBefore = outDay != null && squares.getOrNull(outDay - 2) == Square.MISSED
        return if (outDay != null && missedBefore) "Taken out on day $outDay" to Tone.BAD else "Left the run" to Tone.QUIET
    }
    if (over) {
        val owed = squares.count { it != Square.NOT_OWED && it != Square.BEFORE }
        val opened = squares.count { it.counted }
        return if (opened >= owed) "All $owed ${pluralOf(owed, "day")}" to Tone.OK else "$opened of $owed days" to Tone.WARN
    }
    val day = tester.day
    val tonight = tester.nights.firstOrNull { it.day == day }
    val yesterday = squares.getOrNull(day - 2)
    return when {
        day < tester.firstNight -> "Not joined yet" to Tone.QUIET
        tester.excused || day in tester.reportedDays -> "Reported it" to Tone.QUIET
        tonight != null && tonight.counts && tonight.byHand ->
            "Opened yours${clockAt(tonight.at)}, picked by hand" to Tone.OK
        tonight != null && tonight.counts -> "Opened yours${clockAt(tonight.at).ifEmpty { " tonight" }}" to Tone.OK
        tonight != null && tonight.waitingLong(now) -> "Picture waiting for hours" to Tone.BAD
        tonight != null -> "Picture on its way" to Tone.ACCENT
        yesterday == Square.WAITING_LONG -> "Picture waiting for hours" to Tone.BAD
        member?.missedYesterday == true || yesterday == Square.MISSED -> "Missed yesterday" to Tone.WARN
        squares.getOrNull(day - 1) == Square.NOT_OWED -> "Not owed tonight" to Tone.QUIET
        else -> "Not tonight yet" to Tone.QUIET
    }
}

/** Their pictures of the three nights up to [upTo], oldest first. */
private fun lastPictures(
    tester: MemberNights,
    pictures: AppNights,
    upTo: Int,
    outDay: Int?,
): List<NightPicture> {
    val last = if (outDay != null) minOf(upTo, outDay) else upTo
    return tester.nights
        .filter { it.proof.isNotBlank() && it.day in (last - 2)..last }
        .sortedBy { it.day }
        .map { NightPicture(it.proof, tester.name, it.day, it.at, it.usedMs, it.byHand, pictures.appName) }
}

internal fun pluralOf(
    n: Int,
    word: String,
) = if (n == 1) word else "${word}s"
