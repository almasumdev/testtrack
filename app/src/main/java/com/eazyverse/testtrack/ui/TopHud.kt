package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

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
    signedIn: Boolean = level != null,
    openCrew: String? = null,
    openTaken: Int = 0,
    openSeats: Int = 0,
    onSignIn: () -> Unit = {},
) {
    val visibleLevel = level.takeIf { signedIn }
    val title =
        when {
            visibleLevel != null -> "Level $visibleLevel"
            signedIn -> "TestTrack"
            openCrew != null && openSeats > 0 -> "$openCrew is filling"
            else -> "TestTrack"
        }
    val subtitle =
        when {
            visibleLevel != null && forNext > 0 -> "${(forNext - intoLevel).coerceAtLeast(0)} exp to level ${visibleLevel + 1}"
            visibleLevel != null -> "$intoLevel exp"
            signedIn -> "Signed in"
            openCrew != null && openSeats > 0 -> "$openTaken of $openSeats seats taken"
            else -> ""
        }
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRobotBadge(visibleLevel, intoLevel, forNext, onSignIn = onSignIn.takeIf { !signedIn })
        Spacer(Modifier.width(11.dp))
        Crossfade(
            targetState = title to subtitle,
            modifier = Modifier.weight(1f),
            animationSpec = tween(220),
            label = "Header text",
        ) { text ->
            Column {
                Text(text.first, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                if (text.second.isNotEmpty()) {
                    Spacer(Modifier.size(1.dp))
                    Text(text.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Crossfade(
            targetState =
                if (visibleLevel !=
                    null
                ) {
                    2
                } else if (signedIn) {
                    1
                } else {
                    0
                },
            animationSpec = tween(220),
            label = "Header action",
        ) { state ->
            when (state) {
                2 -> TixChip(tix)
                0 -> SignInChip(onSignIn)
                else -> Unit
            }
        }
    }
}

/** Explicit Google sign-in action beside the guest robot. */
@Composable
private fun SignInChip(onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(percent = 50),
            ).clickable(onClick = onClick)
            .padding(start = 11.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleMark(Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Sign in",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * What this account has to spend, as a game would show it: a gold ticket over the end of a
 * raised navy pill, with the balance in white.
 *
 * The ticket hangs off the pill's left end rather than sitting inside it, the way a coin counter
 * in a game wears its coin, and it is drawn in the robot's cartoon style so the two belong to the
 * same app. The pill stands on a darker edge a few points below it, which is what makes it read
 * as a thing rather than a label.
 *
 * Nothing moves at rest, and nothing animates on arrival: the first composition sets the figure
 * and stops, because a balance that counts itself up every time a tab opens is a slot machine.
 * The one moment worth marking is the balance changing. Then the digits roll from the old figure
 * to the new one, the ticket hops and turns over once, and the amount floats up off the pill and
 * fades, green for tix earned and quiet for tix spent.
 */
@Composable
private fun TixChip(
    tix: Int,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors

    // Long enough to read as counting, short enough not to be waited on.
    val shown by animateIntAsState(
        targetValue = tix,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "tix",
    )

    // Compared against the last value seen rather than keyed on the first composition, so
    // opening the screen is not a change.
    val hop = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(tix) }
    var change by remember { mutableStateOf(0) }
    LaunchedEffect(tix) {
        if (tix != seen) {
            change = tix - seen
            seen = tix
            if (ValueAnimator.areAnimatorsEnabled()) {
                launch {
                    hop.snapTo(0f)
                    hop.animateTo(1f, tween(800, easing = LinearEasing))
                }
                rise.snapTo(0f)
                rise.animateTo(1f, tween(1100, easing = LinearOutSlowInEasing))
            }
        }
    }

    val shape = RoundedCornerShape(percent = 50)
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .padding(start = 16.dp, bottom = 3.dp)
                // The edge it stands on, drawn under the pill and 3dp lower.
                .drawBehind {
                    drawRoundRect(
                        t.tixEdge,
                        topLeft = Offset(0f, 3.dp.toPx()),
                        size = size,
                        cornerRadius = CornerRadius(size.height / 2f),
                    )
                }.clip(shape)
                .background(Brush.verticalGradient(listOf(t.tixTop, t.tixBottom)))
                .border(2.dp, t.tixEdge, shape)
                .padding(start = 27.dp, end = 13.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$shown",
                Modifier.graphicsLayer {
                    // A little swell while it counts, settling as the roll ends.
                    val swell = 1f + 0.16f * sin(PI.toFloat() * (1f - hop.value).coerceIn(0f, 1f)).coerceAtLeast(0f)
                    scaleX = swell
                    scaleY = swell
                },
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.ExtraBold,
                color = t.onAccent,
            )
        }
        Image(
            painterResource(R.drawable.tix_ticket),
            null,
            Modifier
                .padding(bottom = 3.dp)
                .size(width = 38.dp, height = 26.dp)
                .graphicsLayer {
                    // Up, over once and back down onto the pill.
                    val p = hop.value
                    translationY = -10.dp.toPx() * sin(PI.toFloat() * (p / 0.55f).coerceAtMost(1f))
                    rotationZ = -12f + 360f * FastOutSlowInEasing.transform(((p - 0.15f) / 0.6f).coerceIn(0f, 1f))
                    val pop = 1f + 0.15f * sin(PI.toFloat() * (p / 0.4f).coerceAtMost(1f))
                    scaleX = pop
                    scaleY = pop
                },
        )
        if (rise.value < 1f && change != 0) {
            Text(
                if (change > 0) "+$change" else "$change",
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp)
                    .graphicsLayer {
                        val p = rise.value
                        translationY = -6.dp.toPx() - 22.dp.toPx() * p
                        alpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (change > 0) t.ok else t.text3,
            )
        }
    }
}
