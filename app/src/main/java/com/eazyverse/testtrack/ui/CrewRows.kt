package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The crew tab's card, which is Circle's card: the shared hairline tile with [padding] inside it.
 * Two tabs of one app that draw their own boxes are two apps, and the only thing that should tell a
 * crew card from a Circle card is what is written in it.
 */
@Composable
fun CrewCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .tileFrame(onClick = onClick)
            .padding(padding),
        content = content,
    )
}
