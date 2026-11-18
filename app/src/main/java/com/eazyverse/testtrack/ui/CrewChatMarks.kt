package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

// The chat's small marks: the bot's face, times, the loading shape, dashed edges and text sizes.

@Composable
internal fun TimeText(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return
    Text(
        text,
        modifier,
        style = TimeStyle(),
        fontWeight = FontWeight.Medium,
        color = Tt.colors.text4,
    )
}

/** The bot's face: the server's picture when it sends one, else the robot head in the theme's colours. */
@Composable
internal fun BotFace(
    avatar: String,
    size: Dp,
) {
    if (avatar.startsWith("https://")) {
        Avatar(avatar, "B", size = size)
    } else {
        BotHead(size)
    }
}

/** The robot's head as the crew pages draw it, from the theme's own colours. */
@Composable
internal fun BotHead(size: Dp) {
    val t = Tt.colors
    Canvas(Modifier.size(size)) {
        val u = this.size.width / 80f

        fun p(
            x: Float,
            y: Float,
        ) = Offset((x - 20f) * u, (y - 2f) * u)
        drawLine(t.tileInk, p(60f, 10f), p(60f, 26f), strokeWidth = 4f * u)
        drawCircle(t.warn, 6f * u, p(60f, 9f))
        drawRoundRect(t.tile, p(26f, 26f), Size(68f * u, 52f * u), CornerRadius(18f * u))
        drawRoundRect(t.tileInk, p(26f, 26f), Size(68f * u, 52f * u), CornerRadius(18f * u), style = Stroke(4f * u))
        drawRoundRect(t.bg, p(36f, 38f), Size(48f * u, 28f * u), CornerRadius(13f * u))
        drawCircle(t.text, 5f * u, p(50f, 52f))
        drawCircle(t.text, 5f * u, p(70f, 52f))
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
