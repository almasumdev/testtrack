package com.eazyverse.testtrack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/** One soft Ocean light field, shared across the app and echoed by the launcher artwork. */
@Composable
fun Modifier.exchangeBackground(variant: Int = 0): Modifier {
    val dark = Tt.colors.isDark
    return drawWithCache {
        val colors =
            if (dark) {
                arrayOf(
                    0f to Color(0xFF21394A),
                    .32f to Color(0xFF182B39),
                    .72f to Color(0xFF101A23),
                    1f to Color(0xFF0F1011),
                )
            } else {
                arrayOf(
                    0f to Color(0xFFF7FCFF),
                    .32f to Color(0xFFE7F4FC),
                    .72f to Color(0xFFB8D8EB),
                    1f to Color(0xFF7FAAC7),
                )
            }
        val wash =
            Brush.radialGradient(
                *colors,
                center = Offset(size.width * (.36f + (variant % 3) * .01f), size.height * .29f),
                radius = (maxOf(size.width, size.height) * .90f).coerceAtLeast(1f),
            )
        val unit = minOf(size.width, 600.dp.toPx())
        val glass = if (dark) Color(0xFF8BCBF7).copy(alpha = .045f) else Color.White.copy(alpha = .23f)
        val rim = if (dark) Color(0xFF8BCBF7).copy(alpha = .10f) else Color.White.copy(alpha = .48f)
        val outline = Stroke(1.dp.toPx())
        // Fixed irregular scatter, with no symbols or checks.
        val shapes =
            listOf(
                floatArrayOf(0.060f, 0.080f, 0.160f, -19.000f, 0.000f),
                floatArrayOf(0.590f, 0.035f, 0.100f, 0.000f, 1.000f),
                floatArrayOf(0.860f, 0.200f, 0.220f, 24.000f, 0.000f),
                floatArrayOf(0.370f, 0.240f, 0.130f, -32.000f, 2.000f),
                floatArrayOf(-0.070f, 0.360f, 0.210f, 0.000f, 1.000f),
                floatArrayOf(0.640f, 0.430f, 0.180f, 17.000f, 0.000f),
                floatArrayOf(0.180f, 0.540f, 0.120f, 34.000f, 0.000f),
                floatArrayOf(0.910f, 0.590f, 0.150f, -28.000f, 2.000f),
                floatArrayOf(0.430f, 0.690f, 0.090f, 0.000f, 1.000f),
                floatArrayOf(-0.040f, 0.780f, 0.180f, -12.000f, 0.000f),
                floatArrayOf(0.740f, 0.840f, 0.200f, 29.000f, 0.000f),
                floatArrayOf(0.290f, 0.940f, 0.160f, -23.000f, 2.000f),
            )
        onDrawBehind {
            drawRect(wash)
            clipRect {
                for (shape in shapes) {
                    val x = shape[0] * size.width
                    val y = shape[1] * size.height
                    val width = shape[2] * unit
                    val kind = shape[4].toInt()
                    val height = if (kind == 2) width * .38f else width
                    val radius = if (kind == 0) width * .25f else width / 2
                    rotate(shape[3], Offset(x + width / 2, y + height / 2)) {
                        drawRoundRect(glass, Offset(x, y), Size(width, height), CornerRadius(radius))
                        drawRoundRect(rim, Offset(x, y), Size(width, height), CornerRadius(radius), style = outline)
                    }
                }
            }
        }
    }
}
