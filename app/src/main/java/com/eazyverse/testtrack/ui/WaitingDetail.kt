package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Quote
import com.composables.icons.lucide.TriangleAlert
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.closeTo
import com.eazyverse.testtrack.domain.timeLeft
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The To check tab's rows, and the page each one opens: a review of your app to accept or refuse,
 * or a case between two other people to decide. The page is where the screenshot is seen at full
 * size, which is the evidence the decision rests on.
 */

/**
 * A review or case on its own page, from the Circle tab's view model.
 *
 * Closes itself once it is decided, and also if it is no longer there to decide: answered on
 * another phone, or accepted by the clock while the list was open.
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
    val review = feed.waiting.firstOrNull { it.id == id }
    val case = feed.disputes.firstOrNull { it.id == id }
    val request = feed.requests.firstOrNull { it.id == id }
    var refusing by remember { mutableStateOf(false) }
    // Arrived from My apps, perhaps before Circle has ever loaded: fetch, rather than find nothing.
    LaunchedEffect(Unit) { if (review == null && case == null && request == null) feed.load(context) }
    LaunchedEffect(feed.decided) { if (feed.decided == id) onClose() }
    // A review arriving, a vote landing or the case being decided shows on this page as it happens.
    WhileShown(onShow = { feed.startPageLive(context) }, onHide = feed::stopPageLive)
    LaunchedEffect(review == null && case == null && request == null, feed.loading) {
        if (review == null && case == null && request == null && !feed.loading) onClose()
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
                )
            request != null ->
                RequestCheck(
                    request,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    onClose = { feed.closeRequest(context, request.id) },
                    onOpenWaiting = onOpen,
                )
            case != null ->
                CaseCheck(
                    case,
                    busy = feed.busy,
                    proof = { ref -> ProofImage(ref, whole = true) },
                    onBack = onClose,
                    onVote = { forReviewer -> feed.vote(context, case, forReviewer) },
                )
        }
    }
    if (refusing && review != null) {
        FeedRefuseDialog(
            appName = review.appName,
            onRefuse = { reason ->
                refusing = false
                feed.reject(context, review, reason)
            },
            onDismiss = { refusing = false },
        )
    }
}

/** Rows in one card with hairlines between, like Mine's. */
@Composable
internal fun CheckGroup(
    count: Int,
    row: @Composable (Int) -> Unit,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(CardRadius)
    Column(
        Modifier
            .padding(horizontal = Gutter)
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape),
    ) {
        repeat(count) { index ->
            if (index > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(t.glassBorder),
                )
            }
            row(index)
        }
    }
}

/** The app's mark, its name over one line of detail spanning the mark's height, a chip, and a chevron. */
@Composable
internal fun CheckRow(
    appName: String,
    detail: String,
    onClick: () -> Unit,
    /** The app's package, so an installed one shows its own icon rather than a lettered tile. */
    packageName: String = "",
    chip: @Composable () -> Unit,
) {
    val t = Tt.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(packageName, appName, size = ROW_TILE) { AppTile(appName, size = ROW_TILE) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).height(ROW_TILE), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                appName,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp, lineHeight = 18.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                color = t.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        chip()
        Icon(Lucide.ChevronRight, null, Modifier.padding(start = 4.dp).size(18.dp), tint = t.text3)
    }
}

/** How long before a review accepts itself; amber in its last twelve hours. */
@Composable
internal fun TimeChip(
    text: String,
    close: Boolean,
) {
    val t = Tt.colors
    val (ink, ground) = if (close) t.warn to t.warnBg else t.accentInk to t.glass2
    Row(
        Modifier.clip(RoundedCornerShape(percent = 50)).background(ground).padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Clock, null, Modifier.size(13.dp), tint = ink)
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = ink)
    }
}

/**
 * A review of your app, on its own page: who sent it, their note, the screenshot whole, and the
 * two answers at the bottom. Refusing is not free, so the page says what it starts.
 */
@Composable
fun ReviewCheck(
    review: Waiting,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onApprove: () -> Unit = {},
    onReject: () -> Unit = {},
) {
    val now = System.currentTimeMillis()
    val until = review.decideBy
    CheckPage(
        title = "Review",
        onBack = onBack,
        actions = {
            Primary(
                if (review.reward > 0) "Accept · pay ${review.reward}" else "Accept",
                Modifier.weight(1f),
                busy = busy,
                onClick = onApprove,
            )
            Spacer(Modifier.width(10.dp))
            Secondary("Refuse", Modifier.weight(1f).height(48.dp), destructive = true, enabled = !busy, onClick = onReject)
        },
    ) {
        AppHeader(review.appName, review.packageName, if (review.isLive) CheckKind.PLAY else CheckKind.CLOSED)
        Spacer(Modifier.height(12.dp))
        StatTiles(
            listOf(
                Stat("Reviewer", review.reviewerName.ifBlank { "Unknown" }),
                if (until != null) {
                    Stat("Time left", timeLeft(until, now), warn = closeTo(until, now))
                } else {
                    Stat("Time left", "2 days")
                },
                Stat("Pays", if (review.reward > 0) "${review.reward} tix" else "-"),
            ),
        )
        if (review.isLive && review.kept == false) {
            Spacer(Modifier.height(12.dp))
            Notice(Status.short, Lucide.TriangleAlert) {
                Text(
                    "They uninstalled the app within three days of reviewing it. Google may see that as a review " +
                        "that is not genuine, so check it before it accepts itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        if (review.written.isNotBlank()) Said("Their note", review.written)
        if (review.proofRef.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            proof(review.proofRef)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            (
                if (until !=
                    null
                ) {
                    "Accepts itself in ${timeLeft(until, now)} if you do nothing. "
                } else {
                    "It accepts itself after two days if you do nothing. "
                }
            ) +
                "Refusing sends it to three members, and whoever they decide against pays them.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = Tt.colors.text3,
        )
    }
}

/** A case between a developer and a reviewer, on its own page, with the two sides at the bottom. */
@Composable
fun CaseCheck(
    case: Case,
    busy: Boolean = false,
    proof: @Composable (String) -> Unit = {},
    onBack: () -> Unit = {},
    onVote: (forReviewer: Boolean) -> Unit = {},
) {
    CheckPage(
        title = "Case",
        onBack = onBack,
        actions = {
            Secondary("The developer is right", Modifier.weight(1f).height(48.dp), enabled = !busy) { onVote(false) }
            Spacer(Modifier.width(10.dp))
            Secondary("The review stands", Modifier.weight(1f).height(48.dp), enabled = !busy) { onVote(true) }
        },
    ) {
        AppHeader(case.appName, case.packageName, CheckKind.DISPUTED)
        Spacer(Modifier.height(12.dp))
        StatTiles(
            listOf(
                Stat("Votes", "${case.votesCast} of ${case.votesNeeded}"),
                Stat("Stake", "${case.stake} tix"),
                Stat("Review pays", if (case.reward > 0) "${case.reward} tix" else "-"),
            ),
        )
        Said("The developer says", case.reason)
        if (case.written.isNotBlank()) Said("The reviewer's note", case.written)
        if (case.proofRef.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            proof(case.proofRef)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Whoever you decide against pays the ${case.stake} tix that goes to the three of you deciding.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = Tt.colors.text3,
        )
    }
}

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

private val ROW_TILE = 42.dp
