package com.eazyverse.testtrack.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.User
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.ui.theme.Tt

// The chat's small marks: the bot's face, dashed edges and text sizes.

/** How wide the head is on the mascot's 880 by 980 artboard: x 30 to 854, antenna and ears in. */
private const val HEAD_WIDTH = 824f

/** How much of the circle's width the head fills, so it still reads at 20dp. */
private const val HEAD_FILL = 0.8f

/**
 * The bot's face: the app's own robot, its head and antenna as the mascot's drawings have them,
 * on a soft round of the accent. The name still comes from the server, but never a picture, so the
 * bot looks the same everywhere.
 */
@Composable
internal fun BotFace(size: Dp) {
    val unit = size * HEAD_FILL / HEAD_WIDTH
    // The head spans x 30 to 854 and y 17 to 633 of the artboard; (442, 325) is its middle.
    val art =
        Modifier
            .requiredSize(unit * 880f, unit * 980f)
            .offset(x = unit * (440f - 442f), y = unit * (490f - 325f))
    Box(
        Modifier.size(size).clip(CircleShape).background(Tt.colors.botRound),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.level_robot_antenna), null, art)
        Image(painterResource(R.drawable.level_robot_head), null, art)
        Image(painterResource(R.drawable.level_robot_eyes), null, art)
    }
}

/**
 * The anonymous person: a soft round with the person glyph, for someone with no photo or name to
 * show. The same face as the thread's person mark and the review faces' disc.
 */
@Composable
internal fun PersonDisc(
    size: Dp,
    modifier: Modifier = Modifier,
    /** The figure's size: 0.56 of the disc unless given. */
    glyph: Dp = size * 0.56f,
    /** The figure's ink: the tile's unless given, such as faded for a review under way. */
    ink: Color = Tt.colors.tileInk,
) {
    val t = Tt.colors
    Box(
        modifier.size(size).clip(CircleShape).background(t.tile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Lucide.User, null, Modifier.size(glyph), tint = ink)
    }
}

/** A dashed edge, round a deleted message. */
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
