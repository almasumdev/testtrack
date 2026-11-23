package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.ShieldCheck
import com.eazyverse.testtrack.ui.theme.Tt

/** Shared rules; the request-specific requirements and agreement follow separately. */
@Composable
internal fun ReviewStartRules(job: ReviewJob) {
    val within = if (job.live) FeedRules.LIVE_CLAIM_HOURS else FeedRules.CLAIM_HOURS
    val useMinutes = ((job.review.neededMs + 59_999L) / 60_000L).coerceAtLeast(1)
    val duration = "$useMinutes " + if (useMinutes == 1L) "minute" else "minutes"
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ReviewLineGap),
    ) {
        ReviewSectionTitle(Lucide.ShieldCheck, "Before you start")
        ReviewBullet(
            body = "Use the app for at least $duration.",
            highlights = listOf(duration),
        )
        if (job.paced) {
            val again = "${FeedRules.LIVE_AGAIN_HOURS} hours"
            ReviewBullet(
                body = "Come back after $again and use it for $duration more.",
                highlights = listOf(again, "$duration more"),
            )
            val opens = "${FeedRules.LIVE_OPEN_HOURS} hours"
            ReviewBullet(body = "The review opens $opens after your first $duration.", highlights = listOf(opens))
        } else if (job.live) {
            ReviewBullet(
                body = "Return on another day, at least ${FeedRules.LIVE_RETURN_HOURS} hours later.",
                highlights = listOf("another day", "${FeedRules.LIVE_RETURN_HOURS} hours"),
            )
        }
        ReviewBullet(
            body = if (job.live) "Give a 5-star review on Play." else "Send useful private feedback to the developer on Play.",
            highlights = if (job.live) listOf("5-star review") else listOf("private feedback", "developer"),
        )
        if (job.paced) {
            val days = "${FeedRules.LIVE_FINISH_HOURS / 24} days"
            ReviewBullet(body = "Send a screenshot within $days of your first $duration.", highlights = listOf(days))
        } else {
            ReviewBullet(
                body = "Send a screenshot within $within hours of installation.",
                highlights = listOf("$within hours"),
            )
        }
        ReviewBullet(
            body = "Earn ${job.post.reward} tix after approval.",
            highlights = listOf("${job.post.reward} tix", "after approval"),
        )
        if (job.live) {
            ReviewBullet(body = "Keep the app installed until the follow-up check.", highlights = listOf("Keep the app installed"))
            ReviewBullet(
                body = "No Play review box? Feedback sent here pays half the reward.",
                highlights = listOf("half the reward"),
            )
        }
        ReviewBullet(
            lead = "Lost dispute:",
            body = "No reward; up to ${FeedRules.STAKE} tix deducted.",
            highlights = listOf("No reward", "${FeedRules.STAKE} tix"),
        )
    }
}

/** Only the checkbox's own 48dp target toggles agreement; the label and row do not. */
@Composable
internal fun ReviewRulesAgreement(
    accepted: Boolean,
    onAccepted: (Boolean) -> Unit,
) {
    val t = Tt.colors
    val label = "I agree to the rules and this request."
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
            Checkbox(
                checked = accepted,
                onCheckedChange = onAccepted,
                modifier = Modifier.requiredSize(48.dp).semantics { contentDescription = label },
                colors = CheckboxDefaults.colors(checkedColor = t.accent, checkmarkColor = t.onAccent, uncheckedColor = t.text4),
            )
        }
        Text(
            label,
            Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = t.text,
        )
    }
}
