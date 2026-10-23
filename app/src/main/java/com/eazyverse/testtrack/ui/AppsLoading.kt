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

/** One app's page before My apps has it: the icon, its name and its package, as the page's top. */
@Composable
internal fun AppHeroLoading() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Loading the app" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Skeleton(width = 88.dp, height = 88.dp, corner = 26.dp)
        Spacer(Modifier.height(16.dp))
        Skeleton(width = 150.dp, height = 20.dp)
        Spacer(Modifier.height(8.dp))
        Skeleton(width = 190.dp, height = 12.dp)
        Spacer(Modifier.height(24.dp))
        repeat(2) {
            Skeleton(height = 56.dp, corner = 16.dp)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** The app a page is for, before My apps has it: the card [FoundApp] draws it in. */
@Composable
internal fun FoundAppLoading() {
    Box(Modifier.semantics(mergeDescendants = true) { contentDescription = "Loading the app" }) {
        CrewCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Skeleton(width = 48.dp, height = 48.dp, corner = 14.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Skeleton(width = 130.dp, height = 15.dp)
                    Spacer(Modifier.height(7.dp))
                    Skeleton(width = 170.dp, height = 11.dp)
                }
            }
        }
    }
}

/**
 * A page's app is not on My apps. Offline, that is not knowing rather than it being gone, so it
 * says so and offers to try again; otherwise it was deleted, most likely on another phone.
 */
@Composable
internal fun AppNotThere(
    offline: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    if (offline) {
        RobotState(
            Prop.Notes,
            "Couldn't load your apps",
            "Check your connection, then try again.",
            action = "Try again",
            onAction = onRetry,
        )
    } else {
        RobotState(
            Prop.Notes,
            "This app isn't on My apps",
            "It may have been deleted on another phone.",
            action = "Back",
            onAction = onBack,
        )
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
