package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** A request on the Feedback list while it loads, shaped as the ticket that lands: icon, two lines, stub. */
@Composable
internal fun FeedRequestLoadingCard() {
    val t = Tt.colors
    TicketFrame(
        modifier = Modifier.semantics { contentDescription = "Loading Circle content" },
        onClick = null,
        leading = { Skeleton(width = 52.dp, height = 52.dp, corner = 14.dp) },
        stub = {
            TicketStubFrame(null) {
                Image(rememberTicketMark(t.card), null, Modifier.size(16.dp).rotate(-12f))
                Spacer(Modifier.height(3.dp))
                TixLoadingDots()
            }
        },
    ) {
        Skeleton(height = 14.dp, modifier = Modifier.fillMaxWidth(0.62f))
        Spacer(Modifier.height(7.dp))
        Skeleton(height = 10.dp, modifier = Modifier.fillMaxWidth(0.42f))
    }
}

/** The ticket placeholders for the Circle tabs that list tickets (My reviews, To check). */
@Composable
internal fun TicketLoadingList() {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 16.dp, bottom = Gutter + dockRoom()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(SKELETON_ROWS) { FeedRequestLoadingCard() }
    }
}
