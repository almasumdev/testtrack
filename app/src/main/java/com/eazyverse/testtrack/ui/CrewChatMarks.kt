package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

// The chat's small marks: the bot's face, times, the loading shape, dashed edges and text sizes.

@Composable
internal fun TimeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Tt.colors.text4,
) {
    if (text.isBlank()) return
    Text(
        text,
        modifier,
        style = TimeStyle(),
        fontWeight = FontWeight.Medium,
        color = color,
        maxLines = 1,
    )
}

/** The square of the mascot's 880 by 980 artboard a bot face shows: the head, ears and antenna. */
private const val HEAD_SIDE = 860f

/**
 * The bot's face: the app's own robot, its head and antenna as the mascot's drawings have them.
 * The name still comes from the server, but never a picture, so the bot looks the same everywhere.
 */
@Composable
internal fun BotFace(size: Dp) {
    val unit = size / HEAD_SIDE
    // The head spans x 30 to 854 and y 17 to 633 of the artboard; (442, 325) is its middle.
    val art =
        Modifier
            .requiredSize(unit * 880f, unit * 980f)
            .offset(x = unit * (440f - 442f), y = unit * (490f - 325f))
    Box(Modifier.size(size).clipToBounds(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.level_robot_antenna), null, art)
        Image(painterResource(R.drawable.level_robot_head), null, art)
        Image(painterResource(R.drawable.level_robot_eyes), null, art)
    }
}

/** The chat's loading shape: faces and bubbles of mixed widths, others' on the left, yours on the right. */
@Composable
internal fun ChatSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        listOf(0 to 0.62f, 0 to 0.44f, 1 to 0.52f, 0 to 0.7f, 1 to 0.4f, 0 to 0.55f, 1 to 0.64f, 0 to 0.46f).forEach { (mine, share) ->
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = if (mine == 1) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.Bottom,
            ) {
                if (mine == 0) {
                    Skeleton(width = 28.dp, height = 28.dp, corner = 14.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Skeleton(height = 38.dp, corner = 18.dp, modifier = Modifier.fillMaxWidth(share))
            }
        }
    }
}

/** A dashed edge, for what is gone: the deleted stub, an expired picture, a team post. */
internal fun Modifier.dashed(
    color: Color,
    corner: Dp,
    width: Dp = 1.dp,
    shape: RoundedCornerShape? = null,
): Modifier =
    drawBehind {
        val stroke = Stroke(width.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
        val outline = (shape ?: RoundedCornerShape(corner)).createOutline(size, layoutDirection, this)
        drawOutline(outline, color, style = stroke)
    }

/** The chat's text sizes, from the body style. */
@Composable
internal fun chatStyle(
    size: Float,
    line: Float,
) = MaterialTheme.typography.bodyMedium.copy(fontSize = size.sp, lineHeight = line.sp)

/** A message's time: small, in the figures' own face. */
@Composable
private fun TimeStyle() =
    MaterialTheme.typography.labelSmall
        .tabular()
        .copy(fontSize = 10.5.sp, lineHeight = 14.sp)
