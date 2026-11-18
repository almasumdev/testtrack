package com.eazyverse.testtrack.ui

import android.animation.ValueAnimator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** A quiet staggered pulse for an unknown amount, on the same clock as the loading cards. */
@Composable
internal fun TixLoadingDots(
    modifier: Modifier = Modifier,
    color: Color = Tt.colors.tabSelected,
) {
    val moving = ValueAnimator.areAnimatorsEnabled()
    val time = robotTime(moving)
    Box(
        modifier.size(width = 24.dp, height = 20.dp).drawBehind {
            repeat(3) { index ->
                val alpha = if (moving) 0.25f + 0.65f * pingPong(time.longValue + index * 140L, half = 425) else 0.55f
                drawCircle(
                    color = color.copy(alpha = alpha),
                    radius = 1.5.dp.toPx(),
                    center = Offset(size.width / 2 + (index - 1) * 6.dp.toPx(), size.height / 2),
                )
            }
        },
    )
}
