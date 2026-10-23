package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch

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
    Box(
        Modifier
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

/** "by Habib", with "· Sign-in" after it when the app's sheet has one to copy. */
@Composable
internal fun ownerLine(
    owner: String,
    signIn: Boolean,
    tail: String = "",
) = buildAnnotatedString {
    append("by $owner")
    if (signIn) {
        append(" · ")
        withStyle(SpanStyle(color = Tt.colors.accentInk, fontWeight = FontWeight.SemiBold)) { append("Sign-in") }
    }
    if (tail.isNotBlank()) append(" · $tail")
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
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(packageName, title, icon, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Icon(Lucide.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp), tint = t.text4)
    }
}

/** What a row's sheet is about, which decides its main button and its last option. */
enum class SheetKind { INSTALL, ASKED, TONIGHT, DONE }

/** The app a row's sheet is open for. */
data class RowSheet(
    val seat: CrewSeat,
    val kind: SheetKind,
    val app: CrewApp? = null,
)

/**
 * One app's sheet: who brought it, its sign-in for testers, the main thing to do with it, and the
 * rarer one, Report (or It won't install, for an app members are asked about).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrewRowSheet(
    sheet: RowSheet,
    crewName: String,
    actions: CrewActions,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val seat = sheet.seat
    val installed = actions.isInstalled(seat.packageName)

    fun then(next: () -> Unit) {
        scope.launch {
            state.hide()
            onDismiss()
            next()
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Gutter)
                .navigationBarsPadding()
                .padding(bottom = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppMark(seat.packageName, seat.appName, seat.icon, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(seat.appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("by ${seat.owner} · in $crewName", style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
                }
            }
            Text(
                "Sign-in for testers",
                Modifier.padding(top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Tt.colors.text4,
            )
            TintBox {
                if (seat.notes.isNotBlank()) {
                    SignInDetails(seat.notes)
                } else {
                    Text(
                        "No sign-in needed. It opens straight in.",
                        Modifier.padding(vertical = 6.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Tt.colors.text4,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            val app = sheet.app
            when {
                !installed -> Primary("Install on Play", Modifier.fillMaxWidth()) { then { actions.onInstall(seat) } }
                app != null && sheet.kind == SheetKind.TONIGHT ->
                    Primary(
                        "Open ${seat.appName}",
                        Modifier.fillMaxWidth(),
                    ) { then { actions.onOpen(app) } }
                app != null && sheet.kind == SheetKind.DONE ->
                    Secondary("Open ${seat.appName} again", Modifier.fillMaxWidth()) {
                        then { actions.onOpen(app) }
                    }
                else -> Secondary("Open its Play page", Modifier.fillMaxWidth()) { then { actions.onInstall(seat) } }
            }
            Spacer(Modifier.height(12.dp))
            when {
                sheet.kind == SheetKind.ASKED && seat.myVote == null ->
                    SheetOption("It won't install", "Say why. Two No answers send it back to ${seat.owner}.", Tone.BAD) {
                        then { actions.onVote(seat, false) }
                    }
                sheet.kind == SheetKind.ASKED -> Unit
                seat.reported ->
                    SheetOption(
                        "You reported it",
                        "Its owner has what you wrote. It no longer counts against you.",
                        Tone.WARN,
                        null,
                    )
                else ->
                    SheetOption(
                        "Report a problem",
                        if (sheet.kind ==
                            SheetKind.INSTALL
                        ) {
                            "It won't install, crashes, or the sign-in fails"
                        } else {
                            "It won't open or the sign-in fails. It stops counting against you."
                        },
                        Tone.BAD,
                    ) { then { actions.onReport(seat) } }
            }
        }
    }
}

/** The rarer thing a sheet offers, as one row with its icon and what it does. */
@Composable
private fun SheetOption(
    title: String,
    line: String,
    tone: Tone,
    onClick: (() -> Unit)?,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Tt.colors.glass)
            .border(1.dp, Tt.colors.glassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(tone.soft()), contentAlignment = Alignment.Center) {
            Icon(Lucide.Flag, null, Modifier.size(17.dp), tint = tone.ink())
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = tone.ink(),
            )
            Text(line, style = MaterialTheme.typography.bodySmall, color = Tt.colors.text4)
        }
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
            seat.myVote != null -> "${seat.yes} Yes · ${seat.no} No"
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
    CrewPageRow(
        app.packageName,
        app.label,
        ownerLine(app.owner, seat.notes.isNotBlank(), if (installed) "" else "not on your phone"),
        place,
        onOpen = { onSheet(RowSheet(seat, SheetKind.TONIGHT, app)) },
        icon = seat.icon,
    ) {
        if (installed) {
            RowPill(
                "Open",
                PillKind.SOLID,
            ) { actions.onOpen(app) }
        } else {
            RowPill("Install", PillKind.SOFT) { actions.onInstall(seat) }
        }
    }
}

/** Run: an app already opened tonight, or reported. Only a chip; the row still opens its sheet. */
@Composable
internal fun DoneRow(
    app: CrewApp,
    seat: CrewSeat,
    place: TilePlace,
    onSheet: (RowSheet) -> Unit,
) {
    CrewPageRow(app.packageName, app.label, ownerLine(app.owner, false), place, icon = seat.icon, onOpen = {
        onSheet(RowSheet(seat, SheetKind.DONE, app))
    }) {
        if (app.reported) StatusChip("Reported", Tone.WARN) else StatusChip("Opened", Tone.OK)
    }
}
