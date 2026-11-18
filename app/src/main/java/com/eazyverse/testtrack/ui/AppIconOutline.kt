package com.eazyverse.testtrack.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** A separate frame outside the artwork. Transparent logo details never become a status stroke. */
internal fun Modifier.appIconOutline(
    color: Color,
    contour: IconContour? = null,
): Modifier =
    if (color == Color.Unspecified) {
        this
    } else {
        drawWithCache {
            val stroke = Stroke(1.dp.toPx())
            // A half-dp gap preserves every artwork pixel and still fits the strip's 4dp spacing.
            val outset = 0.5.dp.toPx() + stroke.width / 2
            val frameSize = Size(size.width + outset * 2, size.height + outset * 2)
            val radius = size.minDimension * if (contour?.isRoundIcon() == true) 0.5f else 0.3f
            onDrawWithContent {
                drawRoundRect(
                    color,
                    topLeft = Offset(-outset, -outset),
                    size = frameSize,
                    cornerRadius = CornerRadius(radius + outset),
                    style = stroke,
                )
                // The artwork is drawn last, so even an unusual opaque corner cannot be tinted.
                drawContent()
            }
        }
    }
