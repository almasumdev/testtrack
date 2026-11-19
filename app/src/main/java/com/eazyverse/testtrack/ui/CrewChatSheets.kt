package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
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
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.SendState
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

/** What a held-down message offers: Reply, Copy, and Delete on your own (Remove when it never sent). */
@Composable
internal fun ChatMessageSheet(
    line: ChatLine,
    title: String,
    canReply: Boolean,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    PickerSheet(title = title, onDismiss = onDismiss, skipHalf = true) { close ->
        Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
            if (canReply) SheetAction(Lucide.Reply, "Reply") { close(onReply) }
            if (line.text.isNotBlank()) SheetAction(Lucide.Copy, "Copy") { close(onCopy) }
            when {
                // Still on its way: it can't be called back, so nothing to offer until it lands or fails.
                !line.mine || line.send == SendState.SENDING -> Unit
                line.send == SendState.SENT -> SheetAction(Lucide.Trash2, "Delete", "Gone for everyone", danger = true) { close(onDelete) }
                else -> SheetAction(Lucide.Trash2, "Remove", "Not sent", danger = true) { close(onDelete) }
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: ImageVector,
    title: String,
    line: String? = null,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, danger = danger)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = chatStyle(16f, 20f), fontWeight = FontWeight.SemiBold, color = if (danger) t.bad else t.text)
            if (line != null) Text(line, style = chatStyle(12.5f, 16f), color = t.text3)
        }
    }
}
