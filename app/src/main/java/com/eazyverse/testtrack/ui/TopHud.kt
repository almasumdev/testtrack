package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
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
    openCrew: String? = null,
    openTaken: Int = 0,
    openSeats: Int = 0,
    onSignIn: () -> Unit = {},
) {
    if (level == null) {
        SignedOutHud(modifier, openCrew, openTaken, openSeats, onSignIn)
        return
    }
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
                "Level $level",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.size(1.dp))
            Text(
                if (forNext <= 0) {
                    "$intoLevel exp"
                } else {
                    "${forNext - intoLevel} exp to level ${level + 1}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        TixChip(tix)
    }
}

/**
 * The header before anybody has signed in.
 *
 * It used to draw the level ring with a question mark in it, which is a progress ring with no
 * progress: a shape that answers nothing and asks nothing, taking the most valuable strip of the
 * screen to do it.
 *
 * So it says the one true thing a stranger can be told, which happens to be the only question they
 * have. Somebody who has just installed this wants to know whether anybody is actually in here,
 * and a crew with fourteen of its fifteen seats taken answers that better than any sentence could.
 * The figures are live and read without a token, which is what the crew list was opened up for.
 *
 * With no crew filling there is nothing true to say, so it says nothing: the mark, the name, and
 * the way in. An empty statistic would be worse than the ring it replaced.
 */
@Composable
private fun SignedOutHud(
    modifier: Modifier,
    openCrew: String?,
    openTaken: Int,
    openSeats: Int,
    onSignIn: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudMark()
        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            if (openCrew != null && openSeats > 0) {
                Text(
                    "$openCrew is filling",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(1.dp))
                Row {
                    Text(
                        "$openTaken",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        " of $openSeats seats taken",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    "TestTrack",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Secondary("Sign in", onClick = onSignIn)
    }
}

/** The app mark at header size: a run of days, most of them kept. */
@Composable
private fun HudMark() {
    val ink = MaterialTheme.colorScheme.onPrimary
    val tint = MaterialTheme.colorScheme.primary

    Box(
        Modifier
            .size(MarkSize)
            .clip(RoundedCornerShape(12.dp))
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(19.dp)) {
            val cell = size.width * 0.28f
            val gap = size.width * 0.08f
            repeat(3) { row ->
                repeat(3) { col ->
                    // The last cell is the day still to come.
                    val pending = row == 2 && col == 2
                    drawRoundRect(
                        color = ink.copy(alpha = if (pending) 0.30f else 0.95f),
                        topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.34f),
                    )
                }
            }
        }
    }
}

private val MarkSize = 38.dp

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
