package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import android.graphics.Paint
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

/** Original mascot, holding one level plaque. Only the upper robot moves at rest. */
@Composable
internal fun LevelRobotBadge(
    level: Int?,
    intoLevel: Int,
    forNext: Int,
    onSignIn: (() -> Unit)? = null,
) {
    val target = if (level != null && forNext > 0) (intoLevel.toFloat() / forNext).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(target, tween(700), label = "Robot XP")
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    val motion = robotMotion(lifecycle.isAtLeast(Lifecycle.State.STARTED) && ValueAnimator.areAnimatorsEnabled())
    val celebration = remember { Animatable(0f) }
    var previousLevel by remember { mutableStateOf(level) }
    LaunchedEffect(level) {
        val leveledUp = level != null && previousLevel != null && level > previousLevel!!
        previousLevel = level
        if (leveledUp && ValueAnimator.areAnimatorsEnabled()) {
            celebration.animateTo(1f, tween(180))
            celebration.animateTo(0f, tween(420))
        }
    }
    val badgeAlpha by animateFloatAsState(if (level != null) 1f else 0f, tween(300), label = "Signed-in badge")
    var displayedLevel by remember { mutableStateOf(level) }
    LaunchedEffect(level) { if (level != null) displayedLevel = level }
    val colors = Tt.colors
    val signInMark =
        remember {
            PathParser()
                .parsePathString(
                    "M651 833 H699 Q717 833 717 851 V959 Q717 977 699 977 H651 M537 905 H657 M609 857 L657 905 L609 953",
                ).toPath()
        }
    val context = LocalContext.current
    val paint =
        remember(context) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = ResourcesCompat.getFont(context, R.font.barlow_semibold)
            }
        }
    val outline =
        remember {
            PathParser()
                .parsePathString(
                    "M316 905 V844 A78 78 0 0 1 394 766 H860 A78 78 0 0 1 938 844 V966 A78 78 0 0 1 860 1044 H394 A78 78 0 0 1 316 966 V905",
                ).toPath()
        }
    val frame =
        remember {
            PathParser()
                .parsePathString(
                    "M392 752 H862 Q952 752 952 842 V968 Q952 1058 862 1058 H392 Q302 1058 302 968 V842 Q302 752 392 752 Z",
                ).toPath()
        }
    val face =
        remember {
            PathParser()
                .parsePathString(
                    "M393 777 H861 Q927 777 927 843 V967 Q927 1033 861 1033 H393 Q327 1033 327 967 V843 Q327 777 393 777 Z",
                ).toPath()
        }
    val measure = remember(outline) { PathMeasure().apply { setPath(outline, false) } }
    val earned = remember { Path() }
    Box(
        Modifier
            .size(44.dp, 50.dp)
            .then(
                if (onSignIn != null) Modifier.clickable(role = Role.Button, onClickLabel = "Sign in", onClick = onSignIn) else Modifier,
            ).semantics(mergeDescendants = true) {
                contentDescription =
                    if (onSignIn !=
                        null
                    ) {
                        "Sign in to TestTrack"
                    } else if (level ==
                        null
                    ) {
                        "TestTrack robot"
                    } else {
                        "Level $level, $intoLevel of $forNext experience"
                    }
                if (level != null) progressBarRangeInfo = ProgressBarRangeInfo(target, 0f..1f)
            },
    ) {
        Image(painterResource(R.drawable.level_robot_body), null, Modifier.matchParentSize())
        Image(
            painterResource(R.drawable.level_robot_head),
            null,
            Modifier.matchParentSize().graphicsLayer { translationY = -motion.breathe * 0.7.dp.toPx() - celebration.value * 1.5.dp.toPx() },
        )
        Image(
            painterResource(R.drawable.level_robot_eyes),
            null,
            Modifier.matchParentSize().graphicsLayer {
                translationY = -motion.breathe * 0.7.dp.toPx() - celebration.value * 1.5.dp.toPx()
                transformOrigin = TransformOrigin(0.5f, (529f - 110f) / 980f)
                scaleY = motion.blink
            },
        )
        Image(
            painterResource(R.drawable.level_robot_antenna),
            null,
            Modifier.matchParentSize().graphicsLayer {
                translationY = -motion.breathe * 0.7.dp.toPx() - celebration.value * 1.5.dp.toPx()
                transformOrigin = TransformOrigin((627f - 185f) / 880f, (278f - 110f) / 980f)
                rotationZ = motion.breathe * 3f
            },
        )
        Canvas(Modifier.matchParentSize()) {
            val factor = minOf(size.width / 880f, size.height / 980f)
            withTransform({
                translate((size.width - 880f * factor) / 2f, (size.height - 980f * factor) / 2f)
                scale(factor, factor, Offset.Zero)
                translate(-185f, -110f)
            }) {
                drawPath(frame, colors.badgeFill)
                drawPath(face, colors.glass)
                drawPath(outline, colors.glassBorderStrong, alpha = badgeAlpha, style = Stroke(19f))
                earned.reset()
                measure.getSegment(0f, measure.length * progress, earned)
                drawPath(earned, colors.ok, alpha = badgeAlpha, style = Stroke(19f, cap = StrokeCap.Round))
                drawPath(
                    signInMark,
                    colors.accentInk,
                    alpha = 1f - badgeAlpha,
                    style = Stroke(22f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                paint.color = colors.text.toArgb()
                paint.alpha = (255 * badgeAlpha).toInt()
                paint.textSize = if ((displayedLevel ?: 0) < 100) 186f else 150f
                val baseline = 910f - (paint.ascent() + paint.descent()) / 2f
                drawContext.canvas.nativeCanvas.drawText(displayedLevel?.toString().orEmpty(), 627f, baseline, paint)
            }
        }
        Image(painterResource(R.drawable.level_robot_hands), null, Modifier.matchParentSize())
    }
}

private data class RobotMotion(
    val breathe: Float = 0f,
    val blink: Float = 1f,
)

@Composable
private fun robotMotion(active: Boolean): RobotMotion {
    if (!active) return RobotMotion()
    // On the robots' shared clock, so the header robot does not start over on every tab.
    val ms = robotTime().longValue
    val breathe = -1f + 2f * pingPong(ms, 2400)
    val blink = blinkAt(ms, 5600, 4400)
    return RobotMotion(breathe, blink)
}
