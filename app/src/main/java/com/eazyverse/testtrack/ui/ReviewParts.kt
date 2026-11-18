package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircleQuestion
import com.eazyverse.testtrack.ui.theme.Tt

/** The same star/written/detailed contract the developer selects on Ask for feedback. */
@Composable
internal fun DeveloperAsks(job: ReviewJob) {
    val kind = FeedRules.Feedback.of(job.post.feedback)
    val chars = maxOf(job.post.needsChars, job.review.needsChars, kind.chars)
    val type =
        when {
            !job.live -> "Private feedback to the developer"
            chars >= FeedRules.Feedback.DETAILED.chars -> "Star rating + detailed feedback"
            chars > 0 -> "Star rating + written feedback"
            else -> "Star rating only"
        }
    val asked = job.post.ask.trim()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
        ReviewSectionTitle(Lucide.MessageCircleQuestion, "Developer asks")
        ReviewBullet(body = "$type.", highlights = listOf(type))
        if (!job.live) {
            ReviewBullet(
                body = "Describe what worked and what needs improving.",
                highlights = listOf("what worked", "what needs improving"),
            )
        }
        if (chars > 0) ReviewBullet(body = "At least $chars characters.", highlights = listOf("$chars characters"))
        if (asked.isNotEmpty() && asked != GENERAL_ASK) ReviewBullet(lead = "Focus:", body = asked)
    }
}

/** The icon route and open instruction sections before the review arrives. */
@Composable
internal fun ReviewLoading() {
    Column(
        Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "Getting your review ready" },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(ReviewSteps.size) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Skeleton(width = 48.dp, height = 48.dp, corner = 24.dp)
                    Spacer(Modifier.height(8.dp))
                    Skeleton(width = 58.dp, height = 12.dp)
                }
            }
        }
        Spacer(Modifier.height(ReviewSectionGap))
        Skeleton(height = 14.dp)
        Spacer(Modifier.height(6.dp))
        Skeleton(width = 220.dp, height = 14.dp)
        repeat(2) {
            Spacer(Modifier.height(ReviewSectionGap))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Skeleton(width = 28.dp, height = 28.dp, corner = 9.dp)
                Skeleton(width = 150.dp, height = 16.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.padding(start = ReviewBulletInset), horizontalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
                Skeleton(width = 4.dp, height = 4.dp, corner = 2.dp)
                Column(Modifier.weight(1f)) {
                    Skeleton(height = 12.dp)
                    Spacer(Modifier.height(6.dp))
                    Skeleton(width = 170.dp, height = 12.dp)
                }
            }
        }
    }
}

/** Under the Google Play step, for somebody who would rather use the phone's own screenshot keys. */
@Composable
internal fun SelfShotLink(onClick: () -> Unit) {
    Text(
        "I'll take the screenshot myself",
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = Tt.colors.accentInk,
        style = MaterialTheme.typography.labelLarge,
    )
}
