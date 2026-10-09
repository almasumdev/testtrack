package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * My apps before they have arrived: a page of them, in the shapes the tab really uses.
 *
 * Two running cards, as [MyAppCard] draws them with their meter, then a run of finished rows as
 * [MyAppRow] does. It was two thin outlines with most of the screen empty under them, which said
 * "two things, maybe" rather than "your apps are on their way". The headings are bars too: which
 * of the two halves somebody has is part of the answer.
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
        repeat(2) { i ->
            Box(Modifier.padding(horizontal = Gutter, vertical = 5.dp)) {
                CrewCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Skeleton(width = if (i == 0) 128.dp else 104.dp, height = 14.dp)
                            Spacer(Modifier.height(7.dp))
                            Skeleton(width = if (i == 0) 92.dp else 116.dp, height = 11.dp)
                        }
                        Spacer(Modifier.width(10.dp))
                        Skeleton(width = 58.dp, height = 22.dp, corner = 11.dp)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Skeleton(width = 112.dp, height = 13.dp)
                        Spacer(Modifier.weight(1f))
                        Skeleton(width = 64.dp, height = 11.dp)
                    }
                    Spacer(Modifier.height(10.dp))
                    SkeletonMeter(boxes = 14, height = 8.dp)
                }
            }
        }
        HeadingLoading(76)
        repeat(FINISHED_ROWS) { i ->
            ListTile(i, FINISHED_ROWS) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Skeleton(width = 44.dp, height = 44.dp, corner = 13.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Skeleton(width = RowNames[i % RowNames.size].dp, height = 13.dp)
                        Spacer(Modifier.height(7.dp))
                        Skeleton(width = RowLines[i % RowLines.size].dp, height = 10.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Skeleton(width = 50.dp, height = 20.dp, corner = 10.dp)
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

/** Enough finished rows to carry the page past the dock. */
private const val FINISHED_ROWS = 5

private val RowNames = listOf(118, 92, 136, 104, 84)
private val RowLines = listOf(170, 146, 188, 132, 160)
