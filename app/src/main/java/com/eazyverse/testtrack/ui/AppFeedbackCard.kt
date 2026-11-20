package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.EyeOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The Feedback block on an app's page: every request the app has had. The open one (or, with none
 * open, the newest) is drawn in full; every earlier one follows as a compact card under "Earlier".
 * Each shows one face for every review bought, drawn from the counts the server sends; there are
 * no names here, so every face is the same neutral person.
 */

/**
 * A block's heading with a small tag at the far end saying when that block is for. The tag stays
 * once the block is running; the line that explains it only shows on the empty start tile.
 */
@Composable
internal fun BlockHeading(
    title: String,
    tag: String,
) {
    val t = Tt.colors
    Row(
        Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 19.sp),
            fontWeight = FontWeight.Bold,
            color = t.text,
        )
        HeadingTag(tag)
    }
}

/** The small quiet tag at a heading's far end, such as when a block is for or "Optional". */
@Composable
internal fun HeadingTag(tag: String) {
    val t = Tt.colors
    Text(
        tag,
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(t.glass2)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        fontWeight = FontWeight.SemiBold,
        color = t.text3,
    )
}

/**
 * Every request an app has had, for its page. [requests] are this app's only, in any order. With
 * none yet it is the Get reviews start row, and its Ask pill when [canAsk]; while the first load
 * is out it is a card-shaped skeleton. Tapping a card opens that request; [onCheck] and [onFix]
 * open it on the part that needs the owner, and [onAsk] starts a new one.
 */
@Composable
fun FeedbackRequests(
    requests: List<Request>,
    loading: Boolean,
    onOpen: (Request) -> Unit,
    onAsk: () -> Unit,
    onCheck: (Request) -> Unit,
    onFix: (Request) -> Unit,
    modifier: Modifier = Modifier,
    canAsk: Boolean = true,
) {
    val t = Tt.colors
    Column(modifier.fillMaxWidth()) {
        if (requests.isEmpty()) {
            if (loading) {
                RequestSkeleton()
            } else {
                AppStartRow(
                    Lucide.MessageSquare,
                    "Get reviews",
                    FEEDBACK_START_LINE,
                    onClick = if (canAsk) onAsk else null,
                    pill = "Ask",
                )
            }
            return@Column
        }
        val (lead, earlier) = remember(requests) { requests.splitForPage() }
        if (lead != null) {
            RequestCard(
                lead,
                compact = false,
                askAgain = canAsk && lead.cardState().finished,
                onOpen = { onOpen(lead) },
                onCheck = { onCheck(lead) },
                onFix = { onFix(lead) },
                onAsk = onAsk,
            )
        }
        if (earlier.isEmpty()) return@Column
        Text(
            "Earlier",
            Modifier.padding(start = 2.dp, top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
            fontWeight = FontWeight.SemiBold,
            color = t.text3,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            earlier.forEach { request ->
                RequestCard(
                    request,
                    compact = true,
                    askAgain = false,
                    onOpen = { onOpen(request) },
                    onCheck = { onCheck(request) },
                    onFix = { onFix(request) },
                    onAsk = onAsk,
                )
            }
        }
    }
}

/**
 * One request: how many reviews are in and where it stands, its faces, and one line with the
 * action that fits it. [compact] is an earlier request: tighter, smaller faces, and the day it
 * ended after its state when that is known.
 */
@Composable
internal fun RequestCard(
    request: Request,
    compact: Boolean,
    askAgain: Boolean,
    onOpen: () -> Unit,
    onCheck: () -> Unit,
    onFix: () -> Unit,
    onAsk: () -> Unit,
) {
    val t = Tt.colors
    val state = request.cardState()
    val counts = request.reviewCounts()
    val tone =
        when (state) {
            RequestState.OFF_CIRCLE -> t.bad
            RequestState.DONE -> t.ok
            else -> t.text3
        }
    val day = request.closedAt?.takeIf { compact }?.let { monthDay(it) }
    CrewCard(
        padding = if (compact) PaddingValues(horizontal = 14.dp, vertical = 12.dp) else PaddingValues(16.dp),
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                requestTitle(state, counts),
                Modifier.weight(1f),
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (compact) 16.sp else 18.sp,
                        lineHeight = if (compact) 20.sp else 23.sp,
                    ),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (day != null) "${state.label} $day" else state.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                color = tone,
                maxLines = 1,
                softWrap = false,
            )
        }
        Spacer(Modifier.height(if (compact) 10.dp else 12.dp))
        val dimmed = state == RequestState.OFF_CIRCLE
        if (counts.summarised) FaceSummary(counts, dimmed = dimmed) else FaceGrid(counts, compact, dimmed = dimmed)
        RequestFooter(request, counts, requestFoot(request, compact, askAgain), compact, onCheck, onFix, onAsk)
    }
}

@Composable
private fun RequestFooter(
    request: Request,
    counts: ReviewCounts,
    foot: RequestFoot,
    compact: Boolean,
    onCheck: () -> Unit,
    onFix: () -> Unit,
    onAsk: () -> Unit,
) {
    val t = Tt.colors
    val lineTop = if (compact) 10.dp else 12.dp
    when (foot) {
        RequestFoot.OFF_CIRCLE ->
            FooterRow(lineTop) {
                Headline("Testers can't install it", t.bad, compact, Modifier.weight(1f), Lucide.EyeOff)
                Box(Modifier.offset(x = 8.dp)) { InlineAction("Fix it", onClick = onFix) }
            }
        RequestFoot.TO_CHECK ->
            FooterRow(lineTop) {
                Headline("${counts.toCheck} waiting for you", t.text, compact, Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                CheckPill(onCheck)
            }
        RequestFoot.ASK_AGAIN ->
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                Box(Modifier.offset(x = 8.dp)) { InlineAction("Ask again", onClick = onAsk) }
            }
        RequestFoot.TIX_BACK ->
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(rememberTicketMark(t.card), null, Modifier.size(13.dp).rotate(-12f))
                Spacer(Modifier.width(5.dp))
                Text(
                    "${request.refunded} tix came back",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
                    color = t.text3,
                )
            }
        RequestFoot.ON_IT -> FooterRow(lineTop) { Headline("Testers are on it", t.text3, compact, Modifier.weight(1f)) }
        RequestFoot.NONE -> Unit
    }
}

@Composable
private fun FooterRow(
    top: Dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = top), verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
private fun Headline(
    text: String,
    tone: Color,
    compact: Boolean,
    modifier: Modifier,
    icon: ImageVector? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(15.dp), tint = tone)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = if (compact) 14.sp else 15.sp, lineHeight = 20.sp),
            fontWeight = FontWeight.Medium,
            color = tone,
        )
    }
}

/** The filled button that takes the owner to the reviews waiting for them. */
@Composable
private fun CheckPill(onClick: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        Button(
            onClick = onClick,
            modifier = Modifier.heightIn(min = 32.dp),
            shape = RoundedCornerShape(percent = 50),
            elevation = null,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp),
        ) {
            Text(
                "Check",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.5.sp, lineHeight = 16.sp),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** The lead card while the first load is out: its title, a row of faces and its line, in the card's own frame. */
@Composable
private fun RequestSkeleton() {
    CrewCard(padding = PaddingValues(16.dp)) {
        Skeleton(modifier = Modifier.fillMaxWidth(0.55f), height = 23.dp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(5) { Skeleton(width = 34.dp, height = 34.dp, corner = 17.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Skeleton(modifier = Modifier.fillMaxWidth(0.4f), height = 20.dp)
    }
}
