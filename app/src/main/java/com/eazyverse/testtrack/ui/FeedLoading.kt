package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** Uses the shared themed shimmer and the same card geometry as the arriving feed. */
@Composable
internal fun FeedLoadingCard() {
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

/** A request on the Feedback list while it loads, shaped as the ticket that lands: icon, two lines, stub. */
@Composable
internal fun FeedRequestLoadingCard() {
    val t = Tt.colors
    val shape = remember { TicketShape() }
    Row(
        Modifier
            .semantics { contentDescription = "Loading Circle content" }
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(t.bg)
            .border(1.dp, t.cardLine, shape),
    ) {
        Row(
            Modifier.weight(1f).padding(start = 12.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Skeleton(width = 52.dp, height = 52.dp, corner = 14.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Skeleton(height = 14.dp, modifier = Modifier.fillMaxWidth(0.62f))
                Spacer(Modifier.height(7.dp))
                Skeleton(height = 10.dp, modifier = Modifier.fillMaxWidth(0.42f))
            }
        }
        Column(
            Modifier
                .width(TICKET_STUB)
                .fillMaxHeight()
                .background(t.leafAccentSoft)
                .tearLine(t.glassBorder),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The stub is the same grey as the default bar, so these sit on card like the real ticket does.
            Skeleton(width = 16.dp, height = 16.dp, corner = 4.dp, ground = t.card)
            Spacer(Modifier.height(3.dp))
            Skeleton(width = 24.dp, height = 14.dp, corner = 4.dp, ground = t.card)
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
