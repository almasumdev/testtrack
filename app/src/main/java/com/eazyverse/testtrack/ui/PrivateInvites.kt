package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Eye
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Play
import com.composables.icons.lucide.Ticket
import com.eazyverse.testtrack.viewmodel.PrivateTest

/*
 * An owner asked the reader to test their app: a row over every Crew tab until it is answered,
 * and the sheet that answers it. The app's name and icon are all an invite shows; its package and
 * sign-in come with yes.
 */

/** The invites waiting for an answer, one row each, over whichever tab shows. */
@Composable
internal fun PrivateInviteRows(
    invites: List<PrivateTest>,
    onOpen: (PrivateTest) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (invites.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        invites.forEach { invite ->
            AskRow(
                "Invite from ${invite.ownerName}",
                "Test ${invite.appName}. No tix, no rules.",
                { onOpen(invite) },
                leading = { AppMark(invite.packageName, invite.appName, invite.icon, size = 40.dp) },
                trailing = { RowPill("See", PillKind.SOFT, touchTarget = false) { onOpen(invite) } },
            )
        }
    }
}

/** Accept or decline one invite. [onAccept] and [onDecline] get a way to close the sheet. */
@Composable
internal fun PrivateInviteSheet(
    invite: PrivateTest,
    busy: Boolean,
    onAccept: (close: () -> Unit) -> Unit,
    onDecline: (close: () -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet(
        title = "Test ${invite.appName}?",
        onDismiss = onDismiss,
        subtitle = "From ${invite.ownerName}",
        subtitleSingleLine = true,
        skipHalf = true,
        leading = { AppMark(invite.packageName, invite.appName, invite.icon, size = 36.dp) },
    ) { close ->
        Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SheetLine(Lucide.Play, "", "Open it from TestTrack when you like.")
                SheetLine(Lucide.Eye, "Only ${invite.ownerName} sees", " the nights you open it.")
                SheetLine(Lucide.Ticket, "", "No tix and no rules. Stop any time.")
                SheetLine(Lucide.Calendar, "", "Ends when ${invite.crewName} ends.")
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Secondary("Decline", Modifier.weight(1f), enabled = !busy) { onDecline { close {} } }
                Primary("Accept", Modifier.weight(1f), busy = busy) { onAccept { close {} } }
            }
        }
    }
}
