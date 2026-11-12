package com.eazyverse.testtrack.ui

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.math.roundToLong

/*
 * The words under an open crew's name: about when it will start.
 *
 * Pure over values, with the clock and the zone passed in, so every branch is tested on a desk.
 * The server only says when the crew will probably be full. A run starts at the reader's own
 * midnight after that, so the day count is worked out here, on this phone, in this zone.
 */

/** The estimate line. [known] is false for "no estimate yet", which is drawn quieter and without the clock. */
data class StartEstimate(
    val text: String,
    val known: Boolean,
)

/**
 * What the open crew says about when it starts, or null when the server sent nothing to say.
 *
 * The start is the first local midnight after [likelyFullAt]. Counted from now it is tonight (the
 * coming midnight), tomorrow night (the one after), then in about so many days, weeks, or over a
 * month. A basis of "new" means no history to go on, whatever else came with it.
 */
fun startEstimate(
    likelyFullAt: String?,
    likelyBasis: String?,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): StartEstimate? {
    if (likelyBasis == "new") return StartEstimate("No start estimate yet", known = false)
    val full = likelyFullAt?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return null

    val coming =
        now
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
    val start =
        full
            .atZone(zone)
            .toLocalDate()
            .plusDays(1)
            .atStartOfDay(zone)
    val text =
        when {
            // Already full, or full before the coming midnight: it starts at that one.
            !start.isAfter(coming) -> "Likely starts tonight"
            start == coming.toLocalDate().plusDays(1).atStartOfDay(zone) -> "Likely starts tomorrow night"
            else -> {
                val days = ((start.toEpochSecond() - now.epochSecond) / DAY_SECONDS).roundToLong().coerceAtLeast(2L)
                when {
                    days < 14L -> "Likely starts in about $days days"
                    days < 35L -> "Likely starts in about ${(days / 7.0).roundToLong()} weeks"
                    else -> "Likely starts in over a month"
                }
            }
        }
    return StartEstimate(text, known = true)
}

private const val DAY_SECONDS = 86_400.0

/** "9 of 15 seats left", "1 seat left", or "No seats left" for the open crew's name line. */
fun seatsLeftText(free: Int): String =
    when {
        free <= 0 -> "No seats left"
        free == 1 -> "1 seat left"
        else -> "$free of ${CrewRules.SEATS} seats left"
    }
