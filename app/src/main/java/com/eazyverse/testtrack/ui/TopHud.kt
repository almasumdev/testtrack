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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.domain.grouped
import com.eazyverse.testtrack.ui.theme.Barlow
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
 * there is a shop, this is where it goes: a second figure beside the balance.
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
    /** Opens the You page; the balance is the tap target. */
    onOpenYou: () -> Unit = {},
    /** Opens the level sheet from the robot and the level lines. Not wired yet, so null. */
    onOpenLevel: (() -> Unit)? = null,
    /** A level-up nobody has looked at yet: a small dot on the robot. */
    levelNews: Boolean = false,
    /** An optional update Play has for this install; null when there is none. */
    update: UpdateOffer? = null,
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
    if (LocalDensity.current.fontScale > 1.2f) {
        Column(modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LevelRobotBadge(visibleLevel, intoLevel, forNext, onSignIn = onSignIn.takeIf { !signedIn }, news = levelNews)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // Less space after it than it looks: the update button's touch area takes 4 of it.
                if (update != null) {
                    UpdateChip(update)
                    Spacer(Modifier.width(4.dp))
                }
                if (visibleLevel != null) {
                    TixFigure(tix, onOpenYou)
                } else if (signedIn) {
                    TixLoading()
                } else {
                    GoogleButton("Sign in", size = GoogleButtonSize.COMPACT, onClick = onSignIn)
                }
            }
        }
        return
    }
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LevelRobotBadge(visibleLevel, intoLevel, forNext, onSignIn = onSignIn.takeIf { !signedIn }, news = levelNews)
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
        if (update != null) {
            UpdateChip(update)
            Spacer(Modifier.width(6.dp))
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
                2 -> TixFigure(tix, onOpenYou)
                1 -> TixLoading()
                else -> GoogleButton("Sign in", size = GoogleButtonSize.COMPACT, onClick = onSignIn)
            }
        }
    }
}

/**
 * What this account has to spend: a drawn ticket, tipped a little, then the figure in plain
 * numbers. No pill and no frame, so it reads as part of the bar and not as a button sitting in it.
 * Tapping it opens the You page.
 *
 * Nothing moves at rest, and nothing animates on arrival: the first composition sets the figure
 * and stops, because a balance that counts itself up every time a tab opens is a slot machine.
 * The one moment worth marking is the balance changing. Then the digits roll from the old figure
 * to the new one, the ticket hops and turns over once, and the amount floats up off the figure and
 * fades, green for tix earned and quiet for tix spent.
 */
@Composable
private fun TixFigure(
    tix: Int,
    onOpenYou: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    val countRoom = with(LocalDensity.current) { TixCountRoom.toDp() }

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

    Box(
        modifier
            .height(48.dp)
            .clickable(role = Role.Button, onClickLabel = "Open your page", onClick = onOpenYou),
        contentAlignment = Alignment.CenterStart,
    ) {
        // Read as one thing, the balance and its unit, rather than a bare number and a picture.
        Row(
            Modifier
                .padding(start = 6.dp)
                .clearAndSetSemantics { contentDescription = "${grouped(tix)} tix" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Ticket(hop)
            Spacer(Modifier.width(7.dp))
            Text(
                grouped(shown),
                Modifier.widthIn(min = countRoom).graphicsLayer {
                    // A little swell while it counts, settling as the roll ends.
                    val swell = 1f + 0.16f * sin(PI.toFloat() * (1f - hop.value).coerceIn(0f, 1f)).coerceAtLeast(0f)
                    scaleX = swell
                    scaleY = swell
                },
                style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                fontSize = 18.sp,
                fontFamily = Barlow,
                fontWeight = FontWeight.SemiBold,
                color = t.text,
                textAlign = TextAlign.Center,
            )
        }
        if (rise.value < 1f && change != 0) {
            Text(
                if (change > 0) "+${grouped(change)}" else grouped(change),
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp)
                    .graphicsLayer {
                        val p = rise.value
                        translationY = -22.dp.toPx() * p
                        alpha = if (p < 0.2f) p / 0.2f else 1f - (p - 0.2f) / 0.8f
                    },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                color = if (change > 0) t.ok else t.text3,
            )
        }
    }
}

/** The static ticket and the same quiet loading dots used by a list tile's unknown amount. */
@Composable
private fun TixLoading(modifier: Modifier = Modifier) {
    val countRoom = with(LocalDensity.current) { TixCountRoom.toDp() }
    Row(
        modifier
            .height(48.dp)
            .padding(start = 6.dp)
            .clearAndSetSemantics { contentDescription = "Loading tix balance" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ticket(null)
        Spacer(Modifier.width(7.dp))
        TixLoadingDots(Modifier.width(countRoom), color = Tt.colors.text)
    }
}

/** Reserve the same amount slot while loading and showing a normal balance, scaled with text. */
private val TixCountRoom = 36.sp

/** The ticket, resting at -12 degrees. [hop] runs the up, over once and back down. */
@Composable
private fun Ticket(hop: Animatable<Float, *>?) {
    val mark = rememberTicketMark()
    Image(
        mark,
        null,
        Modifier
            .size(22.dp)
            .graphicsLayer {
                val p = hop?.value ?: 1f
                translationY = -10.dp.toPx() * sin(PI.toFloat() * (p / 0.55f).coerceAtMost(1f))
                rotationZ = -12f + 360f * FastOutSlowInEasing.transform(((p - 0.15f) / 0.6f).coerceIn(0f, 1f))
                val pop = 1f + 0.15f * sin(PI.toFloat() * (p / 0.4f).coerceAtMost(1f))
                scaleX = pop
                scaleY = pop
            },
    )
}
