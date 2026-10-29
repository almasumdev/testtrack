package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Apps opened whose picture is not on the server, so they have not counted yet.
 *
 * Most are only waiting to send and go by themselves; those say so and ask nothing, unless one
 * has waited more than three hours, which turns the card red until it goes. One whose picture
 * failed tonight (a blank screen, a camera that stopped) asks for one: open the app again for a
 * fresh picture, or pick one from the phone, which its owner sees as added by hand. The seconds
 * already measured stand either way; only the picture is missing.
 */

@Composable
internal fun PicturesDueCard(
    apps: List<CrewApp>,
    actions: CrewActions,
) {
    val due = apps.filter { it.pictureDue }
    val stuck = due.filterNot { actions.sendingPicture(it) }
    // Tonight's waiting to send, and any app with an older night's picture still on the phone.
    val sending = apps.filter { actions.sendingPicture(it) || actions.waitingPicture(it.id) }
    if (stuck.isEmpty() && sending.isEmpty()) return
    Box(Modifier.padding(top = 10.dp)) {
        CrewCard {
            if (stuck.isNotEmpty()) Missing(stuck, actions)
            if (sending.isNotEmpty()) {
                if (stuck.isNotEmpty()) Spacer(Modifier.height(16.dp))
                Sending(sending, actions)
            }
        }
    }
}

@Composable
private fun Sending(
    sending: List<CrewApp>,
    actions: CrewActions,
) {
    val why = actions.picturesStuck
    val late = sending.any { actions.latePicture(it.id) }
    StatusChip(
        "Sending ${sending.size} ${if (sending.size == 1) "picture" else "pictures"}",
        when {
            late -> Tone.BAD
            why != null -> Tone.WARN
            else -> Tone.ACCENT
        },
    )
    Text(
        when {
            late && why != null -> "Waiting more than 3 hours. $why"
            late -> "Waiting more than 3 hours. It counts once it reaches your Drive, so check you're online."
            why != null -> why
            else -> "Opened and timed. ${if (sending.size == 1) "It counts" else "They count"} once the picture reaches your Drive."
        },
        Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.bodySmall,
        color = if (late) Tone.BAD.ink() else Tt.colors.text3,
    )
    // The picture is safe on the phone and goes by itself; this is for not waiting.
    if (why != null || late) {
        Secondary("Try again", Modifier.fillMaxWidth().padding(top = 10.dp)) { actions.onRetryPictures() }
    }
}

@Composable
private fun Missing(
    stuck: List<CrewApp>,
    actions: CrewActions,
) {
    StatusChip("${stuck.size} ${if (stuck.size == 1) "picture" else "pictures"} missing", Tone.WARN)
    stuck.forEachIndexed { index, app ->
        if (index > 0) Spacer(Modifier.height(14.dp))
        Text(
            "${app.label} needs a picture",
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "You opened it, but its picture didn't save. Take one now, or pick one from your phone, so tonight counts.",
            Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text3,
        )
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Primary("Open it again", Modifier.fillMaxWidth(), busy = actions.busySeat == app.id) { actions.onOpen(app) }
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Column(Modifier.weight(1f)) {
                Secondary("Pick a picture", Modifier.fillMaxWidth()) { actions.onPickPicture(app) }
            }
        }
    }
    CardNote("A picture you pick shows to its owner as added by hand.")
}
