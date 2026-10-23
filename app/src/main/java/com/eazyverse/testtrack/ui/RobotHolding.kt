package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

/**
 * The robot holding the cartoon thing for a page's case (see [Prop]): a megaphone where nobody is
 * asking, a plug pulled out where it did not load, a seal once approved, gears while an update is
 * needed. The head breathes and the eyes blink like they do in the header, and the whole robot
 * hovers over a shadow that tightens as it rises. With animations off on the phone it stands
 * still, so nothing moves that the person asked not to move.
 */
@Composable
fun RobotHolding(
    prop: Prop,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    description: String? = null,
) = RobotFigure(modifier, size, description ?: "", prop)

/**
 * The robot itself: body, breathing head, blinking eyes, swaying antenna, hovering over its
 * shadow, with [prop] between its hands, each of its layers on the one shared clock.
 */
@Composable
private fun RobotFigure(
    modifier: Modifier,
    size: Dp,
    description: String,
    prop: Prop,
) {
    val t = Tt.colors
    val moving = ValueAnimator.areAnimatorsEnabled()
    // Read only while drawing, so the robot is redrawn each frame without being recomposed.
    val time = robotTime(moving)
    val lift = { if (moving) pingPong(time.longValue, 1400) else 0f }
    val lids = { if (moving) blinkAt(time.longValue, 4200, 3300) else 1f }
    val clock = { if (moving) (time.longValue % PROP_CYCLE.toLong()).toFloat() else 0f }
    val height = size * (980f / 880f)

    Column(
        modifier.semantics(mergeDescendants = true) { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(size, height + size * 0.16f), contentAlignment = Alignment.BottomCenter) {
            // The shadow on the ground: smaller and fainter the higher the robot floats.
            Canvas(Modifier.size(size * 0.6f, size * 0.1f)) {
                val up = lift()
                val scale = 1f - up * 0.3f
                drawOval(
                    color = t.shadow.copy(alpha = 0.18f - up * 0.08f),
                    topLeft = Offset(this.size.width * (1 - scale) / 2f, 0f),
                    size = Size(this.size.width * scale, this.size.height),
                )
            }
            Box(
                Modifier
                    .size(size, height)
                    .graphicsLayer {
                        translationY = -(size * 0.1f).toPx() - lift() * (size * 0.08f).toPx()
                    },
            ) {
                Image(painterResource(R.drawable.level_robot_body), null, Modifier.matchParentSize())
                // The head breathes, and nods along when the scene asks it to, about the neck.
                Box(
                    Modifier.matchParentSize().graphicsLayer {
                        translationY = -lift() * (size * 0.012f).toPx()
                        transformOrigin = TransformOrigin(0.5f, (720f - 110f) / 980f)
                        prop.nod?.run { at(clock(), this@graphicsLayer.size.width / 880f) }
                    },
                ) {
                    Image(painterResource(R.drawable.level_robot_head), null, Modifier.matchParentSize())
                    val face = prop.face
                    if (face != null) {
                        Image(
                            painterResource(face),
                            null,
                            Modifier.matchParentSize().graphicsLayer {
                                prop.faceMotion?.run { at(clock(), this@graphicsLayer.size.width / 880f) }
                            },
                        )
                    } else {
                        Image(
                            painterResource(R.drawable.level_robot_eyes),
                            null,
                            Modifier.matchParentSize().graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, (529f - 110f) / 980f)
                                scaleY = lids()
                            },
                        )
                    }
                    if (prop.antenna) {
                        Image(
                            painterResource(R.drawable.level_robot_antenna),
                            null,
                            Modifier.matchParentSize().graphicsLayer {
                                transformOrigin = TransformOrigin((627f - 185f) / 880f, (278f - 110f) / 980f)
                                rotationZ = (lift() - 0.5f) * 8f
                            },
                        )
                    }
                    prop.head.forEach { PropImage(it, clock) }
                }
                // What it holds, between the head and the hands so the hands close over it.
                prop.layers.forEach { PropImage(it, clock) }
                Image(painterResource(R.drawable.level_robot_hands), null, Modifier.matchParentSize())
            }
        }
    }
}

/** One layer of a prop, filling the robot's box, moving on [clock] (read only while drawing). */
@Composable
private fun BoxScope.PropImage(
    layer: PropLayer,
    clock: () -> Float,
) = Image(
    painterResource(layer.res),
    null,
    Modifier.matchParentSize().graphicsLayer {
        transformOrigin = TransformOrigin((layer.px - 185f) / 880f, (layer.py - 110f) / 980f)
        layer.motion?.run { at(clock(), this@graphicsLayer.size.width / 880f) }
    },
)
