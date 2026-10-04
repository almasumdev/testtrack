package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.cos
import kotlin.math.sin

/**
 * The robot, working on it: the app's own loading state.
 *
 * Built from the same layers as the header badge (body, head, eyes, antenna, hands) but with
 * nothing in its hands except two arrows chasing each other round in a small glow, as if it were
 * spinning the thing up for you. The head breathes and the eyes blink like they do in the header,
 * the whole robot hovers over a shadow that tightens as it rises. With animations off on the phone
 * it stands still and the arrows stop, so nothing moves that the person asked not to move.
 */
@Composable
fun RobotLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    label: String? = null,
) {
    val t = Tt.colors
    RobotFigure(modifier, size, label ?: "Loading", label) { center, shine, turn ->
        // A light orb held between the hands, so the arrows read on the dark body in either
        // theme, with a halo that breathes behind it.
        orb(center, shine, t.accentInk, t.glass)
        rotate(turn, center) {
            repeat(2) { i -> arrow(center, 110f, i * 180f, t.accentInk) }
        }
    }
}

/**
 * The robot holding something that says how a review stands, in place of the app icon's check
 * cards and arrows: an hourglass that turns over now and then while it waits, a tick once it is
 * approved. Same hover and blink as the loader, so it is plainly the same robot.
 */
@Composable
fun RobotHolding(
    icon: ImageVector,
    tone: Color,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    turning: Boolean = false,
    description: String? = null,
) {
    val t = Tt.colors
    val painter = rememberVectorPainter(icon)
    val loop = rememberInfiniteTransition(label = "Robot holding")
    // Still for a while, then over in half a second: an hourglass being turned, not a spinner.
    val over by loop.animateFloat(
        0f,
        180f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 3200
                0f at 0
                0f at 2500
                180f at 3100 using FastOutSlowInEasing
                180f at 3200
            },
        ),
        label = "turn over",
    )
    val flip = if (turning && ValueAnimator.areAnimatorsEnabled()) over else 0f
    RobotFigure(modifier, size, description ?: "", null) { center, shine, _ ->
        orb(center, shine, tone, t.glass)
        rotate(flip, center) {
            val side = 190f
            translate(center.x - side / 2f, center.y - side / 2f) {
                with(painter) { draw(Size(side, side), colorFilter = ColorFilter.tint(tone)) }
            }
        }
    }
}

/**
 * The robot itself: body, breathing head, blinking eyes, swaying antenna, hovering over its
 * shadow, with [held] drawn between its hands in the badge's own 880 by 980 space.
 */
@Composable
private fun RobotFigure(
    modifier: Modifier,
    size: Dp,
    description: String,
    label: String?,
    held: DrawScope.(center: Offset, shine: Float, turn: Float) -> Unit,
) {
    val t = Tt.colors
    val moving = ValueAnimator.areAnimatorsEnabled()
    val loop = rememberInfiniteTransition(label = "Robot loader")
    val hover by loop.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover",
    )
    val spin by loop.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val glow by loop.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val blink by loop.animateFloat(
        1f,
        1f,
        infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 0
                1f at 3300
                0.08f at 3410
                1f at 3530
                1f at 4200
            },
        ),
        label = "blink",
    )
    val lift = if (moving) hover else 0f
    val turn = if (moving) spin else 30f
    val shine = if (moving) glow else 0.5f
    val lids = if (moving) blink else 1f
    val height = size * (980f / 880f)

    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size, height + size * 0.16f), contentAlignment = Alignment.BottomCenter) {
            // The shadow on the ground: smaller and fainter the higher the robot floats.
            Canvas(Modifier.size(size * 0.6f, size * 0.1f)) {
                val scale = 1f - lift * 0.3f
                drawOval(
                    color = t.shadow.copy(alpha = 0.18f - lift * 0.08f),
                    topLeft = Offset(this.size.width * (1 - scale) / 2f, 0f),
                    size = Size(this.size.width * scale, this.size.height),
                )
            }
            Box(
                Modifier
                    .size(size, height)
                    .graphicsLayer {
                        translationY = -(size * 0.1f).toPx() - lift * (size * 0.08f).toPx()
                    },
            ) {
                val headLift = Modifier.graphicsLayer { translationY = -lift * (size * 0.012f).toPx() }
                Image(painterResource(R.drawable.level_robot_body), null, Modifier.matchParentSize())
                Image(painterResource(R.drawable.level_robot_head), null, Modifier.matchParentSize().then(headLift))
                Image(
                    painterResource(R.drawable.level_robot_eyes),
                    null,
                    Modifier.matchParentSize().then(headLift).graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, (529f - 110f) / 980f)
                        scaleY = lids
                    },
                )
                Image(
                    painterResource(R.drawable.level_robot_antenna),
                    null,
                    Modifier.matchParentSize().then(headLift).graphicsLayer {
                        transformOrigin = TransformOrigin((627f - 185f) / 880f, (278f - 110f) / 980f)
                        rotationZ = (lift - 0.5f) * 8f
                    },
                )
                // Whatever it holds, where the badge's plaque sits.
                Canvas(Modifier.matchParentSize()) {
                    val factor = minOf(this.size.width / 880f, this.size.height / 980f)
                    withTransform({
                        translate((this.size.width - 880f * factor) / 2f, (this.size.height - 980f * factor) / 2f)
                        scale(factor, factor, Offset.Zero)
                        translate(-185f, -110f)
                    }) {
                        held(Offset(627f, 905f), shine, turn)
                    }
                }
                Image(painterResource(R.drawable.level_robot_hands), null, Modifier.matchParentSize())
            }
        }
        if (label != null) {
            Spacer(Modifier.height(10.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp), color = t.text3)
        }
    }
}

/** The light orb the robot holds things in, with a halo in [tone] that breathes behind it. */
private fun DrawScope.orb(
    center: Offset,
    shine: Float,
    tone: Color,
    fill: Color,
) {
    val halo = 240f + shine * 30f
    drawCircle(
        Brush.radialGradient(listOf(tone.copy(alpha = 0.32f + shine * 0.18f), tone.copy(alpha = 0f)), center, halo),
        halo,
        center,
    )
    drawCircle(fill, 172f, center)
    drawCircle(tone.copy(alpha = 0.35f), 172f, center, style = Stroke(10f))
}

/** One curved arrow: an arc of a circle from [start] degrees, with its head at the leading end. */
private fun DrawScope.arrow(
    center: Offset,
    radius: Float,
    start: Float,
    color: Color,
) {
    val sweep = 125f
    val stroke = 30f
    drawArc(
        color = color,
        startAngle = start,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(stroke, cap = StrokeCap.Round),
    )
    val end = Math.toRadians((start + sweep).toDouble())
    val tip = Offset(center.x + radius * cos(end).toFloat(), center.y + radius * sin(end).toFloat())
    // Tangent at the end of a clockwise arc, and the normal pointing outwards.
    val tangent = Offset(-sin(end).toFloat(), cos(end).toFloat())
    val normal = Offset(cos(end).toFloat(), sin(end).toFloat())
    val head = 46f
    val path =
        Path().apply {
            moveTo(tip.x + tangent.x * head * 0.9f, tip.y + tangent.y * head * 0.9f)
            lineTo(tip.x + normal.x * head * 0.75f, tip.y + normal.y * head * 0.75f)
            lineTo(tip.x - normal.x * head * 0.75f, tip.y - normal.y * head * 0.75f)
            close()
        }
    drawPath(path, color)
}
