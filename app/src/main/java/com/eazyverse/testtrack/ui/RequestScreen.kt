package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Text
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.pluralOf
import com.eazyverse.testtrack.domain.requestReviewStatus
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * One of your own requests for feedback, after the asking.
 *
 * Everything it has had, and how each ended: what was sent, what you decided, how a refusal came
 * out. Before this a review left the owner's sight the moment it was accepted, and a refusal was
 * never heard of again. Closing it is here too, and gives back what was held for the slots nobody
 * took.
 */

/**
 * The page for one request. [onOpenWaiting] opens a review on its own page: one waiting on the
 * owner, one refused with the members deciding, or one accepted.
 */
@Composable
fun RequestCheck(
    request: Request,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenWaiting: (String) -> Unit = {},
    onRelist: () -> Unit = {},
    /** Accept a review the owner refused, while its case is still open. */
    onAcceptLater: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var closing by remember { mutableStateOf(false) }
    var accepting by remember { mutableStateOf<RequestReview?>(null) }
    CheckFrame(
        title = "Your request",
        onBack = onBack,
        foot =
            if (request.closed) {
                null
            } else {
                {
                    if (request.unlisted) {
                        Primary("I fixed it, relist it", enabled = !busy, onClick = onRelist)
                        Spacer(Modifier.height(8.dp))
                    }
                    Secondary(
                        if (request.refundable > 0) "Close and get ${request.refundable} tix back" else "Close request",
                        Modifier.fillMaxWidth(),
                        enabled = !busy,
                    ) { closing = true }
                }
            },
    ) {
        AppHead(
            request.packageName,
            request.appName,
            request.icon,
            request.isLive,
            sub = if (request.isLive) "Live app" else "Closed test",
            onClick = { openPlay(context, request.packageName) }.takeIf { request.packageName.isNotBlank() },
            onClickLabel = "Open ${request.appName} on Google Play",
        )
        Spacer(Modifier.height(14.dp - AppHeadMarkRoom))
        ChipRow {
            StatusChip("${request.done} of ${request.bought} done", Tone.QUIET)
            StatusChip(
                if (request.closed) "Closed" else "${request.open} open ${pluralOf(request.open, "slot")}",
                Tone.QUIET,
            )
            StatusChip("Pays ${request.reward} tix", Tone.QUIET)
        }
        if (request.reports.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            InstallReports(request)
        }
        askedLine(request.feedback, request.needsChars, owner = true)?.let { (line, figure) ->
            Spacer(Modifier.height(14.dp))
            InfoLine(strong(line, figure), icon = Lucide.Text)
        }
        if (request.readyTexts.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val n = request.readyTexts.size
            QuietNote("$n ready-written ${pluralOf(n, "review")}, each for one reviewer. Nobody has to use them.", center = false)
        }
        SectionHeading("Reviews", count = request.reviews.size.takeIf { it > 0 }, top = 22.dp)
        if (request.reviews.isEmpty()) {
            QuietNote(
                if (request.closed) {
                    "Nobody reviewed it before it closed."
                } else {
                    "Nothing yet. Reviews show up here as people take it on."
                },
                center = false,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                request.reviews.forEach { review ->
                    RequestReviewCard(review, request.needsChars, busy, proof, { accepting = review }) { onOpenWaiting(review.id) }
                }
            }
        }
    }

    if (closing) {
        Ask(
            title = "Close this request?",
            body =
                (
                    if (request.refundable > 0) {
                        "Nobody new can take it, and ${request.refundable} tix for the ${request.open} " +
                            "slots nobody took come back to you now. "
                    } else {
                        "Nobody new can take it. "
                    }
                ) + "Reviews already under way can finish, and any that end unpaid give their tix back too.",
            confirm = "Close it",
            onConfirm = {
                closing = false
                onClose()
            },
            onDismiss = { closing = false },
        )
    }
    accepting?.let { review ->
        Ask(
            title = "Accept it after all?",
            body =
                "The reviewer is paid ${request.reward} tix and the members stop deciding. Members who already voted " +
                    "get ${FeedRules.JUROR_FEE} tix each from your ${FeedRules.STAKE} tix stake, and the rest comes back to you.",
            confirm = "Accept and pay",
            onConfirm = {
                accepting = null
                onAcceptLater(review.id)
            },
            onDismiss = { accepting = null },
        )
    }
}

