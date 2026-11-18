package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** One width measurement for a strip of square app slots, shared by Lobby, Run and their loaders. */
@Composable
internal fun CrewAppIconStrip(
    count: Int,
    description: String? = null,
    content: @Composable (Dp) -> Unit,
) {
    if (count <= 0) return
    BoxWithConstraints(
        Modifier.fillMaxWidth().then(
            if (description == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = description },
        ),
    ) {
        val side =
            with(LocalDensity.current) {
                ((constraints.maxWidth - 4.dp.roundToPx() * (count - 1)) / count)
                    .coerceIn(1, 24.dp.roundToPx())
                    .toDp()
            }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { content(side) }
    }
}
