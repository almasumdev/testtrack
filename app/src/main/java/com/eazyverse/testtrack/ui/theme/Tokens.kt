package com.eazyverse.testtrack.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Every colour this app draws, in one place.
 *
 * An Ocean palette matched to the app background: ice-blue cards in light, layered navy
 * surfaces in dark, and one blue that is
 * spent only on things you can act on. Nothing outside this file writes a colour literal; screens
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
    val washTop: Color,
    val washMiddle: Color,
    val washLower: Color,
    val washBottom: Color,
    val shapeFill: Color,
    val shapeRim: Color,
    val text: Color,
    /** Secondary text: the line under a title. */
    val text2: Color,
    /** Labels, hints, section eyebrows. */
    val text3: Color,
    /** The faintest ink: chevrons, placeholder glyphs. */
    val text4: Color,
    val tabSelected: Color,
    val tabUnselected: Color,
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
    /** The tix counter: a raised navy pill, lighter at the top, standing on a darker edge. */
    val tixTop: Color,
    val tixBottom: Color,
    val tixEdge: Color,
    /** The navy the tix ticket's edge is drawn in (res/drawable/tix_ticket), for a number on it. */
    val ticketInk: Color,
    /** A soft gold light behind the ticket where a request shows what it pays. */
    val ticketGlow: Color,
) {
    val primary: Color get() = if (isDark) accentInk else accent
    val onPrimary: Color get() = if (isDark) bg else onAccent
    val selectedFill: Color get() = if (isDark) leafAccentSoft else leafAccent
    val onSecondary: Color get() = if (isDark) bg else onSelected

    /**
     * Words and icons on a solid [fill]. White reads on the deep fills; on a pale one, the mint
     * `ok` at night above all, white was about 1.6:1, so a pale fill gets the theme's dark ink.
     */
    fun inkOn(fill: Color): Color = if (fill.luminance() > 0.4f) (if (isDark) bg else text) else onAccent

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

private val OkLight = Color(0xFF246B59)
private val WarnLight = Color(0xFF8A611C)
private val BadLight = Color(0xFF9E3B2E)
private val OkDark = Color(0xFF83D8C4)
private val WarnDark = Color(0xFFD8B774)
private val BadDark = Color(0xFFE4938A)

val OceanLight =
    TtTokens(
        isDark = false,
        bg = Color(0xFFE9F0F4),
        washTop = Color(0xFFF7FCFF),
        washMiddle = Color(0xFFE7F4FC),
        washLower = Color(0xFFB8D8EB),
        washBottom = Color(0xFF7FAAC7),
        shapeFill = Color.White.alpha(.23f),
        shapeRim = Color.White.alpha(.48f),
        text = Color(0xFF142F43),
        text2 = Color(0xFF203A4E),
        text3 = Color(0xFF223D50),
        text4 = Color(0xFF59758A),
        tabSelected = Color(0xFF0C527D),
        tabUnselected = Color(0xFF526B7D),
        glass = Color(0xFFF2F9FD),
        glass2 = Color(0xFFDFEDF6),
        glassBorder = Color(0xFFB7D1E2),
        glassBorderStrong = Color(0xFF789AAF),
        hair = Color(0xFFB7D1E2),
        accent = Color(0xFF1D5C82),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF123C56),
        badgeFill = Color(0xFF1D5C82),
        tile = Color(0xFFD5EAF7),
        tileInk = Color(0xFF1D5C82),
        leafAccent = Color(0xFF1D5C82),
        leafAccentSoft = Color(0xFF32729B).alpha(0.16f),
        onSelected = Color(0xFFFFFFFF),
        dock = Color(0xFFE8F3FA),
        dockInk = Color(0xFF304C60),
        contour = Color(0xFFC8DDEA),
        contourDeep = Color(0xFFABCADD),
        glow = Color(0xFFF5F9FB),
        ok = OkLight,
        okBg = OkLight.alpha(0.12f),
        warn = WarnLight,
        warnBg = Color(0xFF9A6E24).alpha(0.12f),
        warnBorder = Color(0xFF9A6E24).alpha(0.24f),
        bad = BadLight,
        badBg = BadLight.alpha(0.12f),
        badBorder = BadLight.alpha(0.24f),
        onWarn = Color(0xFFFFFFFF),
        onBad = Color(0xFFFFFFFF),
        field = Color(0xFFF7FCFF),
        fieldBorder = Color(0xFF789AAF),
        scrim = Color(0xFF142230).alpha(0.46f),
        container = Color(0xFFD5EAF7),
        onContainer = Color(0xFF174866),
        surface2 = Color(0xFFEAF4FB),
        surface3 = Color(0xFFC7DEED),
        shadow = Color(0xFF102F46),
        inverse = Color(0xFF172F42),
        onInverse = Color(0xFFEFF7FC),
        inverseAccent = Color(0xFF9CCBEA),
        panelTop = Color(0xFFF7FCFF),
        panelBottom = Color(0xFFE3F0F8),
        panelGlow = Color(0xFFB5D6EA).alpha(0.60f),
        panelLine = Color(0xFF32729B).alpha(0.22f),
        panelOutline = Color(0xFF94B8CF),
        gold = Color(0xFF9A7B1F),
        silver = Color(0xFF6B7488),
        bronze = Color(0xFF8A5A34),
        tixTop = Color(0xFF2B6F99),
        tixBottom = Color(0xFF1B4F70),
        tixEdge = Color(0xFF143A55),
        ticketInk = Color(0xFF17405E),
        ticketGlow = Color(0xFFF6C445),
    )

