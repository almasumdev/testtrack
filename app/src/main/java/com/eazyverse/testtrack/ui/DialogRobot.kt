package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.R

/*
 * The robot that looks over the top of every dialog.
 *
 * Its own drawings, unchanged: the head, eyes and antenna the level badge and the empty pages
 * use, and its two mitts. Drawn as two layers either side of the card. The head goes behind, so
 * the card hides everything below its top edge and only the face rises above it; the mitts go in
 * front, laid over that edge, holding it. Nothing is cropped or redrawn, which is what keeps the
 * shape the robot has everywhere else.
 */

/** How wide the robot is drawn: the whole 880 unit artboard at this width. */
internal val DialogRobotWidth = 96.dp

/** The artboard's height at [DialogRobotWidth]. */
private val Frame = DialogRobotWidth * (980f / 880f)

/** How far above the card's edge the robot's frame starts: the edge falls just under the smile. */
internal val DialogRobotRise = DialogRobotWidth * (540f / 880f)

/** The mitts are drawn at the robot's waist; this lifts them to the card's edge. */
private val HandsLift = DialogRobotWidth * (262f / 880f)

/**
 * The head, behind the card. [looking] sends the eyes from side to side, for while something is
 * being checked; otherwise the head only drifts a little, as the robot does on every page.
 */
@Composable
internal fun DialogRobotHead(
    looking: Boolean,
    modifier: Modifier = Modifier,
) {
    val moving = ValueAnimator.areAnimatorsEnabled()
    val loop = rememberInfiniteTransition(label = "Dialog robot")
    val drift by loop.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val glance by loop.animateFloat(
        -1f,
        1f,
        infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glance",
    )
    val lift = if (moving) drift else 0f
    Box(modifier.size(DialogRobotWidth, Frame).graphicsLayer { translationY = -lift * 2.dp.toPx() }) {
        Image(painterResource(R.drawable.level_robot_antenna), null, Modifier.size(DialogRobotWidth, Frame))
        Image(painterResource(R.drawable.level_robot_head), null, Modifier.size(DialogRobotWidth, Frame))
        Image(
            painterResource(R.drawable.level_robot_eyes),
            null,
            Modifier.size(DialogRobotWidth, Frame).graphicsLayer {
                translationX = if (looking && moving) glance * Glance.toPx() else 0f
            },
        )
    }
}

/** The two mitts, in front of the card, over its top edge. */
@Composable
internal fun DialogRobotHands(modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.level_robot_hands),
        null,
        modifier.size(DialogRobotWidth, Frame).graphicsLayer { translationY = -HandsLift.toPx() },
    )
}

/** How far the eyes travel each way: inside the face plate, never past it. */
private val Glance: Dp = 2.4.dp
