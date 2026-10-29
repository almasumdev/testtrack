package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

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
)

/** States a review is still being worked on in. Tapping any row opens its review page. */
val InProgressStates = setOf("claimed", "installed", "used")

/** Sent, and with the developer (or the members deciding a dispute). */
val PendingStates = setOf("submitted", "disputed")

/** Decided, one way or the other. */
val HistoryStates = setOf("approved", "rejected", "abandoned", "expired")

/** Anything past the reviewer's own part: off the Feedback list for good. */
val SentStates = PendingStates + HistoryStates

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
        section("In progress", going, onOpen, first === going)
        // Every row opens its review: one in progress at its step, a sent one at its outcome.
        section("Waiting for the developer", pending, onOpen, first === pending)
        section("History", history, onOpen, first === history)
    }
}

/**
 * One section. Reviews in progress are cards of their own, since each is something to go and
 * do; the others are rows in one card, since they are only to be followed.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    rows: List<MyReview>,
    onOpen: (MyReview) -> Unit,
    first: Boolean,
) {
    if (rows.isEmpty()) return
    item(key = "h-$title") { CrewHeading(title, "${rows.size}", top = if (first) 0.dp else 20.dp) }
    if (rows.first().state in InProgressStates) {
        items(rows, key = { it.id }) { review ->
            // Fades in, fades out and glides to its new place when the list changes.
            Box(Modifier.animateItem()) {
                Box(Modifier.padding(horizontal = Gutter, vertical = 4.dp)) {
                    val shape = RoundedCornerShape(CardRadius)
                    Box(Modifier.clip(shape).background(Tt.colors.glass).border(1.dp, Tt.colors.glassBorder, shape)) {
                        MineRow(review, onOpen) { StepChip(review) }
                    }
                }
            }
        }
    } else {
        item(key = "g-$title") {
            val shape = RoundedCornerShape(CardRadius)
            Column(
                Modifier
                    .padding(horizontal = Gutter)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Tt.colors.glass)
                    .border(1.dp, Tt.colors.glassBorder, shape),
            ) {
                rows.forEachIndexed { index, review ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .padding(horizontal = 14.dp)
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Tt.colors.glassBorder),
                        )
                    }
                    MineRow(review, onOpen) { Tix(review) }
                }
            }
        }
    }
}

/**
 * The app's mark, and beside it two lines spanning exactly its height: the name level with its
 * top edge and the state level with its bottom. Whatever sits at the end is centred on both.
 */
@Composable
private fun MineRow(
    review: MyReview,
    onOpen: (MyReview) -> Unit,
    end: @Composable () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onOpen(review) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(review.packageName, review.appName, review.icon, size = MINE_TILE)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).height(MINE_TILE), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                review.appName.ifBlank { "An app" },
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val (icon, tone) = mineMark(review)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, Modifier.size(13.dp), tint = tone)
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    mineLine(review),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = tone,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        end()
    }
}

/** Where a review in progress has got to, out of the four steps. */
@Composable
private fun StepChip(review: MyReview) {
    val step =
        when (review.state) {
            "claimed" -> 1
            "installed" -> 2
            else -> 3
        }
    Pill("Step $step/4", Tt.colors.accentInk, Tt.colors.tile)
}

/** What the review is worth: owed, paid, or not paid. */
@Composable
private fun Tix(review: MyReview) {
    val t = Tt.colors
    val (text, tone) =
        when (review.state) {
            "approved" -> "+${review.reward}" to t.ok
            "rejected" -> "+${review.reward}" to t.text4
            else -> "+${review.reward}" to t.text2
        }
    Text(
        text,
        style =
            MaterialTheme.typography.titleSmall
                .copy(fontSize = 15.sp)
                .tabular(),
        fontWeight = FontWeight.SemiBold,
        color = tone,
        textDecoration = if (review.state in setOf("rejected", "abandoned", "expired")) TextDecoration.LineThrough else null,
    )
}

private fun mineLine(review: MyReview): String =
    when (review.state) {
        "claimed" -> "Install it from Play"
        "installed" -> if (review.isLive) liveUseLine(review) else "Use it for two minutes"
        "used" -> if (review.isLive) "Review it on Google Play" else "Send feedback on Google Play"
        "submitted" -> if (review.isLive) "Sent. With the developer" else "Sent. Checked within a day"
        "disputed" -> "Disputed. Members are deciding"
        "approved" -> "Approved. Tix paid"
        "rejected" -> review.reason.ifBlank { "Not paid" }
        "abandoned" -> "Given up"
        "expired" -> if (review.isLive) "Ran out after three days" else "Ran out after a day"
        else -> ""
    }

/** Where a live review is with its two days, so a row never asks for what is already done. */
internal fun liveUseLine(
    review: MyReview,
    now: java.time.Instant = java.time.Instant.now(),
): String {
    val opens =
        review.opensAt?.let {
            runCatching {
                java.time.OffsetDateTime
                    .parse(it)
                    .toInstant()
            }.getOrNull()
        }
    return when {
        review.daysUsed >= 2 && opens != null && opens.isAfter(now) -> "Opens ${opensWhen(opens, now)}"
        review.daysUsed >= 2 && review.usedEnough -> "Ready for your review"
        review.daysUsed >= 2 -> "A little more time in the app"
        review.usedEnough || review.daysUsed == 1 -> "Open it again on another day"
        else -> "Use it on two days"
    }
}

/** The small mark before the state line, and the colour both are in. */
@Composable
private fun mineMark(review: MyReview): Pair<ImageVector?, Color> {
    val t = Tt.colors
    return when (review.state) {
        "submitted" -> Lucide.Hourglass to t.accentInk
        "disputed" -> Lucide.Users to t.warn
        "approved" -> Lucide.CircleCheck to t.ok
        "rejected" -> Lucide.CircleX to t.bad
        "abandoned", "expired" -> Lucide.CircleX to t.text4
        else -> null to t.text3
    }
}

private val MINE_TILE = 42.dp
