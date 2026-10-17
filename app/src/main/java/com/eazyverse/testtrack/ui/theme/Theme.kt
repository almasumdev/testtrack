package com.eazyverse.testtrack.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * State colours, read through [com.eazyverse.testtrack.ui.Status].
 *
 * Green, amber and red whatever the palette, each with a soft ground of its own for a chip. They
 * come from [TtTokens] like everything else, so there is still one place a colour is written.
 */
data class StatusColors(
    val posted: Color,
    val postedSoft: Color,
    val short: Color,
    val shortSoft: Color,
    val missed: Color,
    val missedSoft: Color,
    val upcoming: Color,
    val neutralSoft: Color,
)

private fun TtTokens.status() =
    StatusColors(
        posted = ok,
        postedSoft = okBg,
        short = warn,
        shortSoft = warnBg,
        missed = bad,
        missedSoft = badBg,
        upcoming = glass2,
        neutralSoft = glass2,
    )

val LocalStatusColors = staticCompositionLocalOf { OceanDark.status() }

/**
 * The tokens, poured into Material's slots.
 *
 * Only a mapping; no colour is decided here. Anything drawn by a stock Material component — a
 * dialog, a text field, a switch — picks up the palette through these, and everything drawn by
 * this app reads [Tt.colors] directly.
 */
private fun TtTokens.scheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = tile,
        onPrimaryContainer = tileInk,
        inversePrimary = inverseAccent,
        primaryFixed = tile,
        primaryFixedDim = container,
        onPrimaryFixed = tileInk,
        onPrimaryFixedVariant = onContainer,
        secondaryFixed = container,
        secondaryFixedDim = tile,
        onSecondaryFixed = onContainer,
        onSecondaryFixedVariant = tileInk,
        tertiaryFixed = warnBg,
        tertiaryFixedDim = warnBg,
        onTertiaryFixed = warn,
        onTertiaryFixedVariant = warn,
        secondary = leafAccent,
        onSecondary = onSecondary,
        secondaryContainer = container,
        onSecondaryContainer = onContainer,
        tertiary = warn,
        onTertiary = onWarn,
        tertiaryContainer = warnBg,
        onTertiaryContainer = warn,
        background = bg,
        onBackground = text,
        surface = glass,
        onSurface = text,
        surfaceVariant = glass2,
        onSurfaceVariant = text2,
        surfaceTint = Color.Transparent,
        inverseSurface = inverse,
        inverseOnSurface = onInverse,
        error = bad,
        onError = onBad,
        errorContainer = badBg,
        onErrorContainer = bad,
        outline = glassBorderStrong,
        outlineVariant = glassBorder,
        scrim = scrim,
        surfaceBright = glass,
        surfaceDim = bg,
        surfaceContainerLowest = glass,
        surfaceContainerLow = glass,
        surfaceContainer = surface2,
        surfaceContainerHigh = glass2,
        surfaceContainerHighest = surface3,
    )
}

/**
 * TestTrack in Value Guard's Ocean blue, light or dark.
 *
 * It was always dark, which suited a tool opened at the same hour every evening and nobody else.
 * The palette it follows now was built and checked in both: the phone's own setting decides,
 * unless Light or Dark was chosen on the You page (see ThemeChoice).
 */
@Composable
fun TestTrackTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) OceanDark else OceanLight

    // Android 15+ forces edge-to-edge, so the bars are transparent and show the app's own ground
    // through them. Their icons are drawn by the system; the appearance flags pick the ink.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalTokens provides tokens,
        LocalStatusColors provides tokens.status(),
    ) {
        MaterialTheme(colorScheme = tokens.scheme(), typography = TtTypography, content = content)
    }
}
