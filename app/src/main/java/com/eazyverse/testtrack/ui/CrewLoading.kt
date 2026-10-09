package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/*
 * The crew tabs' first load, each in the shape of what is coming.
 *
 * Only what the server sends shimmers. The tabs, the headings and the rules of a crew are the
 * app's own words and are there from the first frame, so the page is already the page and the
 * shimmer marks only the holes the answer will fill.
 */

/** The open crew's card: its name, its fifteen seats, the line under them and the button. */
@Composable
fun CrewOpenCardLoading() {
    Box(Modifier.padding(start = Gutter, end = Gutter, bottom = 12.dp)) {
        CrewCard(Modifier.loading("Loading the open crew"), padding = PaddingValues(16.dp)) {
            Text("Open crew", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            SkeletonSeats()
            Spacer(Modifier.height(13.dp))
            Skeleton(width = 150.dp, height = 13.dp)
            Spacer(Modifier.height(18.dp))
            Skeleton(height = 48.dp, corner = 24.dp)
        }
    }
}

/** Tonight: the evening's card, then the runs it is spread across. */
@Composable
fun CrewRunLoading() {
    LazyColumn(
        Modifier.fillMaxSize().loading("Loading tonight"),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
        userScrollEnabled = false,
    ) {
        item {
            Box(Modifier.padding(horizontal = Gutter)) {
                CrewCard(padding = PaddingValues(16.dp)) {
                    Skeleton(width = 170.dp, height = 24.dp, corner = 8.dp)
                    Spacer(Modifier.height(10.dp))
                    Skeleton(width = 210.dp, height = 13.dp)
                    Spacer(Modifier.height(18.dp))
                    Skeleton(height = 10.dp, corner = 5.dp)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth()) {
                        repeat(3) {
                            Column(Modifier.weight(1f)) {
                                Skeleton(width = 40.dp, height = 20.dp)
                                Spacer(Modifier.height(6.dp))
                                Skeleton(width = 64.dp, height = 10.dp)
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Skeleton(height = 48.dp, corner = 24.dp)
                }
            }
        }
        item { CrewHeading("Your runs") }
        items(2) { index -> ListTile(index, 2) { CrewRowLoading(index) } }
    }
}

/** Waiting to start: a crew card with its seats. */
@Composable
fun CrewLobbyLoading() {
    Column(
        Modifier.fillMaxSize().padding(top = 8.dp).loading("Loading your crews"),
    ) {
        Box(Modifier.padding(horizontal = Gutter)) {
            CrewCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(width = 16.dp, height = 16.dp, corner = 8.dp)
                    Spacer(Modifier.width(8.dp))
                    Skeleton(width = 190.dp, height = 17.dp)
                }
                Spacer(Modifier.height(10.dp))
                Skeleton(height = 13.dp)
                Spacer(Modifier.height(6.dp))
                Skeleton(width = 180.dp, height = 13.dp)
                Spacer(Modifier.height(16.dp))
                SkeletonSeats()
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Skeleton(width = 120.dp, height = 14.dp)
                    Spacer(Modifier.weight(1f))
                    Skeleton(width = 56.dp, height = 22.dp, corner = 11.dp)
                }
            }
        }
    }
}

/** Finished: the heading, then rows of crews that are over. */
@Composable
fun CrewRecordLoading() {
    Column(Modifier.fillMaxSize().padding(top = 8.dp).loading("Loading finished crews")) {
        CrewHeading("Finished")
        repeat(3) { index -> ListTile(index, 3) { CrewRowLoading(index) } }
    }
}

/** One crew row in a list: its mark, its name and its line, and the chevron's place. */
@Composable
private fun CrewRowLoading(index: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Skeleton(width = 40.dp, height = 40.dp, corner = 12.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Skeleton(width = if (index % 2 == 0) 110.dp else 88.dp, height = 14.dp)
            Spacer(Modifier.height(7.dp))
            Skeleton(width = if (index % 2 == 0) 150.dp else 176.dp, height = 11.dp)
        }
    }
}

/** Fifteen seats, eight to a row like [SeatTiles], so nothing moves when the real ones land. */
@Composable
private fun SkeletonSeats() {
    val perRow = 8
    val gap = 5.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - gap * (perRow - 1)) / perRow
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            (0 until CrewRules.SEATS).chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { Skeleton(width = side, height = side, corner = 9.dp) }
                }
            }
        }
    }
}

private fun Modifier.loading(what: String) = semantics(mergeDescendants = true) { contentDescription = what }
