package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.CircleCheck
import com.composables.icons.lucide.CircleX
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Users
import com.eazyverse.testtrack.ui.theme.Barlow
import com.eazyverse.testtrack.ui.theme.Tt

/** Tags the text column of a review's ticket, so a test can hold it to the icon's height. */
internal const val MINE_TEXT_TAG = "mine-text"

/** Tags the whole ticket. */
internal const val MINE_CARD_TAG = "mine-card"

/** The holes on a review's ticket: one for each step. */
internal const val MINE_STEPS = 4

// The text column is 19 + 2 + 15 + 2 + 14 = 52dp, the height of the app's mark beside it, with the
// font's own padding trimmed the way Requests trims it. A review that is decided has two lines.
@Suppress("DEPRECATION")
private fun mineStyle(
    size: Float,
    height: Float,
    weight: FontWeight,
) = TextStyle(
    fontFamily = Barlow,
    fontSize = size.sp,
    lineHeight = height.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = TicketLineHeight,
)

private val MineName = mineStyle(16f, 19f, FontWeight.SemiBold)
private val MineLine = mineStyle(12.5f, 15f, FontWeight.Medium)
private val MineLineQuiet = mineStyle(12.5f, 15f, FontWeight.Normal)
private val MineMeta = mineStyle(11.5f, 14f, FontWeight.Medium)
private val MineMetaQuiet = mineStyle(11.5f, 14f, FontWeight.Normal)

private val UnpaidStates = setOf("rejected", "abandoned", "expired")

/**
 * One review as a ticket of its own: the app's mark, its name over the next step or where the
 * review stands, and on the stub what it pays. One in progress or sent also shows its punched
 * holes; a paid one has its stub torn off, and one that never paid is faded with the amount struck.
 */
@Composable
internal fun MineCard(
    review: MyReview,
    onOpen: (MyReview) -> Unit,
    now: Long = System.currentTimeMillis(),
) {
    val t = Tt.colors
    val paid = review.state == "approved"
    val unpaid = review.state in UnpaidStates
    val checking = review.state == "checking"
    TicketFrame(
        modifier = Modifier.testTag(MINE_CARD_TAG),
        onClick = { onOpen(review) },
        leading = {
            Box(if (unpaid) Modifier.alpha(0.55f) else Modifier) {
                AppMark(review.packageName, review.appName, review.icon, size = 52.dp)
            }
        },
        stub = { MineStub(review) },
        torn = paid,
        stubFill = t.okBg,
        line = if (checking) t.warnBorder else t.cardLine,
    ) {
        Column(Modifier.testTag(MINE_TEXT_TAG)) {
            Text(
                review.appName.ifBlank { "An app" },
                style = MineName,
                color = if (unpaid) t.text2 else t.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (review.state) {
                in InProgressStates -> {
                    MineLine2(null, Color.Unspecified, mineNext(review), if (checking) t.warn else t.text2, MineLine)
                    MineProgress(review, now)
                }
                in PendingStates -> {
                    val disputed = review.state == "disputed"
                    val tone = if (disputed) t.warn else t.accentInk
                    MineLine2(if (disputed) Lucide.Users else Lucide.Hourglass, tone, mineWaitingWith(review), tone, MineLine)
                    MineWaiting(review)
                }
                else -> {
                    val tone =
                        when (review.state) {
                            "approved" -> t.ok
                            "rejected" -> t.bad
                            else -> t.text4
                        }
                    MineLine2(if (paid) Lucide.CircleCheck else Lucide.CircleX, tone, mineHistory(review), t.text3, MineLineQuiet)
                }
            }
        }
    }
}

/** The second line: an optional 12dp icon and one line of text that gives way with an ellipsis. */
@Composable
private fun MineLine2(
    icon: ImageVector?,
    iconTone: Color,
    text: String,
    tone: Color,
    style: TextStyle,
) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(12.dp), tint = iconTone)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, style = style, color = tone, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The third line of a review in progress: the holes, the step, and at the right end the time left. */
@Composable
private fun MineProgress(
    review: MyReview,
    now: Long,
) {
    val t = Tt.colors
    val step = mineStep(review)
    val held = review.state == "checking"
    val states =
        List(MINE_STEPS) { i ->
            when {
                i + 1 < step -> Punch.Done
                i + 1 == step -> if (held) Punch.Hold else Punch.Next
                else -> Punch.Todo
            }
        }
    val until = mineDeadline(review)?.toEpochMilli()
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PunchDots(states)
        Spacer(Modifier.width(7.dp))
        Text(
            "Step $step of $MINE_STEPS",
            Modifier.weight(1f),
            style = MineMeta,
            color = t.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (until != null) {
            val tone = if (leftIsUrgent(review, until, now)) t.warn else t.text3
            Icon(Lucide.Clock, null, Modifier.size(11.dp), tint = tone)
            Spacer(Modifier.width(3.dp))
            Text(shortLeft(until, now), style = MineMeta, color = tone, maxLines = 1)
        }
    }
}

/** The third line of a sent review: every hole punched, and a few words on how it was sent. */
@Composable
private fun MineWaiting(review: MyReview) {
    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        PunchDots(List(MINE_STEPS) { Punch.Done })
        Spacer(Modifier.width(7.dp))
        Text(
            mineWaitNote(review),
            Modifier.weight(1f),
            style = MineMetaQuiet,
            color = Tt.colors.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the review pays, stamped on the stub for where it stands. */
@Composable
private fun MineStub(review: MyReview) {
    val t = Tt.colors
    val reward = review.reward
    when (review.state) {
        "checking", "disputed" ->
            TicketStubFigure(
                "+$reward",
                "Held, $reward tix",
                color = t.warn,
                fill = t.warnBg,
                stamp = "Held",
            )
        "submitted" -> {
            // Sent here because Play showed no review box: it pays half.
            val pays = if (review.noBox) reward / 2 else reward
            TicketStubFigure(
                "+$pays",
                "Sent, $pays tix" + if (review.noBox) ", half pay" else "",
                stamp = "Sent",
                stampColor = t.accentInk,
                word = if (review.noBox) "half pay" else null,
            )
        }
        "approved" ->
            TicketStubFigure(
                "+$reward",
                "Paid, $reward tix",
                color = t.ok,
                fill = t.okBg,
                tear = null,
                stamp = "Paid",
            )
        "rejected" -> MineVoid(reward, "Refused", t.bad)
        "expired" -> MineVoid(reward, "Ran out", t.text4)
        "abandoned" -> MineVoid(reward, "Given up", t.text4)
        else -> TicketStubFigure("+$reward", "$reward tix to earn", word = "to earn")
    }
}

/** The stub of a review that never paid: stamped with why, the amount struck and quiet. */
@Composable
private fun MineVoid(
    reward: Int,
    why: String,
    stampTone: Color,
) {
    val t = Tt.colors
    TicketStubFigure(
        "$reward",
        "$why, not paid, $reward tix",
        color = t.text4,
        struck = true,
        fill = t.bg,
        stamp = why,
        stampColor = stampTone,
    )
}
