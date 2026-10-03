package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Ticket
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

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
    if (signedIn && level == null) {
        Row(
            modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 11.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudMark()
            Spacer(Modifier.width(11.dp))
            Column {
                Text("TestTrack", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Signed in", style = MaterialTheme.typography.labelSmall, color = Tt.colors.text2)
            }
        }
        return
    }
    if (level == null) {
        SignedOutHud(modifier, openCrew, openTaken, openSeats, onSignIn)
        return
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = Gutter, end = Gutter, top = 10.dp, bottom = 6.dp),
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
            .padding(start = Gutter, end = Gutter, top = 11.dp, bottom = 7.dp),
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
                        color = Tt.colors.accentInk,
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

        SignInChip(onSignIn)
    }
}

/**
 * The way in, at the size a header can carry.
 *
 * A full button was too much furniture for a strip that also holds a mark and two lines of text.
 *
 * It does not say "sign in". The Google mark beside it already says how, in the space that word
 * would have taken, so the word itself is free to say why. "Get started" is also the honest one:
 * signing in does not get anybody a seat, it opens the app up, and the seat is a separate thing
 * they do with an app of their own afterwards. "Join", next to a line about a crew filling, would
 * have promised the seat.
 *
 * Outlined rather than filled. The one filled thing in this app is the accent, and the accent is
 * for what somebody is doing here rather than for the door.
 */
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
            "Get started",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The complete exchange robot, tightly framed for the app bar. */
@Composable
private fun HudMark() {
    Image(painterResource(R.drawable.testtrack_robot), null, Modifier.size(MarkSize))
}

private val MarkSize = 44.dp

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
    val accent = Tt.colors.accentInk

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

/**
 * What this account has to spend, opposite [FeedTabs] at the top of Circle.
 *
 * A filled shape with no border, one step off the page. Bare it read as unfinished beside a solid
 * pill; bordered and matched to that pill it took more of the row than the number was worth. Fill
 * without an outline is the middle: it has an edge, and the edge costs a tone rather than a line.
 *
 * The fill is [ColorScheme.surfaceVariant] and not the primary container, which was the other
 * candidate and is the wrong one for a reason worth writing down: the selected tab beside it is
 * already painted in the primary container. Two things on one row in the same fill would say they
 * are the same kind of thing, and one of them is a state you chose while the other is a number
 * you were given.
 *
 * A resting glow was tried here and taken out. Anything drawn outside the digits gets cut by the
 * bounds of the row it sits in, and the whole point of a halo is that it has no edge, so a halo
 * with a straight line through it is worse than none. Light that is always on says nothing anyway.
 *
 * What replaced it happens at the only moment worth marking: the balance changing. The digits roll
 * from the old figure to the new one rather than swapping, so twenty tix earned is twenty
 * tix seen going in, and the ticket pops and both brighten for a beat and settle. All of it is
 * inside the type and the icon, so there is nothing to clip.
 *
 * Nothing animates on arrival. The first composition sets the figure and stops, because a balance
 * that counts itself up every time you open the tab is a slot machine.
 */
@Composable
private fun TixChip(
    tix: Int,
    modifier: Modifier = Modifier,
) {
    val accent = Tt.colors.accentInk

    // Long enough to read as counting, short enough not to be waited on.
    val shown by animateIntAsState(
        targetValue = tix,
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "tix",
    )

    // Full the instant the number moves, then decaying. Compared against the last value seen
    // rather than keyed on the first composition, so opening the screen is not a change.
    val flare = remember { Animatable(0f) }
    var seen by remember { mutableStateOf(tix) }
    LaunchedEffect(tix) {
        if (tix != seen) {
            seen = tix
            flare.snapTo(1f)
            flare.animateTo(0f, tween(durationMillis = 900, easing = LinearOutSlowInEasing))
        }
    }

    val lit = lerp(accent, Tt.colors.onAccent, 0.4f * flare.value)

    Row(
        modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Lucide.Ticket,
            null,
            Modifier
                .size(13.dp)
                // Scaled from its own centre, so the pop stays inside the line height the text
                // beside it already sets and the fill never has to grow to hold it.
                .graphicsLayer {
                    val pop = 1f + 0.4f * flare.value
                    scaleX = pop
                    scaleY = pop
                },
            tint = lit,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            "$shown",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = lit,
        )
    }
}
