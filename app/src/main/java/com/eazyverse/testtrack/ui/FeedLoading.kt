package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Uses the shared themed shimmer and the same card geometry as the arriving feed. */
@Composable
internal fun FeedLoadingCard(compact: Boolean = false) {
    FeedCard(modifier = Modifier.semantics { contentDescription = "Loading Circle content" }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 42.dp, height = 42.dp, corner = 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(height = 14.dp, modifier = Modifier.fillMaxWidth(0.65f))
                Spacer(Modifier.height(8.dp))
                Skeleton(height = 10.dp, modifier = Modifier.fillMaxWidth(0.45f))
            }
        }
        if (!compact) {
            Spacer(Modifier.height(18.dp))
            Skeleton(height = 12.dp)
            Spacer(Modifier.height(8.dp))
            Skeleton(height = 12.dp, modifier = Modifier.fillMaxWidth(0.7f))
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 96.dp, height = 10.dp)
                Skeleton(width = 88.dp, height = 32.dp, corner = 16.dp)
            }
        }
    }
}
