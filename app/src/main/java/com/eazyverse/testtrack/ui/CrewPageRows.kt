package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The rows of a crew's page, all of one shape: icon, title, one short line, the action you do
 * often (or a chip saying where it stands), then a chevron. Tapping the pill does it; tapping the
 * rest of the row opens that app's sheet, which holds what does not fit: its sign-in, and Report.
 * The line under the name says when the sheet has a sign-in, so nothing in it is a surprise.
 */

internal fun Modifier.clickableRole(
    label: String,
    onClick: () -> Unit,
): Modifier = semantics(mergeDescendants = true) { contentDescription = label }.clickable(onClick = onClick)

/** The three looks of a row's own button: the night's main action, a step, or an answer. */
enum class PillKind { SOLID, SOFT, GHOST, OK }

@Composable
internal fun RowPill(
    label: String,
    kind: PillKind,
    /** Sent and not answered yet: a spinner in place of the word, and nothing to tap. */
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(percent = 50)
    val (bg, fg) =
        when (kind) {
            PillKind.SOLID -> Tt.colors.accent to Tt.colors.onAccent
            PillKind.SOFT -> Tt.colors.tile to Tt.colors.accentInk
            PillKind.GHOST -> Tt.colors.glass to Tt.colors.accentInk
            PillKind.OK -> Status.posted to Tt.colors.inkOn(Status.posted)
        }
    // 34 to look at, 48 to touch, the way an icon button is built.
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 34.dp)
            .clip(shape)
            .background(bg)
            .then(if (kind == PillKind.GHOST) Modifier.border(1.5.dp, Tt.colors.glassBorderStrong, shape) else Modifier)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The word stays, unseen, so the pill keeps its width while the spinner is in it.
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = if (busy) fg.copy(alpha = 0f) else fg,
            maxLines = 1,
        )
        if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
    }
}

/** "by Habib", with ", has a sign-in" after it when the app's sheet has one to copy. */
@Composable
internal fun ownerLine(
    owner: String,
    signIn: Boolean,
    tail: String = "",
) = buildAnnotatedString {
    append("by $owner")
    if (signIn) {
        append(", ")
        withStyle(SpanStyle(color = Tt.colors.accentInk, fontWeight = FontWeight.SemiBold)) { append("has a sign-in") }
    }
    if (tail.isNotBlank()) append(", $tail")
}

/** A picture of the app has waited more than 3 hours to send: said on its row in red until it goes. */
@Composable
private fun lateLine() =
    buildAnnotatedString {
        withStyle(SpanStyle(color = Tone.BAD.ink(), fontWeight = FontWeight.SemiBold)) { append("Picture waiting over 3 hours") }
    }

/** One row of a crew's page. [trailing] is the pill or the chip; the chevron always follows. */
@Composable
internal fun CrewPageRow(
    packageName: String,
    title: String,
    line: androidx.compose.ui.text.AnnotatedString,
    place: TilePlace,
    onOpen: () -> Unit,
    /** Play's icon, drawn when the app is not on this phone. */
    icon: String = "",
    /** Out of the run: faded, and nothing to open, so no chevron and no tap. */
    out: Boolean = false,
    /** A bare number after the name, quiet skips of it; nothing is drawn at 0. */
    mark: Int = 0,
    trailing: @Composable () -> Unit,
) {
    val t = Tt.colors
    val shape = place.shape()
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .clickable(enabled = !out, onClick = onOpen)
            .padding(start = 12.dp, end = if (out) 12.dp else 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(if (out) Modifier.alpha(0.5f) else Modifier) { AppMark(packageName, title, icon, size = 40.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f, fill = false),
                    color = if (out) t.text3 else Color.Unspecified,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mark > 0) QuietMark(mark)
            }
            Text(
                line,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
        if (!out) Icon(Lucide.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp), tint = t.text4)
    }
}

/** The bare count after an app's name: quiet skips of it. Grey and small, and never explained. */
@Composable
private fun QuietMark(count: Int) {
    Box(
        Modifier
            .padding(start = 6.dp)
            .heightIn(min = 17.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(Tt.colors.glass2)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 14.sp),
            fontWeight = FontWeight.Bold,
            color = Tt.colors.text4,
        )
    }
}

// ------------------------------------------------------------------ the rows, by section

/** Lobby: an app to install before day 1. */
@Composable
internal fun InstallRow(
    seat: CrewSeat,
    place: TilePlace,
    actions: CrewActions,
    onSheet: (RowSheet) -> Unit,
) {
    val installed = actions.isInstalled(seat.packageName)
    CrewPageRow(seat.packageName, seat.appName, ownerLine(seat.owner, seat.notes.isNotBlank()), place, icon = seat.icon, onOpen = {
        onSheet(RowSheet(seat, SheetKind.INSTALL))
    }) {
        when {
            seat.reported -> StatusChip("Reported", Tone.WARN)
            seat.state == SeatState.CHECKING -> StatusChip("Being checked", Tone.QUIET)
            installed -> StatusChip("Installed", Tone.OK)
            else -> RowPill("Install", PillKind.SOFT) { actions.onInstall(seat) }
        }
    }
}

