package com.eazyverse.testtrack.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's page background: one plain colour, the same as the launch screen ground.
 *
 * [variant] is kept so call sites stay as they are. It no longer changes anything.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Modifier.exchangeBackground(variant: Int = 0): Modifier = background(Tt.colors.bg)
