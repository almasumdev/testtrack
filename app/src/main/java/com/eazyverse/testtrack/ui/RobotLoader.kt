package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.PI
import kotlin.math.sin

/**
 * The robot, waiting with you: the app's own loading state.
 *
 * It hovers, leaning a little into each rise, over a shadow that tightens as it lifts, with three
 * dots under it taking turns. A mascot doing something reads as "on its way" in a way a shimmer of
 * grey bars on a page that is otherwise ours never did. With animations off on the phone it stands
 * still and the dots stay lit, so nothing moves that the person asked not to move.
 */
@Composable
fun RobotLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    label: String? = null,
) {
    val t = Tt.colors
    val moving = ValueAnimator.areAnimatorsEnabled()
    val loop = rememberInfiniteTransition(label = "Robot loader")
    val hover by loop.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "hover",
    )
    val beat by loop.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "dots",
    )
    val lift = if (moving) hover else 0f

    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = label ?: "Loading" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size, size * 1.18f), contentAlignment = Alignment.BottomCenter) {
            // The shadow on the ground: smaller and fainter the higher the robot floats.
            Canvas(Modifier.size(size * 0.62f, size * 0.12f)) {
                val scale = 1f - lift * 0.28f
                drawOval(
                    color = t.shadow.copy(alpha = 0.16f - lift * 0.07f),
                    topLeft = Offset(this.size.width * (1 - scale) / 2f, 0f),
                    size = Size(this.size.width * scale, this.size.height),
                )
            }
            Image(
                painterResource(R.drawable.testtrack_robot),
                null,
                Modifier
                    .size(size)
                    .graphicsLayer {
                        translationY = -(size * 0.06f).toPx() - lift * (size * 0.1f).toPx()
                        rotationZ = if (moving) (lift - 0.5f) * 6f else 0f
                    },
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            repeat(3) { i ->
                // Each dot peaks a third of a beat after the one before it.
                val phase = ((beat - i / 3f) % 1f + 1f) % 1f
                val glow = if (moving) ((sin(phase * 2 * PI) + 1) / 2).toFloat() else 1f
                Box(
                    Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            scaleX = 0.75f + glow * 0.35f
                            scaleY = 0.75f + glow * 0.35f
                            alpha = 0.35f + glow * 0.65f
                        }.clip(CircleShape)
                        .background(t.accentInk),
                )
            }
        }
        if (label != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = t.text3,
            )
        }
    }
}
