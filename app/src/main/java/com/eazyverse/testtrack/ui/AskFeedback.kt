package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * What a request asks of each reviewer, on the Ask page: two check boxes, written and detailed,
 * and with detailed the ready-written reviews an owner may give. A star rating is always part of
 * it and has no box. Each box raises the least the request may pay (FeedRules.Feedback).
 */

@Composable
internal fun AskFeedbackChoice(
    feedback: FeedRules.Feedback,
    enabled: Boolean,
    onWritten: (Boolean) -> Unit,
    onDetailed: (Boolean) -> Unit,
) {
    Text("What reviewers write", style = MaterialTheme.typography.titleSmall, color = Tt.colors.text)
    Spacer(Modifier.height(4.dp))
    Text(
        "Every review has a star rating. Tick a box to ask for written feedback as well.",
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text2,
    )
    Spacer(Modifier.height(6.dp))
    FeedbackBox(FeedRules.Feedback.WRITTEN, feedback != FeedRules.Feedback.STARS, enabled, onWritten)
    FeedbackBox(FeedRules.Feedback.DETAILED, feedback == FeedRules.Feedback.DETAILED, enabled, onDetailed)
}

@Composable
private fun FeedbackBox(
    kind: FeedRules.Feedback,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(checkedColor = Tt.colors.accent, uncheckedColor = Tt.colors.glassBorderStrong),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(kind.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
            Text(
                "At least ${kind.chars} characters. From ${kind.least} tix a review.",
                style = MaterialTheme.typography.bodySmall,
                color = Tt.colors.text2,
            )
        }
    }
}

/**
 * The ready-written reviews of a detailed request: optional, one per review bought at most. Each
 * goes to one reviewer only, who may use it, change it or write their own, and the page says so.
 */
@Composable
internal fun AskReadyTexts(
    texts: List<String>,
    max: Int,
    enabled: Boolean,
    onAdd: () -> Unit,
    onEdit: (Int, String) -> Unit,
    onRemove: (Int) -> Unit,
) {
    val least = FeedRules.Feedback.DETAILED.chars
    Text("Ready-written reviews (optional)", style = MaterialTheme.typography.titleSmall, color = Tt.colors.text)
    Spacer(Modifier.height(8.dp))
    Notice(Tt.colors.accentInk, Lucide.Info) {
        Text(
            "Reviewers don't have to use these. Each one goes to one reviewer only, who can use it, " +
                "change it, or write their own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    texts.forEachIndexed { i, text ->
        Spacer(Modifier.height(12.dp))
        val short = text.isNotBlank() && plainLength(text) < least
        DialogField(
            value = text,
            onValueChange = { onEdit(i, it.take(FeedRules.WRITTEN_MAX)) },
            label = "Ready review ${i + 1}",
            placeholder = "How you would like a review to read, at least $least characters.",
            error = if (short) "${plainLength(text)} of at least $least characters." else null,
            supporting = if (short) null else "At least $least characters.",
            singleLine = false,
            minHeight = 120.dp,
            maxLength = FeedRules.WRITTEN_MAX,
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Spacer(Modifier.weight(1f))
            RowPill("Remove", PillKind.GHOST, onClick = { if (enabled) onRemove(i) })
        }
    }
    Spacer(Modifier.height(12.dp))
    if (texts.size > max) {
        Text(
            "Remove ${texts.size - max}. There can be one for each review you asked for.",
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.bad,
        )
    } else if (texts.size < max) {
        RowPill("Add a ready review (${texts.size} of $max)", PillKind.SOFT, onClick = { if (enabled) onAdd() })
    } else {
        Text("One for each review you asked for.", style = MaterialTheme.typography.bodySmall, color = Tt.colors.text3)
    }
}

/** Characters as the server counts them: spaces between words count once, ends not at all. */
internal fun plainLength(text: String): Int =
    text
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" ")
        .length
