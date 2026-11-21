package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/*
 * Circle's "Mine" tab: every review this account has taken on, in three groups.
 *
 * A review leaves the Feedback list the moment it is sent, so it has to be findable somewhere:
 * here, under "Waiting for the developer", and later under "History" once it is decided.
 */

/** One of my reviews, as the list draws it. Plain values, so the tab can be opened in a test. */
data class MyReview(
    val id: String,
    val postId: String,
    val appName: String,
    val state: String,
    val reward: Int,
    val reason: String,
    val packageName: String = "",
    val isLive: Boolean = false,
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
    /** Days the app was used on for it, and whether its time in the app is done. */
    val daysUsed: Int = 0,
    val usedEnough: Boolean = false,
    /** A live review waiting on its second day: when it opens, as the server says. */
    val opensAt: String? = null,
    /** Sent in TestTrack because Play showed no review box: pays half. */
    val noBox: Boolean = false,
    /** Checking: by when the new screenshot is due. */
    val checkBy: String? = null,
    /** When the review was decided, and when it was sent, as the server says. */
    val decidedAt: String? = null,
    val submittedAt: String? = null,
)

/**
 * States a review is still being worked on in. Tapping any row opens its review page. Checking is
 * one: the developer can't find it on Play and the reviewer owes a new screenshot.
 */
val InProgressStates = setOf("claimed", "installed", "used", "checking")

/** Sent, and with the developer (or the members deciding a dispute). */
val PendingStates = setOf("submitted", "disputed")

/** Decided, one way or the other. */
val HistoryStates = setOf("approved", "rejected", "abandoned", "expired")

@Composable
fun MineBody(
    reviews: List<MyReview>,
    loading: Boolean,
    signedOut: Boolean,
    onSignIn: () -> Unit,
    onOpen: (MyReview) -> Unit,
    offline: Boolean = false,
    onRetry: () -> Unit = {},
    onFind: () -> Unit = {},
) {
    if (signedOut) {
        RobotState(
            Prop.Lock,
            "Your reviews live here",
            "Sign in to review apps and follow each one until it is paid.",
            action = "Sign in",
            onAction = onSignIn,
        )
        return
    }
    if (offline && reviews.isEmpty()) {
        RobotOffline("Couldn't load your reviews", onRetry)
        return
    }
    val going = reviews.filter { it.state in InProgressStates }
    val pending = reviews.filter { it.state in PendingStates }
    val history = reviews.filter { it.state in HistoryStates }
    if (!loading && going.isEmpty() && pending.isEmpty() && history.isEmpty()) {
        RobotState(
            Prop.Notes,
            "No reviews yet",
            "Pick an app from Feedback. Every review you take on is followed here.",
            action = "Find an app to review",
            onAction = onFind,
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = HeadedListTop, bottom = Gutter + dockRoom()),
    ) {
        // The first heading sits where the other tabs' first card does; the gap above a heading
        // is for separating it from the section before, and the first has none.
        val first = listOf(going, pending, history).firstOrNull { it.isNotEmpty() }
        section("In progress", going, onOpen, first === going, counted = true)
        // Every row opens its review: one in progress at its step, a sent one at its outcome.
        section("Waiting for the developer", pending, onOpen, first === pending)
        section("History", history, onOpen, first === history)
    }
}

/** One section: its heading, then each review as a ticket of its own. */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<MyReview>,
    onOpen: (MyReview) -> Unit,
    first: Boolean,
    /** Only what the reader still has to do carries a count, the same number the tab and the bar badge add up. */
    counted: Boolean = false,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { CrewHeading(title, if (counted) "${rows.size}" else null, top = if (first) 0.dp else 20.dp) }
    items(rows, key = { it.id }) { review ->
        // Fades in, fades out and glides to its new place when the list changes.
        Box(Modifier.animateItem()) {
            Box(Modifier.padding(horizontal = Gutter, vertical = 4.dp)) { MineCard(review, onOpen) }
        }
    }
}
