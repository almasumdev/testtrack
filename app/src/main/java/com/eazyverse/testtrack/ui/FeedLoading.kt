package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

/** A request on the Feedback list while it loads, shaped as the card that lands: icon, name, pill, ticket. */
@Composable
internal fun FeedRequestLoadingCard() {
    FeedCard(
        modifier = Modifier.semantics { contentDescription = "Loading Circle content" },
        padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(width = 48.dp, height = 48.dp, corner = 24.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Skeleton(height = 16.dp, modifier = Modifier.fillMaxWidth(0.6f))
                Skeleton(width = 84.dp, height = 20.dp, corner = 10.dp)
            }
            Spacer(Modifier.width(10.dp))
            Skeleton(width = 52.dp, height = 36.dp, corner = 8.dp)
        }
    }
}

/** A Circle tab before its list has arrived: three placeholder cards where the first will land. */
@Composable
internal fun FeedLoadingList() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 16.dp, bottom = Gutter + dockRoom()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(3) { FeedLoadingCard() }
    }
}
