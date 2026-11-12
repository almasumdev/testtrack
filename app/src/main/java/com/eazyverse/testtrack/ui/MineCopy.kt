package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.closeTo
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * The words on a My reviews ticket. Each line has to fit beside the app's mark without wrapping,
 * so the next steps are kept to about 24 characters.
 */

/** Where a review in progress has got to, out of the four steps. */
internal fun mineStep(review: MyReview): Int =
    when (review.state) {
        "claimed" -> 1
        "installed" -> 2
        "checking" -> 4
        else -> 3
    }

/** What to do next, for a review in progress. */
internal fun mineNext(
    review: MyReview,
    now: Instant = Instant.now(),
): String =
    when (review.state) {
        "claimed" -> "Install from Play"
        "installed" -> if (review.isLive) liveUseLine(review, now) else "Use it for 2 minutes"
        "used" -> if (review.isLive) "Review it on Play" else "Send feedback on Play"
        "checking" -> "Retake your screenshot"
        else -> ""
    }

/** Who has the review now, for one that is sent. */
internal fun mineWaitingWith(review: MyReview): String = if (review.state == "disputed") "Members are deciding" else "With the developer"

/** The last line of a sent review: when it went, why it pays half, or what was asked for. */
internal fun mineWaitNote(
    review: MyReview,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String =
    when {
        review.state == "disputed" -> "You asked for a second look"
        review.noBox -> "Sent here, so it pays half"
        else -> mineDate(review.submittedAt, zone, locale)?.let { "Sent on $it" } ?: "Sent"
    }

/** How a decided review ended, with the day where the server gave one. */
internal fun mineHistory(
    review: MyReview,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val on = mineDate(review.decidedAt, zone, locale)
    return when (review.state) {
        "approved" -> on?.let { "Paid on $it" } ?: "Paid"
        "rejected" -> review.reason.trim().let { if (it.isEmpty()) "Refused" else "Refused. $it" }
        "expired" -> on?.let { "Ran out on $it" } ?: "Ran out"
        "abandoned" -> on?.let { "Given up on $it" } ?: "Given up"
        else -> ""
    }
}

/** "9 Nov" for a time the server sent, or null where there is none. */
internal fun mineDate(
    iso: String?,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String? {
    val at = parseInstant(iso) ?: return null
    return DateTimeFormatter.ofPattern("d MMM", locale).format(at.atZone(zone))
}

/** When a review in progress runs out. Only checking has a deadline the server sends so far. */
internal fun mineDeadline(review: MyReview): Instant? = if (review.state == "checking") parseInstant(review.checkBy) else null

/** "2d left", "23h left", "40m left"; "Due now" once it has come. */
internal fun shortLeft(
    until: Long,
    now: Long,
): String {
    val left = until - now
    if (left <= 0) return "Due now"
    val hours = left / HOUR_MS
    return when {
        hours >= 24 -> "${hours / 24}d left"
        hours >= 1 -> "${hours}h left"
        else -> "${maxOf(1, left / 60_000)}m left"
    }
}

/** Under twelve hours, or held up: worth the warning colour. */
internal fun leftIsUrgent(
    review: MyReview,
    until: Long,
    now: Long,
): Boolean = review.state == "checking" || closeTo(until, now)

private const val HOUR_MS = 3_600_000L

private fun parseInstant(iso: String?): Instant? =
    iso?.let {
        runCatching {
            OffsetDateTime
                .parse(it)
                .toInstant()
        }.getOrNull()
    }

/**
 * Where a live review is with its two days, so a row never asks for what is already done. At most
 * 24 characters, the longest being the day it opens: "Opens tomorrow 12:30 PM".
 */
internal fun liveUseLine(
    review: MyReview,
    now: Instant = Instant.now(),
): String {
    val opens = parseInstant(review.opensAt)
    return when {
        review.daysUsed >= 2 && opens != null && opens.isAfter(now) -> "Opens ${opensShort(opens, now)}"
        review.daysUsed >= 2 && review.usedEnough -> "Ready for your review"
        review.daysUsed >= 2 -> "Use it a bit longer"
        review.usedEnough || review.daysUsed == 1 -> "Open it on another day"
        else -> "Use it on two days"
    }
}

/** [opensWhen] without its filler: "today 9:40 AM", "tomorrow 9:40 AM", "Fri 9:40 AM". */
private fun opensShort(
    at: Instant,
    now: Instant,
): String =
    opensWhen(at, now)
        .replace("today at ", "today ")
        .replace("tomorrow at ", "tomorrow ")
        .replace(Regex("^on (\\p{L}{1,3})\\p{L}* at "), "$1 ")
