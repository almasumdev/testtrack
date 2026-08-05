package com.eazyverse.testtrack.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
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
 * One accent for things you can act on, a separate set for things that happened.
 *
 * Dynamic colour was removed deliberately. It made the app look different on every phone and
 * identical to every other Material app on each of them — and, worse, it painted the primary
 * action and a completed day the same hue, so a button and a result were indistinguishable.
 *
 * Iris sits outside the status range on purpose. Green already means *done* in every grid cell, so
 * it cannot also mean *press me*.
 */
private val Iris = Color(0xFF4F46E5)
private val IrisLight = Color(0xFF8B87F5)

private val LightColors = lightColorScheme(
    primary = Iris,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEECFD),
    onPrimaryContainer = Color(0xFF2E2792),
    secondary = Color(0xFF5B6478),
    onSecondary = Color.White,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF0B1020),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFEDF0F7),
    onSurfaceVariant = Color(0xFF5B6478),
    outline = Color(0xFF8E96AA),
    outlineVariant = Color(0xFFE2E6F0),
    error = Color(0xFFE11D48),
    onError = Color.White,
    errorContainer = Color(0xFFFCE9ED),
    onErrorContainer = Color(0xFF9F1239)
)

private val DarkColors = darkColorScheme(
    primary = IrisLight,
    onPrimary = Color(0xFF1A1745),
    primaryContainer = Color(0xFF1C1D3A),
    onPrimaryContainer = Color(0xFFD7D4FE),
    secondary = Color(0xFF98A2B8),
    onSecondary = Color(0xFF0B1020),
    background = Color(0xFF080C16),
    onBackground = Color(0xFFEEF1F8),
    surface = Color(0xFF101626),
    onSurface = Color(0xFFEEF1F8),
    surfaceVariant = Color(0xFF182034),
    onSurfaceVariant = Color(0xFF98A2B8),
    outline = Color(0xFF6B7692),
    outlineVariant = Color(0xFF232B41),
    error = Color(0xFFFB7185),
    onError = Color(0xFF3D0A17),
    errorContainer = Color(0xFF31151F),
    onErrorContainer = Color(0xFFFECDD3)
)

/**
 * State colours. Material has no slot for "reported but under the bar", so they live here and are
 * read through [com.eazyverse.testtrack.ui.Status].
 */
data class StatusColors(
    val posted: Color,
    val postedSoft: Color,
    val short: Color,
    val shortSoft: Color,
    val missed: Color,
    val missedSoft: Color,
    val upcoming: Color,
    val neutralSoft: Color
)

private val LightStatus = StatusColors(
    posted = Color(0xFF059669),
    postedSoft = Color(0xFFE2F4ED),
    short = Color(0xFFC2670A),
    shortSoft = Color(0xFFFBF0E1),
    missed = Color(0xFFE11D48),
    missedSoft = Color(0xFFFCE9ED),
    upcoming = Color(0xFFE3E7F0),
    neutralSoft = Color(0xFFEDF0F7)
)

private val DarkStatus = StatusColors(
    posted = Color(0xFF34D399),
    postedSoft = Color(0xFF0E2A21),
    short = Color(0xFFFBBF24),
    shortSoft = Color(0xFF2C2210),
    missed = Color(0xFFFB7185),
    missedSoft = Color(0xFF31151F),
    upcoming = Color(0xFF212940),
    neutralSoft = Color(0xFF182034)
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/**
 * TestTrack is a dark app.
 *
 * A committed look beats one that changes with the phone: the palette was chosen and checked
 * against a dark ground, and a tool people open at the same time every evening for a fortnight
 * should look the same every time they open it. The light scheme above is complete and correct —
 * pass `darkTheme = isSystemInDarkTheme()` here to follow the system instead.
 */
private const val ALWAYS_DARK = true

@Composable
fun TestTrackTheme(
    darkTheme: Boolean = ALWAYS_DARK || isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors

    // Android 15+ forces edge-to-edge, so the bars are transparent and show the app's own
    // background through them. Their icons are drawn by the system and default to light, which is
    // invisible on our paper ground — the appearance flags are the only way to darken them.
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

    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
