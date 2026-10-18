package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * My apps before they have arrived: a page of app cards in the shape [AppCard] draws them, an
 * icon and two lines over a row of badges, under the list's one heading.
 */
@Composable
internal fun AppsLoading() {
    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Loading your apps" },
    ) {
        HeadingLoading(92)
        repeat(CARDS) { i ->
            Box(Modifier.padding(horizontal = Gutter, vertical = 5.dp)) {
                CrewCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Skeleton(width = RowNames[i % RowNames.size].dp, height = 14.dp)
                            Spacer(Modifier.height(7.dp))
                            Skeleton(width = RowLines[i % RowLines.size].dp, height = 11.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Skeleton(width = 74.dp, height = 22.dp, corner = 11.dp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row {
                        Skeleton(width = Badges[i % Badges.size].dp, height = 24.dp, corner = 12.dp)
                        Spacer(Modifier.width(6.dp))
                        Skeleton(width = 96.dp, height = 24.dp, corner = 12.dp)
                    }
                }
            }
        }
    }
}

/** A section heading's place, where [CrewHeading] will put its words. */
@Composable
private fun HeadingLoading(width: Int) {
    Box(Modifier.padding(start = Gutter, end = Gutter, top = 20.dp, bottom = 12.dp)) {
        Skeleton(width = width.dp, height = 15.dp)
    }
}

/** Enough cards to carry the page past the dock. */
private const val CARDS = 5

private val RowNames = listOf(118, 92, 136, 104, 84)
private val RowLines = listOf(150, 126, 168, 112, 140)
private val Badges = listOf(132, 104, 150, 118, 96)
