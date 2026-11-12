package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.closeTo
import com.eazyverse.testtrack.domain.grouped
import com.eazyverse.testtrack.domain.pays
import com.eazyverse.testtrack.domain.timeLeft
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * Everything waiting on a decision from this account.
 *
 * Two duties, one screen, because from the reader's side they are the same errand: somebody is
 * waiting to hear from you. As the owner of an app you accept or refuse a review of it; as a
 * member with a finished fortnight behind you, you help decide a case between two other people.
 *
 * Until this existed neither had a way in. Every review approved itself after two days because
 * nothing in the app could call approve, and every dispute sat open because nothing could vote.
 * Half the economy was written, tested and unreachable.
 */
@Composable
fun WaitingBody(
    reviews: List<Waiting>,
    disputes: List<Case>,
    loading: Boolean = false,
    offline: Boolean = false,
    signedOut: Boolean = false,
    onOpenReview: (Waiting) -> Unit = {},
    onOpenCase: (Case) -> Unit = {},
    onSignIn: () -> Unit = {},
    onRetry: () -> Unit = {},
    requests: List<Request> = emptyList(),
    onOpenRequest: (Request) -> Unit = {},
) {
    // Asked before the network is blamed. This branch used to be the offline one and it was
    // titled "Sign in to see what is waiting", so a dropped connection told the reader they were
    // the problem, and a reader without an account was never told anything at all.
    if (signedOut) {
        RobotState(
            Prop.Lock,
            "Sign in to see your feedback",
            "Reviews of your apps, and cases you are asked to decide, arrive here.",
            action = "Sign in",
            onAction = onSignIn,
        )
        return
    }
    if (offline && reviews.isEmpty() && disputes.isEmpty() && requests.isEmpty()) {
        RobotOffline("What's waiting didn't load", onRetry)
        return
    }
    if (DebugLooks.holdLoading || (loading && reviews.isEmpty() && disputes.isEmpty())) {
        TicketLoadingList()
        return
    }
    if (reviews.isEmpty() && disputes.isEmpty() && requests.isEmpty()) {
        RobotState(
            Prop.IceCream,
            "Nothing waiting on you",
            "Reviews of your apps land here, and so does any case you are asked to help decide.",
        )
        return
    }

    // A list like Mine: one ticket each, and the deciding is done on the ticket's own page, where the
    // screenshot can be seen at full size. Cards with the screenshot and both buttons inline took
    // most of a screen each.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = HeadedListTop, bottom = Gutter + dockRoom()),
    ) {
        val now = System.currentTimeMillis()
        if (reviews.isNotEmpty()) {
            item(key = "h-reviews") { CrewHeading("Reviews of your apps", "${reviews.size}", top = 0.dp) }
            items(reviews, key = { "review-${it.id}" }) { review ->
                val until = review.decideBy
                val who = review.reviewerName.ifBlank { "A reviewer" }
                val kind = if (review.isLive) "live app" else "closed test"
                CheckCard(
                    Modifier.animateItem(),
                    review.appName,
                    if (until != null) "$who, $kind, ${timeLeft(until, now)} left" else "$who, $kind",
                    onClick = { onOpenReview(review) },
                    packageName = review.packageName,
                    icon = review.icon,
                    // Time left rides in the note, amber in the last twelve hours.
                    noteIcon = if (until != null) Lucide.Clock else null,
                    noteTone = if (until != null && closeTo(until, now)) Tt.colors.warn else Tt.colors.text3,
                ) {
                    if (review.pays > 0) {
                        TicketStubFigure(grouped(review.pays), "${review.pays} tix")
                    } else {
                        TicketStubIcon(Lucide.Clock, "Waiting for you")
                    }
                }
            }
        }
        if (disputes.isNotEmpty()) {
            item(key = "h-cases") {
                CrewHeading("Cases to decide", "${disputes.size}", top = if (reviews.isEmpty()) 0.dp else 20.dp)
            }
            items(disputes, key = { "case-${it.id}" }) { case ->
                CheckCard(
                    Modifier.animateItem(),
                    case.appName,
                    "Disputed, ${case.votesCast} of ${case.votesNeeded} votes in",
                    onClick = { onOpenCase(case) },
                    packageName = case.packageName,
                    icon = case.icon,
                ) {
                    if (case.stake > 0) {
                        TicketStubFigure(grouped(case.stake), "${case.stake} tix")
                    } else {
                        TicketStubIcon(Lucide.Users, "Members are deciding")
                    }
                }
            }
        }
        // Your own requests: what each has had, and the way to close one. Below what is owed,
        // because that is the errand; this is the record.
        if (requests.isNotEmpty()) {
            item(key = "h-requests") {
                CrewHeading(
                    "Your requests",
                    "${requests.size}",
                    top = if (reviews.isEmpty() && disputes.isEmpty()) 0.dp else 20.dp,
                )
            }
            items(requests, key = { "request-${it.id}" }) { request ->
                CheckCard(
                    Modifier.animateItem(),
                    request.appName,
                    buildList {
                        add("${request.done} of ${request.bought} done")
                        add(if (request.closed) "closed" else "${request.open} open")
                        if (request.underWay > 0) add("${request.underWay} under way")
                    }.joinToString(", "),
                    onClick = { onOpenRequest(request) },
                    packageName = request.packageName,
                    icon = request.icon,
                ) {
                    if (request.closed) {
                        TicketStubIcon(Lucide.Lock, "Closed", Tt.colors.text4)
                    } else {
                        TicketStubFigure(grouped(request.open), "${request.open} open")
                    }
                }
            }
        }
    }
}

/** One thing waiting on this account, as a ticket with the stub's figure chosen by the caller. */
@Composable
private fun CheckCard(
    modifier: Modifier,
    appName: String,
    note: String,
    onClick: () -> Unit,
    packageName: String,
    icon: String,
    noteIcon: ImageVector? = null,
    noteTone: Color = Tt.colors.text3,
    stub: @Composable () -> Unit,
) {
    // The caller's modifier fades the card in and out and glides it to its new place.
    Box(modifier) {
        Box(Modifier.padding(horizontal = Gutter, vertical = 4.dp)) {
            TicketCard(
                leading = { AppMark(packageName, appName, icon, size = 52.dp) },
                title = appName,
                note = note,
                noteIcon = noteIcon,
                noteTone = noteTone,
                onClick = onClick,
                stub = stub,
            )
        }
    }
}
