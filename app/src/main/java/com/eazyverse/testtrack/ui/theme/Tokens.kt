package com.eazyverse.testtrack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour this app draws, in one place.
 *
 * Taken from Value Guard's Ocean palette so the apps read as one family: a mist blue ground with
 * near white cards in light, a charcoal ground with one-step-up cards in dark, and one blue that is
 * spent only on things you can act on. Nothing outside this folder writes a colour literal; screens
 * read a role from here through [Tt.colors] or through the Material scheme mapped from it in
 * [TestTrackTheme]. scripts/check_app.py fails the build on a `Color(0x` anywhere else.
 *
 * The names are roles, not hues. `text3` is "a label or a hint", whatever it happens to be in this
 * palette, so a palette can change without a single screen being edited.
 */
@Immutable
data class TtTokens(
    val isDark: Boolean,
    /** The ground under everything. Cards sit on it and are lighter (light) or one step up (dark). */
    val bg: Color,
    val text: Color,
    /** Secondary text: the line under a title. */
    val text2: Color,
    /** Labels, hints, section eyebrows. */
    val text3: Color,
    /** The faintest ink: chevrons, placeholder glyphs. */
    val text4: Color,
    /** A card or sheet. */
    val glass: Color,
    /** A control sitting on a card: a soft button, an unselected chip, a meter's empty track. */
    val glass2: Color,
    /** The 1dp edge round a card. */
    val glassBorder: Color,
    /** An edge that has to be seen: a switch outline, a sheet handle. */
    val glassBorderStrong: Color,
    val hair: Color,
    /** The one fill for things you act on: the main button, a switch, a filled meter box. */
    val accent: Color,
    val onAccent: Color,
    /** The accent as ink, for text and icons. Lighter than [accent] in dark so it stays legible. */
    val accentInk: Color,
    val badgeFill: Color,
    /** An icon's rounded-square tile, and the glyph on it. */
    val tile: Color,
    val tileInk: Color,
    /** The selected tab or chip. */
    val leafAccent: Color,
    val leafAccentSoft: Color,
    val onSelected: Color,
    /** The floating tab bar and its icons. */
    val dock: Color,
    val dockInk: Color,
    /** The contour lines on the ground, and the light behind them. */
    val contour: Color,
    val contourDeep: Color,
    val glow: Color,
    val ok: Color,
    val okBg: Color,
    val warn: Color,
    val warnBg: Color,
    val warnBorder: Color,
    val bad: Color,
    val badBg: Color,
    val badBorder: Color,
    val onWarn: Color,
    val onBad: Color,
    val field: Color,
    val fieldBorder: Color,
    val scrim: Color,
    val container: Color,
    val onContainer: Color,
    val surface2: Color,
    val surface3: Color,
    val shadow: Color,
    val inverse: Color,
    val onInverse: Color,
    val inverseAccent: Color,
    /** A read-only panel: a soft diagonal gradient, a glow in one corner, a dashed edge. */
    val panelTop: Color,
    val panelBottom: Color,
    val panelGlow: Color,
    val panelLine: Color,
    val panelOutline: Color,
    /** The podium. The one place this app reaches for colours outside the palette. */
    val gold: Color,
    val silver: Color,
    val bronze: Color,
) {
    /**
     * An app's letter tile, tinted from its name.
     *
     * Hue comes off the name so it is stable with nothing stored; saturation and lightness are
     * fixed per theme so every tile weighs the same and they look like a set. Pale tiles with a
     * deep letter in light, deep tiles with a pale letter in dark.
     */
    fun nameTile(hue: Float): Pair<Color, Color> =
        if (isDark) {
            Color.hsl(hue, 0.30f, 0.18f) to Color.hsl(hue, 0.55f, 0.76f)
        } else {
            Color.hsl(hue, 0.45f, 0.89f) to Color.hsl(hue, 0.50f, 0.32f)
        }
}

private fun Color.alpha(value: Float) = copy(alpha = value)

private val OkLight = Color(0xFF2E7040)
private val WarnLight = Color(0xFF8A611C)
private val BadLight = Color(0xFF9E3B2E)
private val OkDark = Color(0xFFA5D69B)
private val WarnDark = Color(0xFFD8B774)
private val BadDark = Color(0xFFE4938A)

