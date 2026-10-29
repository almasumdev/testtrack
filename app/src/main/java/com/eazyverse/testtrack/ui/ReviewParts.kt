package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageCircleQuestion
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/*
 * The pieces the review page is drawn from: the dial, the step names under it, the developer's
 * question, and the shimmer the page shows before the review arrives. Split from ReviewScreen.kt.
 */

/**
 * Four rounded segments round a ring, one per step.
 *
 * Done steps fill in the accent, the current one is tinted, and while the app is being used its
 * segment fills as the thirty seconds pass, so the dial is the clock as well as the progress.
 */
@Composable
internal fun StepDial(
    job: ReviewJob,
    size: Dp,
) {
    val t = Tt.colors
    val here = if (job.settled) ReviewSteps.size else ReviewSteps.indexOf(job.step).coerceAtLeast(0)
    val used by animateFloatAsState(job.usedFraction, tween(500), label = "used")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.toPx() * 0.072f
            val inset = stroke / 2f
            val ring = Size(this.size.width - stroke, this.size.height - stroke)
            val gapDeg = 14f
            val sweep = 90f - gapDeg
            for (i in 0 until 4) {
                val start = -90f + i * 90f + gapDeg / 2f
                val segment: (Color, Float) -> Unit = { color, fraction ->
                    drawArc(
                        color = color,
                        startAngle = start,
                        sweepAngle = sweep * fraction,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = ring,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                segment(t.glass2, 1f)
                when {
                    i < here -> segment(t.accent, 1f)
                    // Waiting on a second day: its time is in, but the step is not done, so lighter.
                    i == here && ReviewSteps[i] == ReviewStep.USE && job.waitingForDay -> segment(t.accent.copy(alpha = 0.45f), 1f)
                    i == here && ReviewSteps[i] == ReviewStep.USE -> if (used > 0f) segment(t.accent, used)
                    i == here -> segment(t.accentInk.copy(alpha = 0.35f), 1f)
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                job.review.state == "approved" -> {
                    Text("+${job.post.reward}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = t.ok)
                    Text("tix added", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                job.settled -> {
                    // A tick rather than the word: the title under the dial already says "Sent".
                    Icon(Lucide.Check, null, Modifier.size(size * 0.2f), tint = t.accentInk)
                    Text("with the developer", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                job.step == ReviewStep.USE && job.waitingForDay && job.opensAt() != null -> WaitCentre(job, size)
                job.step == ReviewStep.USE -> {
                    val needed = (job.review.neededMs / 1000).toInt()
                    Text(
                        "${(needed - job.secondsLeft).coerceAtLeast(0)}",
                        style =
                            MaterialTheme.typography.headlineMedium
                                .tabular()
                                .copy(fontSize = (size.value * 0.24f).sp),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text("of $needed seconds", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
                else -> {
                    Text("Step ${here + 1}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("of ${ReviewSteps.size}", style = MaterialTheme.typography.labelMedium, color = t.text3)
                }
            }
        }
    }
}

/**
 * The dial's middle while a live review waits on its second day: when it opens, as a time
 * tomorrow, a countdown once only hours are left, or "Day 2" when today already counts.
 */
@Composable
private fun WaitCentre(
    job: ReviewJob,
    size: Dp,
) {
    val t = Tt.colors
    val opens = job.opensAt() ?: return
    val now = java.time.Instant.now()
    val (big, small) =
        when {
            !opens.isAfter(now) -> "Open" to "come back to it"
            job.secondDayNow() -> "Day 2" to "open it again"
            job.usedOnTwoDays -> untilShort(opens, now) to "until it opens"
            else -> {
                val at = opensWhen(opens, now)
                at.substringAfter(" at ") to at.substringBefore(" at ")
            }
        }
    Text(
        big,
        style = MaterialTheme.typography.headlineMedium.copy(fontSize = (size.value * 0.15f).sp),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
    Text(small, style = MaterialTheme.typography.labelMedium, color = t.text3)
}

/** The four steps by name under the dial, ticked as they are done. */
@Composable
internal fun StepLabels(job: ReviewJob) {
    val t = Tt.colors
    val here = if (job.settled) ReviewSteps.size else ReviewSteps.indexOf(job.step).coerceAtLeast(0)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        ReviewSteps.forEachIndexed { i, step ->
            // Where you are, in a soft pill; done ones ticked, the rest grey. As four bare words it
            // read like four things to tap.
            Text(
                (if (i < here) "✓ " else "") + stepShort(step, job),
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (i == here) t.tile else Color.Transparent)
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.SemiBold,
                color =
                    if (i < here) {
                        t.text2
                    } else if (i == here) {
                        t.accentInk
                    } else {
                        t.text4
                    },
            )
        }
    }
}

/** The developer's question: an icon in line with the label, the question full width under it. */
@Composable
internal fun DeveloperAsks(asked: String) {
    val t = Tt.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.glass)
            .border(1.dp, t.glassBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.MessageCircleQuestion, null, Modifier.size(15.dp), tint = t.accentInk)
            Spacer(Modifier.width(6.dp))
            Text(
                "The developer asks",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                fontWeight = FontWeight.SemiBold,
                color = t.text3,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(asked, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp))
    }
}

/**
 * The page before the review has arrived: a skeleton of the step, the dial, its four labels and
 * its two lines of words, centred where the step will be, as every other page waits.
 */
@Composable
internal fun ReviewLoading(
    room: Dp,
    dial: Dp,
    gap: Dp,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = room - 64.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Getting your review ready" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Skeleton(width = dial, height = dial, corner = dial / 2)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(ReviewSteps.size) { Skeleton(width = 40.dp, height = 10.dp) }
        }
        Spacer(Modifier.height(gap))
        Skeleton(width = 180.dp, height = 22.dp, corner = 8.dp)
        Spacer(Modifier.height(10.dp))
        Skeleton(width = 260.dp)
        Spacer(Modifier.height(6.dp))
        Skeleton(width = 200.dp)
    }
}

/** Under the Google Play step, for somebody who would rather use the phone's own screenshot keys. */
@Composable
internal fun SelfShotLink(onClick: () -> Unit) {
    Spacer(Modifier.height(10.dp))
    Text(
        "I'll take the screenshot myself",
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        color = Tt.colors.accentInk,
        style = MaterialTheme.typography.labelLarge,
    )
}
