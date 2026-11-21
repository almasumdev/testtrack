package com.eazyverse.testtrack.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's page background: one plain colour, the same as the launch screen ground.
 */
@Composable
fun Modifier.exchangeBackground(): Modifier = background(Tt.colors.bg)
