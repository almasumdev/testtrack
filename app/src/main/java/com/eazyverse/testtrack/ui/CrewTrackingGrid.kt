package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.viewmodel.AppNights
import com.eazyverse.testtrack.viewmodel.MemberNights

/*
 * The run at a glance, the way a contribution graph reads: one square per tester per night, and
 * under each night how many testers it counted for, against Google's 12. A night already over with
 * fewer than 12 is said by number, since that is the night the owner's run is short on.
 */

/** What one square says. Each has its own fill and edge, so it reads without the colour too. */
private enum class Square { PICTURE, BY_HAND, WAITING, WAITING_LONG, MISSED, NOT_OWED, TONIGHT, TO_COME, BEFORE }

/**
 * What one tester's night was, as the server judges it: a night they did not owe (reported, let
 * off, or a quiet skip) or one before their first is neutral, never a miss.
 */
private fun squareOf(
    tester: MemberNights,
    day: Int,
    offDays: Set<Int>,
): Square {
    val night = tester.nights.firstOrNull { it.day == day }
    return when {
        day < tester.firstNight -> Square.BEFORE
        night != null && night.counts && night.byHand -> Square.BY_HAND
        night != null && night.counts -> Square.PICTURE
        night != null && night.waitingLong() -> Square.WAITING_LONG
        night != null -> Square.WAITING
        tester.notOwed(day, offDays) -> Square.NOT_OWED
        day == tester.day -> Square.TONIGHT
        day > tester.day -> Square.TO_COME
        else -> Square.MISSED
    }
}

/** The nights over and short of Google's 12, by number. */
private fun shortNights(pictures: AppNights): List<Int> {
    val last = pictures.testers.maxOfOrNull { it.day - 1 } ?: 0
    return (1..minOf(last, pictures.runDays)).filter { day ->
        day !in pictures.offDays &&
            pictures.testers.count { t -> !t.out && t.nights.any { it.day == day && it.counts } } < CrewRules.FLOOR
    }
}

@Composable
internal fun TrackingGrid(pictures: AppNights) {
    val t = Tt.colors
    val short = shortNights(pictures)
    CrewCard(
        padding =
            androidx.compose.foundation.layout
                .PaddingValues(12.dp),
    ) {
        StatusChip(
            if (short.isEmpty()) "On track for Google" else "Short on ${plural(short.size, "night")} ${short.joinToString()}",
            if (short.isEmpty()) Tone.OK else Tone.WARN,
        )
        Text(
            "Each square is one tester on one night. The number under a night is how many it counted for. " +
                "Google asks for ${CrewRules.FLOOR}.",
            Modifier.padding(top = 6.dp, bottom = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = t.text3,
        )
        pictures.testers.forEach { tester ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.5.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    tester.name.substringBefore(' '),
                    Modifier.width(64.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (tester.out) t.text4 else t.text2,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (day in 1..pictures.runDays) {
                        Cell(squareOf(tester, day, pictures.offDays), Modifier.weight(1f), "${tester.name}, night $day")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(64.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (day in 1..pictures.runDays) {
                    val n = pictures.testers.count { tester -> tester.nights.any { it.day == day && it.counts } }
                    Text(
                        if (pictures.testers.any { day < it.day || day == it.day }) "$n" else "",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        fontWeight = if (day in short) FontWeight.Bold else FontWeight.Normal,
                        color = if (day in short) t.bad else t.text4,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Text(
            "Full: picture in. Yellow edge: picked by hand. Outline: tonight. Red: missed. Grey: not owed that night.",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = t.text4,
        )
    }
}

@Composable
private fun Cell(
    square: Square,
    modifier: Modifier,
    said: String,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(3.dp)
    val (fill, edge) =
        when (square) {
            Square.PICTURE -> Status.posted to Status.posted
            Square.BY_HAND -> Status.posted to t.warn
            Square.WAITING -> t.accent.copy(alpha = 0.35f) to t.accent
            Square.WAITING_LONG -> t.badBg to t.accent
            Square.MISSED -> t.badBg to t.bad
            Square.NOT_OWED -> t.glass2 to t.glassBorder
            Square.TONIGHT -> Color.Transparent to t.accent
            Square.TO_COME -> Color.Transparent to t.glassBorder
            Square.BEFORE -> Color.Transparent to Color.Transparent
        }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(fill)
            .border(if (square == Square.BY_HAND || square == Square.TONIGHT) 1.5.dp else 1.dp, edge, shape)
            .semantics { contentDescription = "$said: ${square.name.lowercase().replace('_', ' ')}" },
    )
}

private fun plural(
    n: Int,
    word: String,
) = if (n == 1) word else "${word}s"