val OceanLight =
    TtTokens(
        isDark = false,
        bg = Color(0xFFE9F0F4),
        text = Color(0xFF18242E),
        text2 = Color(0xFF4A5B68),
        text3 = Color(0xFF566875),
        text4 = Color(0xFF8392A0),
        glass = Color(0xFFFAFCFD),
        glass2 = Color(0xFFDAE5EC),
        glassBorder = Color(0xFFCCDBE4),
        glassBorderStrong = Color(0xFFA0B3C0),
        hair = Color(0xFFCCDBE4),
        accent = Color(0xFF1D5C82),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF1D5C82),
        badgeFill = Color(0xFF32729B),
        tile = Color(0xFFD3E4EF),
        tileInk = Color(0xFF1D5C82),
        leafAccent = Color(0xFF32729B),
        leafAccentSoft = Color(0xFF32729B).alpha(0.16f),
        onSelected = Color(0xFFFFFFFF),
        dock = Color(0xFFDAE5EC),
        dockInk = Color(0xFF1F2B36),
        contour = Color(0xFFC8DDEA),
        contourDeep = Color(0xFFABCADD),
        glow = Color(0xFFF5F9FB),
        ok = OkLight,
        okBg = Color(0xFF4E9150).alpha(0.12f),
        warn = WarnLight,
        warnBg = Color(0xFF9A6E24).alpha(0.12f),
        warnBorder = Color(0xFF9A6E24).alpha(0.24f),
        bad = BadLight,
        badBg = BadLight.alpha(0.12f),
        badBorder = BadLight.alpha(0.24f),
        onWarn = Color(0xFFFFFFFF),
        onBad = Color(0xFFFFFFFF),
        field = Color(0xFFFAFCFD),
        fieldBorder = Color(0xFFCCDBE4),
        scrim = Color(0xFF142230).alpha(0.46f),
        container = Color(0xFFD6E7F2),
        onContainer = Color(0xFF12344B),
        surface2 = Color(0xFFF0F5F8),
        surface3 = Color(0xFFCBDAE4),
        shadow = Color(0xFF0F1C26),
        inverse = Color(0xFF223140),
        onInverse = Color(0xFFECF2F6),
        inverseAccent = Color(0xFF9CCBEA),
        panelTop = Color(0xFFFFFFFF),
        panelBottom = Color(0xFFEEF5F9),
        panelGlow = Color(0xFFB5D6EA).alpha(0.60f),
        panelLine = Color(0xFF32729B).alpha(0.22f),
        panelOutline = Color(0xFF94B8CF),
        gold = Color(0xFF9A7B1F),
        silver = Color(0xFF6B7488),
        bronze = Color(0xFF8A5A34),
    )

val OceanDark =
    TtTokens(
        isDark = true,
        bg = Color(0xFF0F1011),
        text = Color(0xFFF7F8F8),
        text2 = Color(0xFFB4B7BD),
        text3 = Color(0xFF8F949C),
        text4 = Color(0xFF7A7F87),
        glass = Color(0xFF17181A),
        glass2 = Color(0xFF1F2023),
        glassBorder = Color(0xFF2A2C30),
        glassBorderStrong = Color(0xFF3F4247),
        hair = Color(0xFF2A2C30),
        accent = Color(0xFF2F7DAD),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF8BCBF7),
        badgeFill = Color(0xFF2F7DAD),
        tile = Color(0xFF1A2831),
        tileInk = Color(0xFF8BCBF7),
        leafAccent = Color(0xFF8BCBF7),
        leafAccentSoft = Color(0xFF8BCBF7).alpha(0.12f),
        onSelected = Color(0xFF8BCBF7),
        dock = Color(0xFF1F2023),
        dockInk = Color(0xFFF7F8F8),
        contour = Color(0xFF1B1D1F),
        contourDeep = Color(0xFF232528),
        glow = Color(0xFF141516),
        ok = OkDark,
        okBg = OkDark.alpha(0.12f),
        warn = WarnDark,
        warnBg = WarnDark.alpha(0.14f),
        warnBorder = WarnDark.alpha(0.26f),
        bad = BadDark,
        badBg = BadDark.alpha(0.15f),
        badBorder = BadDark.alpha(0.30f),
        onWarn = Color(0xFF2A1D04),
        onBad = Color(0xFF3F0D06),
        field = Color(0xFF17181A),
        fieldBorder = Color(0xFF2A2C30),
        scrim = Color(0xFF000000).alpha(0.60f),
        container = Color(0xFF1A2831),
        onContainer = Color(0xFFF7F8F8),
        surface2 = Color(0xFF1B1C1E),
        surface3 = Color(0xFF2A2C30),
        shadow = Color(0xFF000000),
        inverse = Color(0xFFF7F8F8),
        onInverse = Color(0xFF0F1011),
        inverseAccent = Color(0xFF1D5C82),
        panelTop = Color(0xFF1D1E21),
        panelBottom = Color(0xFF17181A),
        panelGlow = Color(0xFF8BCBF7).alpha(0.08f),
        panelLine = Color(0xFF8BCBF7).alpha(0.10f),
        panelOutline = Color(0xFF3F4247),
        gold = Color(0xFFE3A93C),
        silver = Color(0xFFB4BCCB),
        bronze = Color(0xFFC58455),
    )

val LocalTokens = staticCompositionLocalOf { OceanDark }

/** The tokens, from anywhere in a composable: `Tt.colors.text3`. */
object Tt {
    val colors: TtTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTokens.current
}
