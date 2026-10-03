package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Minus
import com.composables.icons.lucide.Plus
import kotlin.math.roundToInt

@Composable
internal fun ReviewInvestment(
    reviews: Int,
    reward: Int,
    enabled: Boolean,
    onReviewsChange: (Int) -> Unit,
    onRewardChange: (Int) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Number of reviews", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onReviewsChange(reviews - 1) }, enabled = enabled && reviews > FeedRules.MIN_REVIEWS) {
                    Box(
                        Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.Minus, "Fewer reviews", Modifier.size(18.dp))
                    }
                }
                Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
                    Text("$reviews", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                IconButton(onClick = { onReviewsChange(reviews + 1) }, enabled = enabled && reviews < FeedRules.MAX_REVIEWS) {
                    Box(
                        Modifier.size(32.dp).background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Lucide.Plus, "More reviews", Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(3, 5, 10, 20).forEach { count ->
                FilterChip(
                    selected = reviews == count,
                    onClick = { onReviewsChange(count) },
                    label = { Text("$count", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "$count reviews" },
                )
            }
        }
        Text("Choose 1–50 reviews.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Reward per review", style = MaterialTheme.typography.titleSmall)
            Text("$reward tix", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = reward.toFloat(),
            onValueChange = { onRewardChange((it / 10).roundToInt() * 10) },
            valueRange = FeedRules.MIN_REWARD.toFloat()..FeedRules.MAX_REWARD.toFloat(),
            steps = (FeedRules.MAX_REWARD - FeedRules.MIN_REWARD) / 10 - 1,
            enabled = enabled,
            modifier = Modifier.semantics { contentDescription = "Ticket reward per review" },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${FeedRules.MIN_REWARD} tix", style = MaterialTheme.typography.bodySmall)
            Text("${FeedRules.MAX_REWARD} tix", style = MaterialTheme.typography.bodySmall)
        }
    }
}
