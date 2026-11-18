package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/** Measured usage only: the animation catches up to new observations, never advances the clock. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReviewUsageProgress(job: ReviewJob) {
    val t = Tt.colors
    val totalSeconds = (job.review.neededMs.coerceAtLeast(0) / 1000).toInt()
    val usedSeconds = (totalSeconds - job.secondsLeft).coerceIn(0, totalSeconds)
    val progress by animateFloatAsState(
        targetValue = job.usedFraction,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "review-use-progress",
    )
    val sweep = remember { Animatable(1f) }
    var lastUsed by remember(job.review.id) { mutableLongStateOf(job.review.usedMs) }
    LaunchedEffect(job.review.id, job.review.usedMs) {
        val advanced = job.review.usedMs > lastUsed
        lastUsed = job.review.usedMs
        if (advanced) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        }
    }
    val fill = t.accent.copy(alpha = if (t.isDark) 0.3f else 0.2f).compositeOver(t.track)
    val figure =
        MaterialTheme.typography.labelLarge
            .tabular()
            .toSpanStyle()
    Column(
        Modifier.fillMaxWidth().padding(vertical = ReviewLineGap),
        verticalArrangement = Arrangement.spacedBy(ReviewLineGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(11.dp))
                .drawBehind {
                    drawRect(t.track)
                    val filledWidth = size.width * progress
                    clipRect(right = filledWidth) {
                        drawRect(fill)
                        // One gentle sheen when measured use increases; no looping loading effect.
                        if (sweep.value < 1f && filledWidth > 0f) {
                            val band = size.width * 0.35f
                            val along = -band + (filledWidth + band) * sweep.value
                            drawRect(
                                Brush.linearGradient(
                                    listOf(t.bg.copy(alpha = 0f), t.bg.copy(alpha = 0.35f), t.bg.copy(alpha = 0f)),
                                    start = Offset(along, 0f),
                                    end = Offset(along + band, size.height),
                                ),
                            )
                        }
                    }
                }.clearAndSetSemantics {
                    contentDescription = "App usage, ${usageLeftLabel(usedSeconds)} used, ${usageLeftLabel(job.secondsLeft)} remaining"
                    progressBarRangeInfo = ProgressBarRangeInfo(job.usedFraction, 0f..1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            // Let the labels wrap inside the bar at large accessibility font sizes.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 13.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(figure) { append(usageClock(usedSeconds)) }
                        append(" used")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = t.text2,
                )
                Text(
                    buildAnnotatedString {
                        withStyle(figure) { append(usageClock(job.secondsLeft)) }
                        append(" left")
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = t.text,
                )
            }
        }
        Text(
            "${usageLeftLabel(totalSeconds)} of app use required",
            style = MaterialTheme.typography.bodySmall,
            color = t.text3,
        )
    }
}

private fun usageClock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
