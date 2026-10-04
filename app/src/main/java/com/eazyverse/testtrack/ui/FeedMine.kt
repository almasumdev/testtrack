package com.eazyverse.testtrack.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ClipboardCheck
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

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
)

/** States a review is still being worked on in. Tapping any row opens its review page. */
val InProgressStates = setOf("claimed", "installed", "used")

/** Sent, and with the developer (or the members deciding a dispute). */
val PendingStates = setOf("submitted", "disputed")

/** Decided, one way or the other. */
val HistoryStates = setOf("approved", "rejected")

/** Anything past the reviewer's own part: off the Feedback list for good. */
val SentStates = PendingStates + HistoryStates

@Composable
fun MineBody(
    reviews: List<MyReview>,
    loading: Boolean,
    signedOut: Boolean,
    onSignIn: () -> Unit,
    onOpen: (MyReview) -> Unit,
) {
    if (signedOut) {
        EmptyState(
            icon = Lucide.ClipboardCheck,
            title = "Your reviews live here",
            subtitle = "Sign in, review an app from Feedback, and you can follow it from here.",
            actionLabel = "Sign in",
            onAction = onSignIn,
        )
        return
    }
    val going = reviews.filter { it.state in InProgressStates }
    val pending = reviews.filter { it.state in PendingStates }
    val history = reviews.filter { it.state in HistoryStates }
    if (!loading && going.isEmpty() && pending.isEmpty() && history.isEmpty()) {
        EmptyState(
            icon = Lucide.ClipboardCheck,
            title = "No reviews yet",
            subtitle = "Pick an app from Feedback. Once you send the review, it waits here for the developer.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = Gutter + dockRoom()),
    ) {
        // The first heading sits where the other tabs' first card does; the gap above a heading
        // is for separating it from the section before, and the first has none.
        val first = listOf(going, pending, history).firstOrNull { it.isNotEmpty() }
        section("In progress", going, onOpen, first === going)
        // Every row opens its review: one in progress at its step, a sent one at its outcome.
        section("Waiting for the developer", pending, onOpen, first === pending)
        section("History", history, onOpen, first === history)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<MyReview>,
    onOpen: ((MyReview) -> Unit)?,
    first: Boolean,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { CrewHeading(title, "${rows.size}", top = if (first) 2.dp else 20.dp) }
    itemsIndexed(rows, key = { _, r -> r.id }) { index, review ->
        ListTile(index, rows.size) { MyReviewRow(review, onOpen) }
    }
}

@Composable
private fun MyReviewRow(
    review: MyReview,
    onOpen: ((MyReview) -> Unit)?,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable { onOpen(review) } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTile(review.appName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                review.appName.ifBlank { "An app" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                mineLine(review),
                style = MaterialTheme.typography.bodySmall,
                color = t.text3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        val (label, tone, ground) = minePill(review)
        Pill(label, tone, ground)
    }
}

private fun mineLine(review: MyReview): String =
    when (review.state) {
        "claimed" -> "Install it to start"
        "installed" -> "Use it for 30 seconds"
        "used" -> "Leave your review on Play"
        "submitted" -> "Sent. The developer has two days"
        "disputed" -> "Disputed. Members are deciding"
        "approved" -> "Approved"
        "rejected" -> review.reason.ifBlank { "Not paid" }
        else -> ""
    }

@Composable
private fun minePill(review: MyReview): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val t = Tt.colors
    return when (review.state) {
        in InProgressStates -> Triple("Continue", t.tileInk, t.tile)
        "submitted" -> Triple("Waiting", t.text2, t.glass2)
        "disputed" -> Triple("Disputed", Status.short, Status.shortSoft)
        "approved" -> Triple("+${review.reward} tix", Status.posted, Status.postedSoft)
        else -> Triple("Not paid", Status.missed, Status.missedSoft)
    }
}
