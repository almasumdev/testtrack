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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Quote
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
        AppTile(appName, size = ROW_TILE)
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
        CheckHeader(
            review.appName,
            "by ${review.reviewerName.ifBlank { "a reviewer" }} · ${if (review.isLive) "Play review" else "Closed test"}",
        ) { if (until != null) TimeChip(timeLeft(until, now), closeTo(until, now)) }
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
        CheckHeader(case.appName, "Disputed review") {
            Pill("${case.votesCast} of ${case.votesNeeded}", Tt.colors.warn, Tt.colors.warnBg)
        }
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
private fun CheckPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = Gutter, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Lucide.ChevronLeft, "Back") }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

@Composable
private fun CheckHeader(
    appName: String,
    detail: String,
    chip: @Composable () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppTile(appName, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).height(48.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                appName,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = Tt.colors.text3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        chip()
    }
}

/** Something somebody said, under who said it. */
@Composable
private fun Said(
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
