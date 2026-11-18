package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** Quiet recovery below the primary action, available only after starting a review. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewRecoveryHelp(
    job: ReviewJob,
    busy: Boolean,
    onReport: (() -> Unit)?,
    onGiveUp: (() -> Unit)?,
) {
    val (prompt, label, action) =
        when {
            job.canReportInstall && onReport != null -> Triple("Can't install?", "Report a problem", onReport)
            job.canGiveUp && onGiveUp != null -> Triple("Can't finish?", "Give up review", onGiveUp)
            else -> return
        }
    FlowRow(
        Modifier.fillMaxWidth().padding(top = ReviewLineGap),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(
            prompt,
            Modifier.align(Alignment.CenterVertically),
            style = MaterialTheme.typography.bodySmall,
            color = Tt.colors.text3,
        )
        TextButton(
            onClick = action,
            enabled = !busy,
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Tt.colors.accentInk, disabledContentColor = Tt.colors.text4),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, textDecoration = TextDecoration.Underline)
        }
    }
}
