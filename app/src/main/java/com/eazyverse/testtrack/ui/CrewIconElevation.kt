package com.eazyverse.testtrack.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

/** A small lift behind the slot, without a border, mask, or recoloring the app artwork. */
internal fun Modifier.crewIconElevation(tint: Color): Modifier =
    if (tint == Color.Unspecified) {
        this
    } else {
        shadow(
            elevation = 3.dp,
            shape = RectangleShape,
            clip = false,
            ambientColor = tint.copy(alpha = 0.3f),
            spotColor = tint.copy(alpha = 0.55f),
        )
    }
