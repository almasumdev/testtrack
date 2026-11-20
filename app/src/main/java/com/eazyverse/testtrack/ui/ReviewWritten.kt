package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Optional suggested wording to copy on the Play step.
 * On a live app's Play step, also offers help when Play shows no review box.
 */

@Composable
internal fun WrittenAsk(
    job: ReviewJob,
    onNoBox: () -> Unit = {},
) {
    if (job.settled) return
    val offered = job.review.offered.trim()
    // On the proof step the ready review is the developer's bubble beside the box instead (ProveAsks).
    val showOffer = offered.isNotEmpty() && job.needsChars > 0 && job.step == ReviewStep.LEAVE
    val showNoBoxHelp = job.step == ReviewStep.LEAVE && job.live && !job.noBox
    if (!showOffer && !showNoBoxHelp) return
    val clipboard = LocalClipboardManager.current
    val t = Tt.colors
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewSectionGap)) {
        if (showOffer) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                ReviewSectionTitle(Lucide.MessageSquare, "Suggested wording")
                ReviewBullet(body = "Optional. Edit it to match your experience.", highlights = listOf("Optional", "your experience"))
                Text(
                    offered,
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(t.glass2.copy(alpha = 0.55f))
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = t.text,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                    RowPill("Copy", PillKind.SOFT, onClick = { clipboard.setText(AnnotatedString(offered)) })
                }
            }
        }
        if (showNoBoxHelp) ReviewNoPlayOption(job, onNoBox)
    }
}
