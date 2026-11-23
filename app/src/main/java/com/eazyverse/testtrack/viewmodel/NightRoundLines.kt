package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.domain.RoundRules
import com.eazyverse.testtrack.ui.Snack
import com.eazyverse.testtrack.ui.SnackTone
import java.time.Instant
import java.time.ZoneId

/*
 * What [NightRound] says and the midnight rule it follows: the lines for the Crew page, whether a
 * round has crossed into another night, and the way back to TestTrack. Beside the round, so the
 * object itself stays the part that holds its state and runs the clock.
 */

internal fun shortLine(
    stop: RoundStore.Stop,
    result: Outcome.Short,
) = Snack("${stop.label} was open ${result.ms / 1000}s. It needs ${NightRound.COUNT_MS / 1000} to count.")

/** The line for a round that ends after [stop] was recorded: its last word, or how many are left. */
internal fun endedLine(
    stop: RoundStore.Stop,
    moved: RoundStore.Round,
    result: Outcome,
): Snack {
    val left = moved.stops.size - moved.index - 1
    return when {
        left == 0 -> lastSnack(moved)
        result is Outcome.Refused -> Snack("${result.message} Open all again for the $left left.")
        else -> Snack("${stop.label} counted. Open all again for the $left left.")
    }
}

/** The round's last word: tonight done, or how many were left out and why. A private test is its own app's. */
internal fun lastSnack(round: RoundStore.Round): Snack {
    val skipped = round.skipped
    val only = round.stops.singleOrNull()?.takeIf { it.privateTestId.isNotBlank() }
    if (only != null && skipped.isEmpty()) return Snack("${only.label} counted for tonight.", SnackTone.DONE)
    val clean = skipped.isEmpty() && round.noPicture == 0
    return Snack(roundLastWord(skipped, round.noPicture), if (clean) SnackTone.DONE else SnackTone.INFO)
}

/** A crew app's visit is dated by its night, so a round that has crossed midnight must not go on. */
internal fun newNightFor(
    stop: RoundStore.Stop,
    startedAt: Long,
) = stop.privateTestId.isBlank() && isNewNight(startedAt)

/** TestTrack to the front, on the Crew tab, once a floating round is over. */
internal fun bringBack(context: Context) = MainActivity.bringForward(context, openCrew = true)

/** Said when a round is found to have crossed midnight: its stops belong to the night it began. */
internal const val NEW_NIGHT_LINE = "It's a new night. Open the round again."

/**
 * Whether the phone's local date has changed since the round began, so a visit would be dated a
 * different night from the one its stop was set up for. 0 (a round from a build that did not keep
 * its start) is never a new night.
 */
internal fun isNewNight(
    startedAt: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean =
    startedAt > 0 &&
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate() != Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

/** The round's last line: tonight done, how many were left out, and how many counted apps have no picture. */
internal fun roundLastWord(
    skipped: List<String>,
    noPicture: Int = 0,
): String {
    if (noPicture <= 0) return RoundRules.lastWord(skipped)
    val missing =
        if (noPicture == 1) {
            "1 picture is missing. Add it from the Crew page."
        } else {
            "$noPicture pictures are missing. Add them from the Crew page."
        }
    return if (skipped.isEmpty()) "Counted, but $missing" else "${skipped.size} left. ${skipped.first()} $missing"
}
