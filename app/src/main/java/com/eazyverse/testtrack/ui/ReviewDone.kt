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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Hourglass
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The review page's ends that are not a step. Sent and decided reviews are a chat thread
 * (ReviewWaitThread); a review given up or run out, and the first load that could not happen, are
 * the TestTrack robot holding a cartoon thing that says how things stand.
 */

/**
 * The review is out of the reviewer's hands. Given up or run out: the robot, one line on what that
 * means, and what it cost. Anything else (sent, refused, approved, not paid) is the thread.
 */
@Composable
internal fun ReviewDone(
    job: ReviewJob,
    room: Dp,
) {
    val state = job.review.state
    if (state != "abandoned" && state != "expired") {
        ReviewWaitThread(job)
        return
    }
    val t = Tt.colors
    val (prop, title) = if (state == "abandoned") Prop.Flag to "Given up" else Prop.Clock to "Ran out"
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
            reviewOutcome(state, job.live, job.review.endedReason),
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
            FailedRow(
                when {
                    state == "abandoned" -> "You gave it up"
                    job.live -> "Not finished within three days"
                    else -> "Not finished within a day"
                },
                "The slot went back for somebody else",
            )
            Box(
                Modifier
                    .padding(start = STAGE_DOT / 2 - 1.dp, top = 3.dp, bottom = 3.dp)
                    .size(width = 2.dp, height = 12.dp)
                    .background(t.glassBorder),
            )
            FailedRow("+${job.reward} tix", "Not paid")
        }
    }
}

/**
 * Under a sent or decided review: a hint and the button. The reviewer may answer a refusal once;
 * otherwise the page is done, with a word on what happens next while it waits.
 */
@Composable
internal fun WaitFoot(
    job: ReviewJob,
    busy: Boolean,
    onDone: () -> Unit,
    onAnswer: () -> Unit,
) {
    val state = job.review.state
    val hint: Pair<ImageVector, String>? =
        when {
            job.canReply -> Lucide.MessageSquare to "You can answer once. The members see it."
            state == "submitted" && job.live && !job.noBox ->
                Lucide.Smartphone to "Keep the app installed. If they don't answer, we check after a day and pay you."
            state == "submitted" -> Lucide.Hourglass to "${job.reward} tix once it's accepted."
            else -> null
        }
    hint?.let { (icon, text) ->
        IconLine(
            icon,
            Tt.colors.text3,
            AnnotatedString(text),
            tileStyle(12.5f, 16f, FontWeight.SemiBold),
            Tt.colors.text3,
            Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
    }
    if (job.canReply) {
        Primary("Answer the developer", busy = busy, onClick = onAnswer)
    } else {
        Primary("Done", onClick = onDone)
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

/** One thing that did not happen: a red round with an X, what, and why. */
@Composable
private fun FailedRow(
    title: String,
    detail: String,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(STAGE_DOT).clip(CircleShape).background(t.bad), contentAlignment = Alignment.Center) {
            Icon(Lucide.X, null, Modifier.size(14.dp), tint = t.bg)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text,
            )
            Text(detail, style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp), color = t.text3)
        }
    }
}

private val STAGE_DOT = 28.dp
