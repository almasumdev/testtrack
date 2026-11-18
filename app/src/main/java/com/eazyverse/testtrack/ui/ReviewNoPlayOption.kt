package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.composables.icons.lucide.CircleHelp
import com.composables.icons.lucide.Lucide

/** Make the fallback discoverable, with its payout clear before anybody switches to it. */
@Composable
internal fun ReviewNoPlayOption(
    job: ReviewJob,
    onNoBox: () -> Unit,
) {
    val reward = job.post.reward / 2
    var open by rememberSaveable(job.post.id, job.review.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
        ReviewSectionTitle(Lucide.CircleHelp, "No review option on Play?")
        ReviewBullet(
            body = "Feedback sent here pays $reward tix (half reward), if accepted.",
            highlights = listOf("$reward tix", "half reward"),
        )
        RowPill("Check fixes", PillKind.SOFT, onClick = { open = true })
    }
    if (!open) return
    NoPlayOptionSheet(job, onDismiss = { open = false }, onContinue = onNoBox)
}
