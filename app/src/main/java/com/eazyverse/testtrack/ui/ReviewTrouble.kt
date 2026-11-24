package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Quiet recovery under the primary action, available only after starting a review: a question
 * and the way out, underlined, centred in a 44dp row, as "Refused by mistake?" on the Check page.
 */
@Composable
internal fun ReviewRecoveryHelp(
    job: ReviewJob,
    busy: Boolean,
    onReport: (() -> Unit)?,
    onGiveUp: (() -> Unit)?,
    onUpdate: (() -> Unit)? = null,
    onCancelUpdate: (() -> Unit)? = null,
) {
    val (prompt, label, action) =
        when {
            job.updating && onCancelUpdate != null -> Triple("Changed your mind?", "Keep what I sent", onCancelUpdate)
            job.canUpdate && onUpdate != null ->
                Triple(if (job.live && !job.noBox) "Review not showing yet?" else "Wrong screenshot?", "Send an update", onUpdate)
            job.canReportInstall && onReport != null -> Triple("Can't install?", "Report a problem", onReport)
            job.canGiveUp && onGiveUp != null -> Triple("Can't finish?", "Give up review", onGiveUp)
            else -> return
        }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, style = tileStyle(13.5f, 18f, FontWeight.Normal), color = Tt.colors.text3)
        Spacer(Modifier.width(4.dp))
        InlineAction(label, enabled = !busy, underline = true, onClick = action)
    }
}
