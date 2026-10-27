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
 * Apps opened tonight whose picture is not on the server, so they have not counted yet.
 *
 * Most are only waiting to send and go by themselves; those say so and ask nothing. One whose
 * picture failed (a blank screen, a camera that stopped) asks for one: open the app again for a
 * fresh picture, or pick one from the phone, which its owner sees as added by hand. The seconds
 * already measured stand either way; only the picture is missing.
 */

@Composable
internal fun PicturesDueCard(
    apps: List<CrewApp>,
    actions: CrewActions,
) {
    val due = apps.filter { it.pictureDue }
    if (due.isEmpty()) return
    val stuck = due.filterNot { actions.sendingPicture(it.id) }
    Box(Modifier.padding(top = 10.dp)) {
        CrewCard {
            if (stuck.isEmpty()) {
                StatusChip("Sending ${due.size} ${if (due.size == 1) "picture" else "pictures"}", Tone.ACCENT)
                Text(
                    "Opened and timed. ${if (due.size == 1) "It counts" else "They count"} once the picture reaches your Drive.",
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Tt.colors.text3,
                )
                return@CrewCard
            }
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
    }
}
