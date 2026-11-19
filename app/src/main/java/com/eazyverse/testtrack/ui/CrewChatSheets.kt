package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.BellOff
import com.composables.icons.lucide.Copy
import com.composables.icons.lucide.EllipsisVertical
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Reply
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.domain.ChatPicks
import com.eazyverse.testtrack.ui.theme.Tt

/** The bar's ⋮: mute or unmute, and the crew's members when it has a page for them. */
@Composable
internal fun ChatMenu(
    view: ChatView,
    actions: ChatActions,
) {
    val t = Tt.colors
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { open = true },
            Modifier.clip(CircleShape).background(if (open) t.glass2 else t.glass2.copy(alpha = 0f)),
        ) { Icon(Lucide.EllipsisVertical, "Chat options", Modifier.size(20.dp), tint = t.text2) }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = t.sheet,
            shape = RoundedCornerShape(14.dp),
        ) {
            MenuRow(
                if (view.muted) Lucide.Bell else Lucide.BellOff,
                if (view.muted) "Unmute chat" else "Mute chat",
                if (view.muted) null else "Mentions still reach you",
            ) {
                open = false
                actions.onMute()
            }
            if (view.members) {
                MenuRow(Lucide.Users, "Members") {
                    open = false
                    actions.onMembers()
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    line: String? = null,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .width(214.dp)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(19.dp), tint = t.text2)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = chatStyle(15f, 20f), fontWeight = FontWeight.Medium, color = t.text)
            if (line != null) Text(line, style = chatStyle(12f, 16f), color = t.text3)
        }
    }
}

/**
 * The bar while messages are picked, in place of the page's own: an X to let them go, how many are
 * picked, and what can be done with them. Reply is for one message; Copy and Delete work on
 * many (Delete only when every one is yours, and "Remove" when none of them ever sent).
 */
@Composable
internal fun PickBar(
    picks: ChatPicks,
    actions: ChatActions,
) {
    val t = Tt.colors
    PageBar("${picks.count} selected", actions.onClearSelection, close = true) {
        if (picks.reply) PickButton(Lucide.Reply, "Reply", t.text2, actions.onReplyPicked)
        if (picks.copy) PickButton(Lucide.Copy, "Copy", t.text2, actions.onCopyPicked)
        if (picks.delete) PickButton(Lucide.Trash2, if (picks.removeOnly) "Remove" else "Delete", t.bad, actions.onDeletePicked)
    }
}

@Composable
private fun PickButton(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) { Icon(icon, label, Modifier.size(20.dp), tint = tint) }
}
