package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.closeTo
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
        Gated(
            heading = "Nothing addressed to you",
            body = "Reviews of your apps, and cases you are asked to decide, arrive here.",
            onSignIn = onSignIn,
            mark = { NoteMark() },
        )
        return
    }
    if (offline) {
        RobotOffline("What's waiting didn't load", onRetry)
        return
    }
    if (DebugLooks.holdLoading || (loading && reviews.isEmpty() && disputes.isEmpty())) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 16.dp, bottom = Gutter + dockRoom()),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(12.dp),
        ) {
            items(3) { FeedLoadingCard() }
        }
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

    // A list like Mine: one row each, and the deciding is done on the row's own page, where the
    // screenshot can be seen at full size. Cards with the screenshot and both buttons inline took
    // most of a screen each.
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = HeadedListTop, bottom = Gutter + dockRoom()),
    ) {
        val now = System.currentTimeMillis()
        if (reviews.isNotEmpty()) {
            item(key = "h-reviews") { CrewHeading("Reviews of your apps", "${reviews.size}", top = 0.dp) }
            item(key = "reviews") {
                CheckGroup(reviews.size) { index ->
                    val review = reviews[index]
                    CheckRow(
                        review.appName,
                        listOf(
                            review.reviewerName.ifBlank { "A reviewer" },
                            if (review.isLive) "Play review" else "Closed test",
                        ).joinToString(" · "),
                        onClick = { onOpenReview(review) },
                    ) {
                        val until = review.decideBy
                        if (until != null) {
                            TimeChip(timeLeft(until, now), closeTo(until, now))
                        }
                    }
                }
            }
        }
        if (disputes.isNotEmpty()) {
            item(key = "h-cases") {
                CrewHeading("Cases to decide", "${disputes.size}", top = if (reviews.isEmpty()) 0.dp else 20.dp)
            }
            item(key = "cases") {
                CheckGroup(disputes.size) { index ->
                    val case = disputes[index]
                    CheckRow(
                        case.appName,
                        "Disputed · ${case.votesCast} of ${case.votesNeeded} votes",
                        onClick = { onOpenCase(case) },
                    ) { Pill("${case.stake} tix", Tt.colors.accentInk, Tt.colors.glass2) }
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
            item(key = "requests") {
                CheckGroup(requests.size) { index ->
                    val request = requests[index]
                    CheckRow(
                        request.appName,
                        buildList {
                            add("${request.done} of ${request.bought} done")
                            if (request.underWay > 0) add("${request.underWay} under way")
                        }.joinToString(" · "),
                        onClick = { onOpenRequest(request) },
                    ) {
                        if (request.closed) {
                            Pill("Closed", Tt.colors.text3, Tt.colors.glass2)
                        } else {
                            Pill("${request.open} open", Tt.colors.accentInk, Tt.colors.glass2)
                        }
                    }
                }
            }
        }
    }
}
