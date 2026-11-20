package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Quote
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The To check tab's rows, and the page each one opens: a review of your app to accept or refuse,
 * or a case between two other people to decide. The page is where the screenshot is seen at full
 * size, which is the evidence the decision rests on.
 */

/**
 * A review or case on its own page, from the Circle tab's view model.
 *
 * A review, once decided, gives way to the next of the same app's reviews still waiting, so a
 * run of them is checked in one go; with none left the page closes. A vote stays on the page,
 * drawn from what the phone already knows, until the others are in. The page also closes if what
 * it shows is no longer there to decide: answered on another phone, or accepted by the clock.
 */
@Composable
fun CheckScreen(
    feed: FeedViewModel,
    id: String,
    onClose: () -> Unit,
    /** Open another of these pages, by id: a request's review that is waiting on its owner. */
    onOpen: (String) -> Unit = {},
) {
    val context = LocalContext.current
    // The review shown: the one opened, then the next of its app's once one is decided here. Keyed
    // on the id, since opening another of these from this page reuses the page with a new one.
    var current by rememberSaveable(id) { mutableStateOf(id) }
    var doneHere by rememberSaveable(id) { mutableStateOf(listOf<String>()) }
    val review = feed.waiting.firstOrNull { it.id == current }
    val listed = feed.disputes.firstOrNull { it.id == current }
    val request = feed.requests.firstOrNull { it.id == current }
    // A review of one of your requests already decided, opened from that request: refused with
    // the members deciding, or accepted. Drawn on the same page, with nothing left to answer.
    val settled =
        if (review == null && listed == null && request == null) {
            feed.requests.firstNotNullOfOrNull { r -> r.reviews.firstOrNull { it.id == current }?.let { settledCheck(r, it) } }
        } else {
            null
        }
    // A vote, once counted: the case as it was and the side sent, kept after the list lets it go.
    var sending by remember(id) { mutableStateOf<Voted?>(null) }
    var voted by remember(id) { mutableStateOf<Voted?>(null) }
    val case =
        voted?.let { v -> v.case.copy(votesCast = maxOf(v.case.votesCast + 1, listed?.votesCast ?: 0)) } ?: listed
    var refusing by remember { mutableStateOf(false) }
    var askingAgain by remember { mutableStateOf(false) }
    var acceptingLater by remember { mutableStateOf(false) }
    val missing = review == null && case == null && request == null && settled == null
    // This app's reviews still waiting, in the list's order, after the ones decided on this page.
    val queue = review?.let { shown -> feed.waiting.filter { it.sameApp(shown) && it.id !in doneHere } }.orEmpty()
    val position = doneHere.size + queue.indexOfFirst { it.id == current } + 1
    val total = doneHere.size + queue.size
    // Only a load started from here on can say it is gone: one that finished before the page
    // opened knew nothing of a request just posted or a review just sent.
    val opened = remember { feed.loadsStarted }
    // Arrived from My apps, perhaps before Circle has ever loaded: fetch, rather than find nothing.
    LaunchedEffect(Unit) { if (missing) feed.load(context) }
    LaunchedEffect(feed.decided) {
        val done = feed.decided ?: return@LaunchedEffect
        if (done != current) return@LaunchedEffect
        val vote = sending
        if (vote != null && vote.case.id == done) {
            voted = vote
            return@LaunchedEffect
        }
        // Accepted after all: the page stays and redraws as accepted once the request reloads.
        if (review == null && settled != null) return@LaunchedEffect
        val next = queue.firstOrNull { it.id != done }
        if (next == null) {
            onClose()
        } else {
            doneHere = doneHere + done
            current = next.id
        }
    }
    // A review arriving, a vote landing or the case being decided shows on this page as it happens.
    WhileShown(onShow = { feed.startPageLive(context) }, onHide = feed::stopPageLive)
    LaunchedEffect(missing, feed.loadsFinished) {
        if (missing && feed.loadsFinished > opened && !feed.offline) onClose()
    }
    Column(Modifier.fillMaxSize()) {
        PageMessage(feed.message, feed::clearMessage)
        when {
            review != null ->
                ReviewCheck(
                    review,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    onApprove = { feed.approve(context, review) },
                    onReject = { refusing = true },
                    onAskAgain = { askingAgain = true },
                    pager = if (total > 1 && position > 0) "$position of $total" else null,
                )
            settled != null ->
                ReviewCheck(
                    settled.first,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    settled = settled.second,
                    onAcceptLater = { acceptingLater = true },
                )
            request != null ->
                RequestCheck(
                    request,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    onClose = { feed.closeRequest(context, request.id) },
                    onRelist = { feed.relistRequest(context, request.id) },
                    onOpenWaiting = onOpen,
                    onAcceptLater = { reviewId -> feed.acceptLater(context, reviewId) },
                )
            case != null ->
                CaseCheck(
                    case,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    onVote = { forReviewer ->
                        sending = Voted(case, forReviewer)
                        feed.vote(context, case, forReviewer)
                    },
                    voted = voted?.forReviewer,
                )
            feed.offline -> {
                PageBar("", onClose)
                RobotState(
                    Prop.Notes,
                    "Couldn't load this",
                    "Check your connection, then try again.",
                    footer = {
                        Spacer(Modifier.height(8.dp))
                        RowPill("Try again", PillKind.SOFT) { feed.load(context) }
                    },
                )
            }
            else -> CheckSkeleton(onClose)
        }
    }
    if (askingAgain && review != null) {
        Ask(
            title = "Ask for a new screenshot?",
            body =
                "They get ${FeedRules.RECHECK_HOURS} hours to show their review on Play again. ${FeedRules.RECHECK_FEE} tix " +
                    "are held from you: they get them if they show it, you get them back if not. You can ask once.",
            confirm = "Ask",
            onConfirm = {
                askingAgain = false
                feed.askAgain(context, review)
            },
            onDismiss = { askingAgain = false },
        )
    }
    val later = settled?.first
    if (acceptingLater && later != null) {
        Ask(
            title = "Accept it after all?",
            body =
                "The reviewer is paid ${later.reward} tix and the members stop deciding. Members who already voted " +
                    "get ${FeedRules.JUROR_FEE} tix each from your ${FeedRules.STAKE} tix stake, and the rest comes back to you.",
            confirm = "Accept and pay",
            onConfirm = {
                acceptingLater = false
                feed.acceptLater(context, later.id)
            },
            onDismiss = { acceptingLater = false },
        )
    }
    if (refusing && review != null) {
        FeedRefuseSheet(
            needsChars = review.needsChars,
            onRefuse = { reason, picture -> feed.reject(context, review, reason, picture) },
            onDismiss = { refusing = false },
        )
    }
}

/** A vote sent on a case, with the case as it stood when it went. */
private class Voted(
    val case: Case,
    val forReviewer: Boolean,
)

/** Reviews of the same app, by its package, or its name from an older server that sent none. */
private fun Waiting.sameApp(other: Waiting): Boolean =
    if (packageName.isNotBlank() && other.packageName.isNotBlank()) packageName == other.packageName else appName == other.appName

/** A back arrow and a title, the page scrolling under it, and the answers pinned above the dock. */
@Composable
internal fun CheckPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        PageBar(title, onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter, vertical = 8.dp),
            content = content,
        )
        Row(
            Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 12.dp + dockRoom()),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Something somebody said, under who said it. */
@Composable
internal fun Said(
    who: String,
    words: String,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Spacer(Modifier.height(14.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.Quote, null, Modifier.size(14.dp), tint = t.text3)
            Spacer(Modifier.width(6.dp))
            Text(who, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = t.text3)
        }
        Spacer(Modifier.height(6.dp))
        Text(words, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.5.sp, lineHeight = 21.sp), color = t.text)
    }
}
