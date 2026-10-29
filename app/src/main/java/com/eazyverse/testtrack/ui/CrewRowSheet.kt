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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Flag
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The sheet a crew row opens: what does not fit on the row itself. See CrewPageRows.kt.
 */

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
 * rarer one, Report (or It won't install, for an app members are asked about). In the one
 * [PickerSheet] frame, with the app's icon before its name.
 */
@Composable
internal fun CrewRowSheet(
    sheet: RowSheet,
    crewName: String,
    actions: CrewActions,
    onDismiss: () -> Unit,
) {
    val seat = sheet.seat
    val installed = actions.isInstalled(seat.packageName)

    PickerSheet(
        title = seat.appName,
        onDismiss = onDismiss,
        subtitle = "by ${seat.owner}, in $crewName",
        skipHalf = true,
        leading = { AppMark(seat.packageName, seat.appName, seat.icon, size = 44.dp) },
    ) { then ->
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            Text(
                "Sign-in for testers",
                Modifier.padding(top = 4.dp, bottom = 8.dp),
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
            // An update out: getting it is the main thing, opening the old copy the second.
            if (installed && app != null && olderHere(app, actions)) {
                Text(
                    updateLine(app, seat.owner),
                    Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tt.colors.text2,
                )
                Primary("Update on Google Play", Modifier.fillMaxWidth()) { then { actions.onUpdateApp(app) } }
                Spacer(Modifier.height(10.dp))
            }
            val notPlay = installed && !actions.fromPlay(seat.packageName)
            if (notPlay) {
                Text(
                    "This copy wasn't installed from Google Play, so opening it doesn't count. Uninstall it, then install it from Play.",
                    Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tt.colors.text2,
                )
            }
            when {
                !installed || notPlay -> Primary("Install on Play", Modifier.fillMaxWidth()) { then { actions.onInstall(seat) } }
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
                    SheetOption(
                        "It won't install",
                        "Say why. ${CrewRules.VOTES_TO_DECIDE} No answers send it back to ${seat.owner}.",
                        Tone.BAD,
                    ) { then { actions.onVote(seat, false) } }
                sheet.kind == SheetKind.ASKED -> Unit
                // During the run a report lets off only the night it is sent on; tomorrow the row
                // is owed again, and Report is back here if it is still broken.
                seat.reported ->
                    SheetOption(
                        "You reported it",
                        if (sheet.kind == SheetKind.INSTALL) {
                            "Its owner has what you wrote. It no longer counts against you."
                        } else {
                            "Its owner has what you wrote. Tonight doesn't count against you."
                        },
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
                            "It won't open or the sign-in fails. Tonight stops counting against you."
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