/** Lobby: an app Play could not check, which this account is asked to try. Its row follows the steps. */
@Composable
internal fun AskedRow(
    seat: CrewSeat,
    place: TilePlace,
    actions: CrewActions,
    onSheet: (RowSheet) -> Unit,
) {
    val installed = actions.isInstalled(seat.packageName)
    val tail =
        when {
            seat.myVote != null -> "${seat.yes} Yes and ${seat.no} No"
            installed -> "on your phone"
            else -> if (seat.yes > 0) "${seat.yes} Yes so far" else "nobody has answered yet"
        }
    CrewPageRow(
        seat.packageName,
        seat.appName,
        ownerLine(seat.owner, seat.notes.isNotBlank() && !installed, tail),
        place,
        onOpen = { onSheet(RowSheet(seat, SheetKind.ASKED)) },
        icon = seat.icon,
    ) {
        when {
            seat.myVote == true -> StatusChip("You said Yes", Tone.OK)
            seat.myVote == false -> StatusChip("You said No", Tone.WARN)
            installed -> RowPill("It installed", PillKind.OK, busy = actions.busySeat == seat.id) { actions.onVote(seat, true) }
            else -> RowPill("Install", PillKind.SOFT) { actions.onInstall(seat) }
        }
    }
}

/** Run: an app owed tonight. Open if it is on the phone, Install first if it is not. */
@Composable
internal fun TonightRow(
    app: CrewApp,
    seat: CrewSeat,
    place: TilePlace,
    actions: CrewActions,
    onSheet: (RowSheet) -> Unit,
) {
    val installed = actions.isInstalled(app.packageName)
    // A copy Play did not put here never counts: installing it from Play is the way out.
    val notPlay = installed && !actions.fromPlay(app.packageName)
    // Older than the owner's newest: still counts for its 24 hours, then only once updated.
    val old = olderHere(app, actions)
    val note =
        when {
            !installed -> "not on your phone"
            notPlay -> "Install it from Play"
            old && app.updateRequired -> "update needed"
            old -> updateBy(app)?.let { "update by $it" } ?: "update out"
            app.feedbackLate -> "feedback late"
            else -> ""
        }
    CrewPageRow(
        app.packageName,
        app.label,
        // An update out says only that, with its deadline, so the line is not cut: whose it is
        // is in the row's sheet.
        when {
            actions.latePicture(app.id) -> lateLine()
            notPlay || old -> AnnotatedString(note.replaceFirstChar(Char::uppercase))
            else -> ownerLine(app.owner, seat.notes.isNotBlank(), note)
        },
        place,
        onOpen = { onSheet(RowSheet(seat, SheetKind.TONIGHT, app)) },
        icon = seat.icon,
        mark = app.skips,
    ) {
        when {
            !installed -> RowPill("Install", PillKind.SOFT) { actions.onInstall(seat) }
            notPlay -> RowPill("Install", PillKind.SOLID) { actions.onInstall(seat) }
            // From the moment the owner tells the crew, as the notification says: Update is the
            // button, soft while the old copy still counts and solid once it no longer does. Open
            // is still in the row's sheet for those 24 hours.
            old -> RowPill("Update", if (app.updateRequired) PillKind.SOLID else PillKind.SOFT) { actions.onUpdateApp(app) }
            app.feedbackLate -> RowPill("Feedback", PillKind.SOLID) { actions.onFeedback(app) }
            else -> RowPill("Open", PillKind.SOLID) { actions.onOpen(app) }
        }
    }
}

/** Run: an app already opened tonight, or reported. Only a chip; the row still opens its sheet. */
@Composable
internal fun DoneRow(
    app: CrewApp,
    seat: CrewSeat,
    place: TilePlace,
    actions: CrewActions? = null,
    onSheet: (RowSheet) -> Unit,
) {
    // Opened tonight on the old copy: the update is still to do before the 24 hours run out.
    val old = actions != null && actions.isInstalled(app.packageName) && olderHere(app, actions)
    // A copy Play did not put here does not count on any night, so the row says how to fix it.
    val notPlay = actions != null && actions.isInstalled(app.packageName) && !actions.fromPlay(app.packageName)
    val note = if (old) (if (app.updateRequired) "update needed" else updateBy(app)?.let { "update by $it" } ?: "update out") else ""
    val line =
        when {
            actions?.latePicture(app.id) == true -> lateLine()
            notPlay -> AnnotatedString("Install it from Play")
            old -> AnnotatedString(note.replaceFirstChar(Char::uppercase))
            else -> ownerLine(app.owner, false)
        }
    CrewPageRow(app.packageName, app.label, line, place, icon = seat.icon, onOpen = {
        onSheet(RowSheet(seat, SheetKind.DONE, app))
    }) {
        when {
            app.reported -> StatusChip("Reported", Tone.WARN)
            notPlay && actions != null -> RowPill("Install", PillKind.SOFT) { actions.onInstall(seat) }
            old && actions != null -> RowPill("Update", PillKind.SOFT) { actions.onUpdateApp(app) }
            else -> StatusChip("Opened", Tone.OK)
        }
    }
}
