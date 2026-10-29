package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * An app taken out of a run, from both sides.
 *
 * Its owner asks to get it back before anything else: a new crew is offered only once the team
 * has said no, the run ended without an answer, or the server says it can't be asked back (it was
 * put back once already, it is in another crew now, or the run is over). The card never offers a
 * put back the server would refuse. Everybody else keeps the app on the page under
 * Left the run, owed by nobody, so they know which one it was; a day later, once it is not coming
 * back on a whim, the row offers Android's own uninstall. See docs/crew.md.
 */

/** The owner's card for their own app taken out, at whichever point its appeal is. */
@Composable
internal fun OwnerOutCard(
    crew: Crew,
    actions: CrewActions,
) {
    val mine = crew.mine ?: return
    val appeal = mine.appeal
    val why = mine.outReason.ifBlank { "It missed two days in a row." }
    Column {
        when {
            // Put back once after an appeal, and out again: there is no second appeal.
            appeal?.state == "accepted" ->
                CrewHero(
                    chip = "Out again",
                    tone = Tone.BAD,
                    icon = Lucide.CircleX,
                    headline = "Your app left this run again",
                    line = "The team put it back once. $why It can't be asked back a second time.",
                    action = "Put it in a new crew",
                    onAction = actions.onTakeSeat,
                )
            appeal == null && !mine.canAppeal ->
                CrewHero(
                    chip = "Your app is out",
                    tone = Tone.BAD,
                    icon = Lucide.CircleX,
                    headline = "Your app left this run",
                    line = "$why ${mine.appealBlocked.ifBlank { "It can't be asked back now." }}",
                    action = "Put it in a new crew",
                    onAction = actions.onTakeSeat,
                )
            appeal == null ->
                CrewHero(
                    chip = "Your app is out",
                    tone = Tone.BAD,
                    icon = Lucide.CircleX,
                    headline = "Your app left this run",
                    line = "$why Nobody opens it now, and there's nothing more for you to open here.",
                    action = "Ask to get it back",
                    onAction = { actions.onAppeal(mine) },
                )
            appeal.waiting ->
                CrewHero(
                    chip = "Asked to get it back",
                    tone = Tone.WARN,
                    icon = Lucide.Hourglass,
                    headline = "The team is looking at it",
                    line =
                        "You'll get a notification when they answer, usually within a day. Until then you " +
                            "can't put an app in a new crew.",
                )
            else ->
                CrewHero(
                    chip = "Not taken back",
                    tone = Tone.BAD,
                    icon = Lucide.CircleX,
                    headline = "Your app stays out",
                    line =
                        if (appeal.state == "closed" || appeal.answer.isBlank()) {
                            "The run ended before the team answered."
                        } else {
                            "The team said: ${appeal.answer}"
                        },
                    action = "Put it in a new crew",
                    onAction = actions.onTakeSeat,
                )
        }
        CardNote(
            when {
                appeal == null && mine.canAppeal ->
                    "Something went wrong, like a broken phone? Tell the team what happened. If they say no, " +
                        "you can put it in a new crew."
                appeal != null && appeal.waiting -> "You wrote: ${appeal.text}"
                else -> seatOutLine(mine)
            },
        )
    }
}

/** Under the Left the run heading: why those rows have nothing to open. */
@Composable
internal fun LeftRunNote() {
    Text(
        "You don't need to open these",
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text3,
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 8.dp),
    )
}

/**
 * Run: an app that left. Faded, with nothing to open; from a day after it left, Uninstall, which
 * is Android's own prompt. Open all never reaches it: it is not on tonight's list.
 */
@Composable
internal fun LeftRow(
    app: CrewApp,
    seat: CrewSeat,
    place: TilePlace,
    actions: CrewActions,
) {
    val gone = outLongEnough(app.outAt)
    val line = if (gone) "Out of the run. You can uninstall it now." else "Out of the run. Nothing to open."
    CrewPageRow(app.packageName, app.label, AnnotatedString(line), place, icon = seat.icon, out = true, onOpen = {}) {
        if (gone) {
            RowPill("Uninstall", PillKind.GHOST) { actions.onUninstall(app.packageName) }
        } else {
            StatusChip("Out", Tone.QUIET)
        }
    }
}
