package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ArrowDownToLine
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCw
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * An optional update, as the header offers it: the only way one is ever offered.
 *
 * Nothing pops up and nothing starts by itself. [AVAILABLE] is a round button that starts Play's
 * background download when tapped; once that download is on the phone it becomes [READY], a
 * Restart pill that applies it. A forced update is not this: it is the full screen in
 * [ForceUpdateScreen].
 */
class UpdateOffer(
    val state: State,
    val onTap: () -> Unit,
) {
    enum class State { AVAILABLE, READY }
}

@Composable
internal fun UpdateChip(offer: UpdateOffer) {
    val t = Tt.colors
    if (offer.state == UpdateOffer.State.READY) {
        Row(
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(t.accent)
                .clickable(onClickLabel = "Restart to update", role = Role.Button, onClick = offer.onTap)
                .padding(start = 11.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.RotateCw, null, Modifier.size(16.dp), tint = t.onAccent)
            Spacer(Modifier.width(7.dp))
            Text("Restart", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = t.onAccent)
        }
        return
    }
    // A plain round button with a gold dot: there is something new, and nothing is asked of you.
    Box {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(t.glass)
                .border(1.dp, t.glassBorder, CircleShape)
                .clickable(onClickLabel = "Update TestTrack", role = Role.Button, onClick = offer.onTap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Lucide.ArrowDownToLine, "Update available", Modifier.size(19.dp), tint = t.accentInk)
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 1.dp, y = (-1).dp)
                .size(11.dp)
                .clip(CircleShape)
                .background(t.gold)
                .border(2.dp, t.glass, CircleShape),
        )
    }
}