val OceanDark =
    TtTokens(
        isDark = true,
        bg = Color(0xFF0F1011),
        washTop = Color(0xFF21394A),
        washMiddle = Color(0xFF182B39),
        washLower = Color(0xFF101A23),
        washBottom = Color(0xFF0F1011),
        shapeFill = Color(0xFF8BCBF7).alpha(.045f),
        shapeRim = Color(0xFF8BCBF7).alpha(.10f),
        text = Color(0xFFEFF7FC),
        text2 = Color(0xFFBDD1DF),
        text3 = Color(0xFFA1BDCF),
        text4 = Color(0xFF7F9EB3),
        tabSelected = Color(0xFF9CD5FF),
        tabUnselected = Color(0xFF93A7B6),
        glass = Color(0xFF1B3040),
        glass2 = Color(0xFF263F51),
        glassBorder = Color(0xFF365568),
        glassBorderStrong = Color(0xFF58788E),
        hair = Color(0xFF365568),
        accent = Color(0xFF32729B),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF8BCBF7),
        badgeFill = Color(0xFF32729B),
        tile = Color(0xFF24485F),
        tileInk = Color(0xFF8BCBF7),
        leafAccent = Color(0xFF8BCBF7),
        leafAccentSoft = Color(0xFF8BCBF7).alpha(0.12f),
        onSelected = Color(0xFF8BCBF7),
        dock = Color(0xFF172C3C),
        dockInk = Color(0xFFBDD1DF),
        contour = Color(0xFF1B3040),
        contourDeep = Color(0xFF263F51),
        glow = Color(0xFF21394A),
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
        field = Color(0xFF152A39),
        fieldBorder = Color(0xFF58788E),
        scrim = Color(0xFF000000).alpha(0.60f),
        container = Color(0xFF24485F),
        onContainer = Color(0xFFD8EEFC),
        surface2 = Color(0xFF203849),
        surface3 = Color(0xFF304D61),
        shadow = Color(0xFF07131D),
        inverse = Color(0xFFE8F3FA),
        onInverse = Color(0xFF142F43),
        inverseAccent = Color(0xFF1D5C82),
        panelTop = Color(0xFF243F52),
        panelBottom = Color(0xFF1B3040),
        panelGlow = Color(0xFF8BCBF7).alpha(0.08f),
        panelLine = Color(0xFF8BCBF7).alpha(0.10f),
        panelOutline = Color(0xFF42647A),
        gold = Color(0xFFE3A93C),
        silver = Color(0xFFB4BCCB),
        bronze = Color(0xFFC58455),
        tixTop = Color(0xFF3A7FAA),
        tixBottom = Color(0xFF235C80),
        tixEdge = Color(0xFF0E2A3F),
        // The ticket is the same at night, as the drawable is.
        ticketInk = Color(0xFF17405E),
        ticketGlow = Color(0xFFF6C445),
    )

val LocalTokens = staticCompositionLocalOf { OceanDark }

/** The tokens, from anywhere in a composable: `Tt.colors.text3`. */
object Tt {
    val colors: TtTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTokens.current
}
