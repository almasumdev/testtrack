package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Level and balance, on every screen.
 *
 * Drawn on the page's own background with no rule under it. A line there would cut the app in two
 * every time somebody looked at it, and this is meant to read as the top of the screen rather than
 * as a bar sitting on top of one.
 *
 * Experience is the same number the board ranks by. It is earned by opening apps and by reviews
 * that were accepted, nothing spends it, and it is the server's arithmetic throughout: the two
 * numbers below are how far into the level and how wide the level is, so nothing here can decide
 * somebody is further along than the server thinks they are.
 *
 * Gold is not drawn, and there is deliberately no switch here for it. There is no way to buy any,
 * so a balance somebody cannot change would be a question on every screen the app has no answer
 * to, and the first thing anybody would do is go looking for the shop that is not there. When
 * there is a shop, this is where it goes: a second chip beside the balance.
 */
@Composable
fun TopHud(
    level: Int?,
    intoLevel: Int,
    forNext: Int,
    tix: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRing(level, intoLevel, forNext)
        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            Text(
                if (level == null) "TestTrack" else "Level $level",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(1.dp))
            Text(
                when {
                    level == null -> "Sign in to start earning"
                    forNext <= 0 -> "$intoLevel exp"
                    else -> "${forNext - intoLevel} exp to level ${level + 1}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (level != null) {
            TixChip(tix)
        }
    }
}

/**
 * The level, with its progress as the ring around it.
 *
 * A ring rather than a bar because it belongs to the number in the middle of it: one object saying
 * one thing. A bar beside a number is two things the eye has to put together.
 */
@Composable
private fun LevelRing(
    level: Int?,
    intoLevel: Int,
    forNext: Int,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    // Guarded rather than trusted. forNext is zero until the first answer arrives, and dividing by
    // it would put a NaN into the sweep, which draws nothing at all and looks like a broken ring.
    val target = if (forNext > 0) (intoLevel.toFloat() / forNext).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "exp",
    )

    Box(Modifier.size(RingSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(RingSize)) {
            val width = RingStroke.toPx()
            val inset = width / 2
            val box = size.width - width
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(box, box),
                style = Stroke(width),
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    // Twelve o'clock, so a ring that has barely started still reads as started.
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(box, box),
                    style = Stroke(width, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            level?.toString() ?: "?",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color =
                if (level == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

private val RingSize = 42.dp
private val RingStroke = 3.5.dp