/** One review of the request: who, where it stands, and on tap what they sent. */
@Composable
private fun RequestReviewCard(
    review: RequestReview,
    needs: Int,
    busy: Boolean,
    proof: @Composable (String) -> Unit,
    onAcceptLater: () -> Unit,
    onOpenWaiting: () -> Unit,
) {
    val t = Tt.colors
    // Waiting on you, refused with the members deciding, or accepted: each opens the review's own page.
    val waiting = review.opensCheck
    val sent = review.proofRef.isNotBlank() || review.written.isNotBlank()
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .tileFrame(onClick = if (waiting || sent) ({ if (waiting) onOpenWaiting() else open = !open }) else null)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                review.reviewerName.ifBlank { "A reviewer" },
                Modifier.weight(1f),
                style = tileStyle(15f, 19f, FontWeight.Bold),
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            StatusChip(requestReviewStatus(review), reviewTone(review))
            if (waiting || sent) {
                Icon(
                    when {
                        waiting -> Lucide.ChevronRight
                        open -> Lucide.ChevronUp
                        else -> Lucide.ChevronDown
                    },
                    null,
                    Modifier.padding(start = 6.dp).size(18.dp),
                    tint = t.text3,
                )
            }
        }
        if (review.state == "rejected" || review.state == "disputed") {
            if (review.reason.isNotBlank()) {
                QuietNote("You said: ${review.reason}", Modifier.padding(top = 4.dp), center = false)
            }
            if (review.canAcceptLater) {
                RefusedByMistake(onAcceptLater, Modifier.padding(top = 4.dp), enabled = !busy, center = false)
            }
        }
        AnimatedVisibility(open && !waiting) { SentThread(review, needs, proof) }
    }
}

/** What a review of the request sent, and any dispute since, as the thread the Check pages draw. */
@Composable
private fun SentThread(
    review: RequestReview,
    needs: Int,
    proof: @Composable (String) -> Unit,
) {
    val who = review.reviewerName.trim().ifBlank { "Reviewer" }
    val again = review.recheckedRef.isNotBlank()
    val evidence = review.evidence
    Thread {
        ThreadGroup(
            listOfNotNull(
                review.proofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
                reviewWords(review.written, needs, review.textUse),
            ),
            who = who,
            time = if (again) "First screenshot" else "",
            picture = proof,
        )
        if (again) {
            ThreadGroup(
                listOf(ThreadPart.Picture(review.recheckedRef)),
                who = who,
                time = "New, taken on Play when asked",
                picture = proof,
            )
        }
        ThreadGroup(
            listOfNotNull(evidence.ownerProofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) }),
            side = ThreadSide.RIGHT,
            picture = proof,
        )
        ThreadGroup(
            listOfNotNull(
                evidence.reply.takeIf { it.isNotBlank() }?.let { ThreadPart.Words(it) },
                evidence.replyProofRef.takeIf { it.isNotBlank() }?.let { ThreadPart.Picture(it) },
            ),
            who = who,
            picture = proof,
        )
    }
}

/** A review's status tone: settled in green, money at stake in amber, under way in the accent, ended unpaid in grey. */
private fun reviewTone(review: RequestReview): Tone =
    when (review.state) {
        "approved" -> Tone.OK
        "submitted", "disputed", "checking" -> Tone.WARN
        "claimed", "installed", "used" -> Tone.ACCENT
        else -> Tone.QUIET
    }
