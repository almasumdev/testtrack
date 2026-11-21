package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
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

/**
 * The looks of a row's own button: the night's main action, a step, or an answer. [PRIMARY] is the
 * theme's primary fill, the colour of [Primary], such as the request card's Check.
 */
enum class PillKind { SOLID, SOFT, GHOST, OK, PRIMARY }

/**
 * How big a [RowPill] is drawn. [ROW] is a crew row's 34dp pill with one line of 14sp words.
 * [SMALL] is the soft pill on My apps: 32dp, 13.5sp words on up to two lines, a 15dp icon, and
 * while busy a loader in the icon's place beside words that say what it is doing.
 */
enum class PillSize { ROW, SMALL }

@Composable
internal fun RowPill(
    label: String,
    kind: PillKind,
    /** Sent and not answered yet: a spinner, and nothing to tap. */
    busy: Boolean = false,
    modifier: Modifier = Modifier,
    /** A small icon before the word, in the pill's own text colour. */
    leading: ImageVector? = null,
    /** The leading icon's size: 13 on a row, 16 on the seat page's group pills, 15 when [size] is SMALL. */
    leadingSize: Dp? = null,
    shape: Shape = RoundedCornerShape(percent = 50),
    /** A small icon after the word, turned by [trailingTurn] degrees. */
    trailing: ImageVector? = null,
    trailingTurn: Float = 0f,
    size: PillSize = PillSize.ROW,
    /** False where the pill is only as big as it is drawn, with no 48dp touch area around it. */
    touchTarget: Boolean = true,
    /** The least it is drawn tall: 34dp on a row and 32dp when [size] is SMALL unless given. */
    minHeight: Dp? = null,
    onClick: () -> Unit,
) {
    val (bg, fg) =
        when (kind) {
            PillKind.SOLID -> Tt.colors.accent to Tt.colors.onAccent
            PillKind.SOFT -> Tt.colors.tile to Tt.colors.accentInk
            PillKind.GHOST -> Tt.colors.bg to Tt.colors.accentInk
            PillKind.OK -> Status.posted to Tt.colors.inkOn(Status.posted)
            PillKind.PRIMARY -> Tt.colors.primary to Tt.colors.onPrimary
        }
    val small = size == PillSize.SMALL
    // 34 to look at, 48 to touch, the way an icon button is built.
    Box(
        modifier
            .then(if (touchTarget) Modifier.minimumInteractiveComponentSize() else Modifier)
            .heightIn(min = minHeight ?: if (small) 32.dp else 34.dp)
            .clip(shape)
            .background(bg)
            .then(if (kind == PillKind.GHOST) Modifier.border(1.5.dp, Tt.colors.glassBorderStrong, shape) else Modifier)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (leading != null && !small) 12.dp else 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (small) {
            SmallPillContent(label, fg, busy, leading, leadingSize ?: 15.dp)
        } else {
            // The word stays, unseen, so the pill keeps its width while the spinner is in it.
            val ink = if (busy) fg.copy(alpha = 0f) else fg
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    Icon(leading, null, Modifier.size(leadingSize ?: 13.dp), tint = ink)
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                )
                if (trailing != null) {
                    Spacer(Modifier.width(4.dp))
                    Icon(trailing, null, Modifier.size(16.dp).rotate(trailingTurn), tint = ink)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = fg)
        }
    }
}

/** A [PillSize.SMALL] pill's inside: the icon, or a loader in its place while busy, then the words. */
@Composable
private fun SmallPillContent(
    label: String,
    ink: Color,
    busy: Boolean,
    leading: ImageVector?,
    leadingSize: Dp,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = ink)
            Spacer(Modifier.width(6.dp))
        } else if (leading != null) {
            Icon(leading, null, Modifier.size(leadingSize), tint = ink)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.Bold,
            color = ink,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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

/** One row of a crew's page. [trailing] is the pill or the chip; the chevron always follows. */
@Composable
internal fun CrewPageRow(
    packageName: String,
    title: String,
    line: AnnotatedString,
    onOpen: () -> Unit,
    /** Play's icon, drawn when the app is not on this phone. */
    icon: String = "",
    /** Out of the run: faded, and nothing to open, so no chevron and no tap. */
    out: Boolean = false,
    /** A bare number after the name, quiet skips of it; nothing is drawn at 0. */
    mark: Int = 0,
    trailing: @Composable () -> Unit,
) {
    HairlineRow(
        leading = { Box(if (out) Modifier.alpha(0.5f) else Modifier) { AppMark(packageName, title, icon, size = 52.dp) } },
        title = title,
        note = line,
        titleTone = if (out) Tt.colors.text3 else Tt.colors.text,
        titleMark = if (mark > 0) ({ QuietMark(mark) }) else null,
        onClick = if (out) null else onOpen,
        trailing = {
            trailing()
            if (!out) Icon(Lucide.ChevronRight, null, Modifier.padding(start = 2.dp).size(18.dp), tint = Tt.colors.text4)
        },
    )
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
    actions: CrewActions,
    onSheet: (RowSheet) -> Unit,
) {
    val installed = actions.isInstalled(seat.packageName)
    CrewPageRow(seat.packageName, seat.appName, ownerLine(seat.owner, seat.notes.isNotBlank()), icon = seat.icon, onOpen = {
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
