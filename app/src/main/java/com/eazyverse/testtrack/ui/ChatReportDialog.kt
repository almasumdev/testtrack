package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.ChatReport
import com.eazyverse.testtrack.domain.ChatReportReason
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Report a chat message or a person: pick why, say more ("Something else" needs a sentence, the
 * rest may have one), and when it is a message, block its author too. Nothing here tells the
 * member who reported them. [name] is the member; a person report ([person]) is titled by it.
 */
@Composable
internal fun ChatReportDialog(
    name: String,
    person: Boolean,
    /** Offers "Block {name} too": only for a message, since a person has their own Block. */
    offerBlock: Boolean,
    onSend: (reason: ChatReportReason, note: String, alsoBlock: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<ChatReportReason?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var alsoBlock by rememberSaveable { mutableStateOf(false) }
    val other = reason == ChatReportReason.OTHER
    Ask(
        title = if (person) "Report $name" else "Report this message",
        body = "The team reads every report, usually within a day. The member isn't told who reported them.",
        confirm = "Report",
        confirmEnabled = ChatReport.ready(reason, note),
        onConfirm = { reason?.let { onSend(it, ChatReport.note(note), offerBlock && alsoBlock) } },
        onDismiss = onDismiss,
    ) {
        Column(Modifier.selectableGroup()) {
            ChatReportReason.entries.forEach { ReasonRow(it.label, it == reason) { reason = it } }
        }
        if (reason != null) {
            Spacer(Modifier.height(10.dp))
            OutlinedField(
                value = note,
                onValueChange = { note = it.take(ChatReport.NOTE_MAX) },
                label = if (other) "What happened" else "Add a note (optional)",
                singleLine = false,
                minLines = if (other) 3 else 2,
                maxLines = 6,
                maxLength = ChatReport.NOTE_MAX,
                imeAction = ImeAction.Default,
            )
        }
        if (offerBlock) {
            Spacer(Modifier.height(4.dp))
            AskCheck("Block $name too", alsoBlock) { alsoBlock = it }
        }
    }
}

/** One reason as a radio line, 48dp tall, the whole line the tap target. */
@Composable
private fun ReasonRow(
    label: String,
    selected: Boolean,
    onPick: () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onPick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = t.accent, unselectedColor = t.text4),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, style = TextStyles.DialogBody, color = if (selected) t.text else t.text2)
    }
}
