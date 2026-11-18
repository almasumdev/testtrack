package com.eazyverse.testtrack.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/** A placeholder letter fits its icon, including tiny marks and large accessibility fonts. */
@Composable
internal fun AppInitial(
    name: String,
    size: Dp,
    color: Color,
) {
    val base = MaterialTheme.typography.titleMedium
    val side = with(LocalDensity.current) { size.toSp() }
    Text(
        name
            .trim()
            .firstOrNull()
            ?.uppercaseChar()
            ?.toString() ?: "?",
        style =
            base.copy(
                fontSize = minOf(base.fontSize.value, side.value * 0.5f).sp,
                lineHeight = minOf(base.lineHeight.value, side.value * 0.8f).sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
