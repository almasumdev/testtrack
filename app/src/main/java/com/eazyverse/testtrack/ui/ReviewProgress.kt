package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.Download
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Star
import com.eazyverse.testtrack.ui.theme.Tt

/** A current-step ring and a connected record of completed steps, never a navigation control. */
@Composable
internal fun ReviewProgress(job: ReviewJob) {
    val t = Tt.colors
    val steps = ReviewSteps
    val here = steps.indexOf(job.step).coerceIn(0, steps.lastIndex)
    val reached by animateFloatAsState(
        targetValue = here.toFloat(),
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "review-path",
    )
    Row(
        Modifier.fillMaxWidth().drawBehind {
            val slot = size.width / steps.size
            val start = Offset(slot / 2, 24.dp.toPx())
            val stroke = 3.dp.toPx()
            drawLine(t.hair, start, Offset(size.width - slot / 2, start.y), stroke, StrokeCap.Round)
            if (reached > 0f) {
                drawLine(t.accentInk, start, Offset(start.x + slot * reached, start.y), stroke, StrokeCap.Round)
            }
        },
    ) {
        steps.forEachIndexed { index, step ->
            val current = index == here
            val done = index < here
            Column(
                Modifier.weight(1f).semantics(mergeDescendants = true) {
                    contentDescription = "Step ${index + 1}, ${stepShort(step, job)}, " +
                        when {
                            done -> "complete"
                            current -> "current"
                            else -> "not started"
                        }
                },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(48.dp).background(t.bg, CircleShape), contentAlignment = Alignment.Center) {
                    val mark =
                        when {
                            current ->
                                Modifier
                                    .size(48.dp)
                                    .padding(2.dp)
                                    .border(1.5.dp, t.accentInk, CircleShape)
                                    .padding(5.dp)
                                    .background(t.accent, CircleShape)
                            done -> Modifier.size(38.dp).background(t.leafAccentSoft, CircleShape)
                            else -> Modifier.size(36.dp).border(1.dp, t.hair, CircleShape)
                        }
                    Box(mark, contentAlignment = Alignment.Center) {
                        Icon(
                            if (done) Lucide.Check else reviewStepIcon(step, job.live),
                            null,
                            Modifier.size(if (current || done) 19.dp else 17.dp),
                            tint =
                                if (current) {
                                    t.onAccent
                                } else if (done) {
                                    t.accentInk
                                } else {
                                    t.text4
                                },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "${index + 1}. ${stepShort(step, job)}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Medium,
                    color =
                        if (current) {
                            t.text
                        } else if (done) {
                            t.accentInk
                        } else {
                            t.text4
                        },
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun reviewStepIcon(
    step: ReviewStep,
    live: Boolean,
): ImageVector =
    when (step) {
        ReviewStep.INSTALL -> Lucide.Download
        ReviewStep.USE -> Lucide.Smartphone
        ReviewStep.LEAVE -> if (live) Lucide.Star else Lucide.MessageSquare
        ReviewStep.PROVE -> Lucide.Camera
        ReviewStep.WAIT -> Lucide.Check
    }
