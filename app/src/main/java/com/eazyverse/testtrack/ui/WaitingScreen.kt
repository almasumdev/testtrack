package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Inbox
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Waiting

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
    busy: Boolean = false,
    /**
     * How a screenshot gets on screen.
     *
     * Passed in rather than fetched here: proof is served by our API and refused without a
     * session, so putting one up means holding the token, and a screen that holds the token is a
     * screen no test can open.
     */
    proof: @Composable (String) -> Unit = {},
    onApprove: (Waiting) -> Unit = {},
    onReject: (Waiting) -> Unit = {},
    onVote: (Case, Boolean) -> Unit = { _, _ -> },
    onSignIn: () -> Unit = {},
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
        EmptyState(
            icon = Lucide.Inbox,
            title = "What is waiting did not load",
            subtitle = "Check your connection and pull to try again.",
        )
        return
    }
    if (loading && reviews.isEmpty() && disputes.isEmpty()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = Gutter + dockRoom()),
            verticalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(12.dp),
        ) {
            items(3) { FeedLoadingCard() }
        }
        return
    }
    if (reviews.isEmpty() && disputes.isEmpty()) {
        EmptyState(
            icon = Lucide.Inbox,
            title = "Nothing waiting on you",
            subtitle =
                "When somebody reviews one of your apps it lands here, and so does any case " +
                    "you are asked to help decide.",
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = Gutter + dockRoom()),
    ) {
        if (reviews.isNotEmpty()) {
            item { CrewHeading("Your apps", trailing = "${reviews.size}") }
            items(reviews, key = { it.id }) { review ->
                WaitingReview(
                    review = review,
                    busy = busy,
                    proof = proof,
                    onApprove = { onApprove(review) },
                    onReject = { onReject(review) },
                )
            }
        }
        if (disputes.isNotEmpty()) {
            item { CrewHeading("Help decide", trailing = "${disputes.size}") }
            items(disputes, key = { it.id }) { dispute ->
                WaitingDispute(dispute, busy = busy, proof = proof) { forReviewer ->
                    onVote(dispute, forReviewer)
                }
            }
        }
    }
}

/**
 * One review of your app, and the two things you can do about it.
 *
 * The proof is shown rather than described. Asking somebody to accept or refuse a review while
 * hiding the evidence would be asking them to guess, and refusing costs them a stake.
 */
@Composable
private fun WaitingReview(
    review: Waiting,
    busy: Boolean,
    proof: @Composable (String) -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Box(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        CrewCard(padding = PaddingValues(14.dp)) {
            Text(
                review.appName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A review is waiting. Two days from when it was sent and it accepts itself.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (review.written.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    review.written,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (review.proofRef.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                proof(review.proofRef)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                Primary("Accept", Modifier.weight(1f), enabled = !busy) { onApprove() }
                Spacer(Modifier.width(10.dp))
                // Refusing opens a case that three members decide, and the loser funds them. The
                // button says so on the next screen rather than pretending this is free.
                Secondary("Refuse", Modifier.weight(1f), destructive = true, enabled = !busy) {
                    onReject()
                }
            }
        }
    }
}

/** One case between two other people, and the side this account comes down on. */
@Composable
private fun WaitingDispute(
    dispute: Case,
    busy: Boolean,
    proof: @Composable (String) -> Unit,
    onVote: (Boolean) -> Unit,
) {
    Box(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
        CrewCard(padding = PaddingValues(14.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    dispute.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    "${dispute.votesCast} of ${dispute.votesNeeded}",
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    Status.neutralSoft,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "The developer refused this review. They said: ${dispute.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (dispute.written.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    dispute.written,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (dispute.proofRef.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                proof(dispute.proofRef)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Whoever you decide against pays the ${dispute.stake} tix that goes to the " +
                    "three of you deciding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Secondary("The review stands", Modifier.weight(1f), enabled = !busy) {
                    onVote(true)
                }
                Spacer(Modifier.width(10.dp))
                Secondary("The developer is right", Modifier.weight(1f), enabled = !busy) {
                    onVote(false)
                }
            }
        }
    }
}
