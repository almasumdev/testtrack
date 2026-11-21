package com.eazyverse.testtrack.domain

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Times, said one way everywhere: the server's ISO strings read once, a day as "Nov 9", a time of
 * day in the phone's own 12 or 24 hour clock, and how long is left.
 */

/** An ISO time from the server, with or without an offset; null when blank or not a time. */
fun instantOf(iso: String?): Instant? =
    iso?.takeIf { it.isNotBlank() }?.let {
        runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
            ?: runCatching { Instant.parse(it) }.getOrNull()
    }

private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** A day, month first and no year: "Nov 9", in [zone]. */
fun shortDate(
    at: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): String = at.atZone(zone).format(MONTH_DAY)

/** A calendar day as "Nov 9". */
fun shortDate(date: java.time.LocalDate): String = date.format(MONTH_DAY)

/** [shortDate] for a time in epoch milliseconds. */
fun shortDate(
    millis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): String = shortDate(Instant.ofEpochMilli(millis), zone)

private val CLOCK_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val CLOCK_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

/**
 * A time of day the way the phone's clock shows it: "8:15 PM", or "20:15" when [hour24], which
 * the screen reads from the phone's own setting (DateFormat.is24HourFormat).
 */
fun clockTime(
    at: Instant,
    hour24: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
): String = at.atZone(zone).format(if (hour24) CLOCK_24 else CLOCK_12)

private const val HOUR_MS = 3_600_000L

/**
 * How long is left until [until], in whole units rounded down so it never promises more time than
 * there is, with [suffix] after it: "1d 20h left", "6h left", "25m left". [daysOnly] drops the
 * hours from a time over a day: "2d left". Past the deadline it is "Due now", with no suffix.
 */
fun timeLeft(
    until: Long,
    now: Long,
    suffix: String,
    daysOnly: Boolean = false,
): String {
    val left = until - now
    if (left <= 0) return "Due now"
    val hours = left / HOUR_MS
    val days = hours / 24
    val amount =
        when {
            hours >= 24 && (daysOnly || hours % 24 == 0L) -> "${days}d"
            hours >= 24 -> "${days}d ${hours % 24}h"
            hours >= 1 -> "${hours}h"
            else -> "${maxOf(1, left / 60_000)}m"
        }
    return amount + suffix
}

/** [word], with an "s" unless there is exactly one: "1 night", "2 nights". Only the word. */
fun pluralOf(
    n: Int,
    word: String,
): String = if (n == 1) word else "${word}s"
