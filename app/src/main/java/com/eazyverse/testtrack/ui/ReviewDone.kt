package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The review page's two ends that are not a step: the review sent and out of the reviewer's hands,
 * and the first load that could not happen. Both are the TestTrack robot holding a cartoon thing
 * that says how things stand.
 */

/** Where one stage of a sent review has got to. */
private enum class Stage { DONE, NOW, LATER, FAILED }

/**
 * The review is with the developer, or decided.
 *
 * The robot holding the outcome, one line on what that means, and the three stages a
 * sent review goes through, so "what happens now" is answered on the page rather than guessed.
 */
@Composable
internal fun ReviewDone(
    job: ReviewJob,
    room: Dp,
) {
    val t = Tt.colors
    val state = job.review.state
    val reward = job.post.reward
    val (prop, title) =
        when (state) {
            "approved" -> Prop.Seal to "Approved"
            "disputed" -> Prop.Scales to "Being decided"
            "rejected" -> Prop.Wallet to "Not paid"
            "abandoned" -> Prop.Flag to "Given up"
            "expired" -> Prop.Clock to "Ran out"
            else -> Prop.Letter to "Sent to the developer"
        }
    Column(
        Modifier.fillMaxWidth().heightIn(min = room - 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RobotHolding(prop, size = (room * 0.2f).coerceIn(96.dp, 132.dp), description = title)
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp, lineHeight = 28.sp),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            // The stages below say what happens next, so the line above them does not repeat it.
            if (state == "submitted") "Thanks for reviewing. Here's what happens next." else reviewOutcome(state, job.live),
            Modifier.widthIn(max = 300.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = t.text2,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        val shape = RoundedCornerShape(CardRadius)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(t.glass)
                .border(1.dp, t.glassBorder, shape)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (state == "abandoned" || state == "expired") {
                StageRow(
                    Stage.FAILED,
                    if (state == "abandoned") {
                        "You gave it up"
                    } else if (job.live) {
                        "Not finished within three days"
                    } else {
                        "Not finished within a day"
                    },
                    "The slot went back for somebody else",
                )
                StageGap()
                StageRow(Stage.FAILED, "+$reward tix", "Not paid")
                return@Column
            }
            StageRow(Stage.DONE, "Sent with your screenshot", "Your part is done")
            StageGap()
            when (state) {
                "submitted" ->
                    if (job.live) {
                        StageRow(Stage.NOW, "The developer checks it", "They can accept it any time")
                        StageGap()
                        StageRow(Stage.LATER, "Keep the app installed", "If they don't answer, we check after 3 days and pay you")
                    } else {
                        StageRow(Stage.NOW, "The developer checks it", "Up to two days, then it approves itself")
                    }
                "disputed" -> StageRow(Stage.NOW, "Three members decide", "The developer disputed it")
                "rejected" ->
                    StageRow(
                        Stage.FAILED,
                        "Decided against",
                        job.review.reason.ifBlank { "The members sided with the developer" },
                    )
                else -> StageRow(Stage.DONE, "The developer approved it", "Your review counts")
            }
            StageGap()
            when (state) {
                "approved" -> StageRow(Stage.DONE, "+$reward tix", "Paid to you")
                "rejected" -> StageRow(Stage.FAILED, "+$reward tix", "Not paid")
                else -> StageRow(Stage.LATER, "+$reward tix", "Paid when it is approved")
            }
        }
    }
}

/**
 * The first load failed and nothing was saved to show instead. The robot, why, and the page's
 * button tries again.
 */
@Composable
internal fun ReviewFailed(
    reason: String,
    room: Dp,
) {
    val t = Tt.colors
    Column(
        Modifier.fillMaxWidth().heightIn(min = room - 64.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RobotHolding(Prop.Plug, size = (room * 0.18f).coerceIn(88.dp, 120.dp))
        Spacer(Modifier.height(18.dp))
        Text("Can't open this review", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            reason,
            Modifier.widthIn(max = 280.dp),
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = t.text2,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StageRow(
    stage: Stage,
    title: String,
    detail: String,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        val (fill, ink) =
            when (stage) {
                Stage.DONE -> t.ok to t.bg
                Stage.NOW -> t.accent to t.bg
                Stage.LATER -> t.glass2 to t.text3
                Stage.FAILED -> t.bad to t.bg
            }
        Box(Modifier.size(STAGE_DOT).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
            when (stage) {
                Stage.DONE -> Icon(Lucide.Check, null, Modifier.size(14.dp), tint = ink)
                Stage.NOW -> Icon(Lucide.Hourglass, null, Modifier.size(13.dp), tint = ink)
                Stage.FAILED -> Icon(Lucide.X, null, Modifier.size(14.dp), tint = ink)
                Stage.LATER -> Unit
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = if (stage == Stage.LATER) t.text2 else t.text,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = t.text3)
        }
    }
}

/** The thread between two stages. */
@Composable
private fun StageGap() {
    Box(
        Modifier
            .padding(start = STAGE_DOT / 2 - 1.dp, top = 3.dp, bottom = 3.dp)
            .size(width = 2.dp, height = 12.dp)
            .background(Tt.colors.glassBorder),
    )
}

private val STAGE_DOT = 28.dp
