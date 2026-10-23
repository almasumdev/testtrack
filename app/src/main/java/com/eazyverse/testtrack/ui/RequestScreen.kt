package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronDown
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.ChevronUp
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
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

/** The page for one request. [onOpenWaiting] opens a review still waiting on the owner. */
@Composable
fun RequestCheck(
    request: Request,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onClose: () -> Unit = {},
    onOpenWaiting: (String) -> Unit = {},
) {
    var closing by remember { mutableStateOf(false) }
    CheckPage(
        title = "Your request",
        onBack = onBack,
        actions = {
            if (!request.closed) {
                Secondary(
                    if (request.refundable > 0) "Close and get ${request.refundable} tix back" else "Close request",
                    Modifier.weight(1f).height(48.dp),
                    enabled = !busy,
                ) { closing = true }
            }
        },
    ) {
        AppHeader(
            request.appName,
            request.packageName,
            if (request.isLive) CheckKind.PLAY else CheckKind.CLOSED,
            detail = if (request.closed) "Closed" else "",
            icon = request.icon,
        )
        Spacer(Modifier.height(12.dp))
        StatTiles(
            listOf(
                Stat("Done", "${request.done} of ${request.bought}"),
                Stat("Open slots", if (request.closed) "Closed" else "${request.open}"),
                Stat("Pays", "${request.reward} tix"),
            ),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            if (request.reviews.isEmpty()) "Reviews" else "Reviews · ${request.reviews.size}",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        if (request.reviews.isEmpty()) {
            DialogNote(
                if (request.closed) {
                    "Nobody reviewed it before it closed."
                } else {
                    "Nothing yet. Reviews show up here as people take it on."
                },
            )
        } else {
            Column(
                verticalArrangement =
                    androidx.compose.foundation.layout.Arrangement
                        .spacedBy(10.dp),
            ) {
                request.reviews.forEach { review ->
                    RequestReviewCard(review, proof) { onOpenWaiting(review.id) }
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
}

/** One review of the request: who, where it stands, and on tap what they sent. */
@Composable
private fun RequestReviewCard(
    review: RequestReview,
    proof: @Composable (String) -> Unit,
    onOpenWaiting: () -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    val waiting = review.state == "submitted"
    val sent = review.proofRef.isNotBlank() || review.written.isNotBlank()
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .clickable(enabled = waiting || sent) { if (waiting) onOpenWaiting() else open = !open }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                review.reviewerName.ifBlank { "A reviewer" },
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            val (ink, ground) = tone(review)
            Pill(requestReviewStatus(review), ink, ground)
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
                Spacer(Modifier.height(4.dp))
                DialogNote("You said: ${review.reason}")
            }
        }
        AnimatedVisibility(open && !waiting) {
            Column {
                if (review.written.isNotBlank()) Said("Their note", review.written)
                if (review.proofRef.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    proof(review.proofRef)
                }
            }
        }
    }
}

/** A review's status colours: settled in green, money at stake in amber, ended unpaid in grey. */
@Composable
private fun tone(review: RequestReview): Pair<Color, Color> {
    val t = Tt.colors
    return when (review.state) {
        "approved" -> t.ok to t.okBg
        "submitted", "disputed" -> t.warn to t.warnBg
        "claimed", "installed", "used" -> t.accentInk to t.glass2
        else -> t.text3 to t.glass2
    }
}
