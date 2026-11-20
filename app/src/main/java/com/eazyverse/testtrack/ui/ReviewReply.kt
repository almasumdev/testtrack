package com.eazyverse.testtrack.ui

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare

/*
 * The reviewer's answer in a dispute: once refused, they answer once with words, a screenshot, or
 * both, from a sheet. The members deciding see both sides.
 */

/**
 * The reviewer's one answer to a refusal, in a sheet over the thread: a few words, a screenshot, or
 * both. The members deciding and the developer see it.
 */
@Composable
internal fun AnswerSheet(
    onSend: (String, Uri?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var picture by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberImagePicker { uri -> if (uri != null) picture = uri }
    PickerSheet(
        title = "Answer the developer",
        onDismiss = onDismiss,
        skipHalf = true,
        fitMax = 0.9f,
        leading = { IconTile(Lucide.MessageSquare, 36.dp, corner = 12.dp, glyph = 18.dp, tint = Tone.ACCENT.ink()) },
    ) { close ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
            InfoLine("You can answer once. The ${FeedRules.JURORS.words()} members and the developer see it.")
            Spacer(Modifier.height(18.dp))
            OutlinedField(
                value = text,
                onValueChange = { text = it.take(FeedRules.REFUSE_MAX) },
                label = "Your answer",
                placeholder = "My feedback was sent on Google Play.",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                imeAction = ImeAction.Default,
                maxLength = FeedRules.REFUSE_MAX,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RowPill(
                    if (picture == null) "Add a screenshot" else "Change the screenshot",
                    PillKind.SOFT,
                    leading = Lucide.ImagePlus,
                    leadingSize = 15.dp,
                ) { pick() }
                if (picture != null) RowPill("Remove", PillKind.GHOST) { picture = null }
            }
            Spacer(Modifier.height(16.dp))
            Primary("Send answer", enabled = text.isNotBlank() || picture != null) {
                val words = text.trim()
                val shot = picture
                close { onSend(words, shot) }
            }
        }
    }
}
