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
 * An Ocean palette matched to the app background: ice-blue cards in light, neutral grey
 * surfaces in dark with a light blue accent, and one blue that is
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
    /** The ground under everything, one plain colour. Same as the launch screen ground (res splash_background). */
    val bg: Color,
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
    /** The streak of light across the ticket where a request shows what it pays. */
    val ticketShine: Color,
    /** The sign-in sheet's own surface, and the hairline along its top edge. */
    val sheet: Color,
    val sheetLine: Color,
    /** The edge of the Google pill and of the account row on that sheet. */
    val sheetOutline: Color,
    /** The faint ring round an avatar, so a pale photo still has an edge. */
    val ring: Color,
    /** The empty run of a thin progress bar. */
    val track: Color,
    /** A quiet note card on the sign-in sheet, and its edge. */
    val card: Color,
    val cardLine: Color,
    /** The closed-test flask on a tile's corner: its liquid, and the glass outline round it. */
    val flaskLiquid: Color,
    val flaskGlass: Color,
    /** Behind a picture shown whole, the ink over it, and the dark pill a time sits on over a picture. */
    val photo: Color,
    val onPhoto: Color,
    val photoScrim: Color,
    /** A problem said over a picture in the picture editor, light enough to read on [photo]. */
    val photoBad: Color,
    /** The soft round of the accent behind the chat bot's face. */
    val botRound: Color,
    /** A faint wash of the accent over someone's bubble that names you. */
    val mentionTint: Color,
    /** The pen and word colours in the picture editor. They mark the picture, so they never change with the theme. */
    val pens: List<Color>,
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

/** The four facets of the Google Play logo. A brand mark, so the same in light and dark. */
val PlayBlue = Color(0xFF00A0FF)
val PlayGreen = Color(0xFF00F076)
val PlayYellow = Color(0xFFFFCE00)
val PlayRed = Color(0xFFFF3A44)

private val PhotoBad = Color(0xFFFF8A80)
private val Pens =
    listOf(
        Color(0xFFFFFFFF),
        Color(0xFF000000),
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFFFFEB3B),
        Color(0xFF4CAF50),
        Color(0xFF00BCD4),
        Color(0xFF2196F3),
        Color(0xFF9C27B0),
        Color(0xFFE91E63),
    )

private val OkLight = Color(0xFF246B59)
private val WarnLight = Color(0xFF8A611C)
private val BadLight = Color(0xFF9E3B2E)
private val OkDark = Color(0xFF83D8C4)
private val WarnDark = Color(0xFFD8B774)
private val BadDark = Color(0xFFE4938A)

val OceanLight =
    TtTokens(
        isDark = false,
        bg = Color(0xFFF7FCFF),
        text = Color(0xFF142F43),
        text2 = Color(0xFF203A4E),
        text3 = Color(0xFF223D50),
        text4 = Color(0xFF59758A),
        tabSelected = Color(0xFF2E4E66),
        tabUnselected = Color(0xFF526B7D),
        glass = Color(0xFFF2F9FD),
        glass2 = Color(0xFFDFEDF6),
        glassBorder = Color(0xFFB7D1E2),
        glassBorderStrong = Color(0xFF789AAF),
        hair = Color(0xFFB7D1E2),
        accent = Color(0xFF3A5B73),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF22394B),
        badgeFill = Color(0xFF3A5B73),
        tile = Color(0xFFDCE6EE),
        tileInk = Color(0xFF3A5B73),
        leafAccent = Color(0xFF3A5B73),
        leafAccentSoft = Color(0xFF4A7290).alpha(0.16f),
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
        container = Color(0xFFDCE6EE),
        onContainer = Color(0xFF2D4A5F),
        surface2 = Color(0xFFEAF4FB),
        surface3 = Color(0xFFC7DEED),
        shadow = Color(0xFF102F46),
        inverse = Color(0xFF172F42),
        onInverse = Color(0xFFEFF7FC),
        inverseAccent = Color(0xFFA9C3D6),
        panelTop = Color(0xFFF7FCFF),
        panelBottom = Color(0xFFE3F0F8),
        panelGlow = Color(0xFFB8CCDB).alpha(0.60f),
        panelLine = Color(0xFF4A7290).alpha(0.22f),
        panelOutline = Color(0xFF94B8CF),
        gold = Color(0xFF9A7B1F),
        silver = Color(0xFF6B7488),
        bronze = Color(0xFF8A5A34),
        tixTop = Color(0xFF3F6680),
        tixBottom = Color(0xFF2B4A60),
        tixEdge = Color(0xFF1E3749),
        ticketInk = Color(0xFF17405E),
        ticketShine = Color(0xFFFFFFFF),
        sheet = Color(0xFFFFFFFF),
        sheetLine = Color(0xFFDCE8F0),
        sheetOutline = Color(0xFFB7D1E2),
        ring = Color(0xFF142F43).alpha(0.14f),
        track = Color(0xFFE4EEF4),
        card = Color(0xFFF3F8FB),
        cardLine = Color(0xFFE1ECF3),
        flaskLiquid = Color(0xFF9279D6),
        flaskGlass = Color(0xFF4A3B86),
        photo = Color(0xFF000000),
        onPhoto = Color(0xFFFFFFFF),
        photoScrim = Color(0xFF000000).alpha(0.5f),
        photoBad = PhotoBad,
        botRound = Color(0xFF4A7290).alpha(0.18f),
        mentionTint = Color(0xFF4A7290).alpha(0.12f),
        pens = Pens,
    )

val OceanDark =
    TtTokens(
        isDark = true,
        bg = Color(0xFF0F1011),
        text = Color(0xFFECEEF0),
        text2 = Color(0xFFBCC1C6),
        text3 = Color(0xFF9DA3A9),
        text4 = Color(0xFF757C82),
        tabSelected = Color(0xFFB6CCDC),
        tabUnselected = Color(0xFF989EA4),
        glass = Color(0xFF1C1E20),
        glass2 = Color(0xFF292C2F),
        glassBorder = Color(0xFF383C41),
        glassBorderStrong = Color(0xFF666D74),
        hair = Color(0xFF2B2E32),
        accent = Color(0xFF4A7290),
        onAccent = Color(0xFFFFFFFF),
        accentInk = Color(0xFF93B3CA),
        badgeFill = Color(0xFF4A7290),
        tile = Color(0xFF2A2D31),
        tileInk = Color(0xFFA2BED2),
        leafAccent = Color(0xFF93B3CA),
        leafAccentSoft = Color(0xFF2A2D31),
        onSelected = Color(0xFF93B3CA),
        dock = Color(0xFF1C1E20),
        dockInk = Color(0xFFBCC1C6),
        contour = Color(0xFF1C1E20),
        contourDeep = Color(0xFF292C2F),
        glow = Color(0xFF1D2023),
        ok = OkDark,
        okBg = OkDark.alpha(0.12f),
        warn = WarnDark,
        warnBg = WarnDark.alpha(0.14f),
        warnBorder = WarnDark.alpha(0.26f),
        bad = BadDark,
        badBg = BadDark.alpha(0.15f),
        badBorder = BadDark.alpha(0.3f),
        onWarn = Color(0xFF2A1D04),
        onBad = Color(0xFF3F0D06),
        field = Color(0xFF141618),
        fieldBorder = Color(0xFF666D74),
        scrim = Color(0xFF000000).alpha(0.6f),
        container = Color(0xFF28333C),
        onContainer = Color(0xFFD6EAF6),
        surface2 = Color(0xFF212427),
        surface3 = Color(0xFF2E3236),
        shadow = Color(0xFF000000),
        inverse = Color(0xFFE8EBEE),
        onInverse = Color(0xFF15181A),
        inverseAccent = Color(0xFF3F6681),
        panelTop = Color(0xFF1F2225),
        panelBottom = Color(0xFF18191B),
        panelGlow = Color(0xFF93B3CA).alpha(0.06f),
        panelLine = Color(0xFF93B3CA).alpha(0.1f),
        panelOutline = Color(0xFF40464C),
        gold = Color(0xFFE3A93C),
        silver = Color(0xFFB4BCCB),
        bronze = Color(0xFFC58455),
        tixTop = Color(0xFF4A7290),
        tixBottom = Color(0xFF355A75),
        tixEdge = Color(0xFF14232E),
        // The ticket is the same at night, as the drawable is.
        ticketInk = Color(0xFF17405E),
        ticketShine = Color(0xFFFFFFFF),
        sheet = Color(0xFF1C1E20),
        sheetLine = Color(0xFF2D3034),
        sheetOutline = Color(0xFF3E4348),
        ring = Color(0xFFFFFFFF).alpha(0.16f),
        track = Color(0xFF2D3034),
        card = Color(0xFF1C1E20),
        cardLine = Color(0xFF2D3034),
        flaskLiquid = Color(0xFF8C70D4),
        flaskGlass = Color(0xFFCDBEF0),
        photo = Color(0xFF000000),
        onPhoto = Color(0xFFFFFFFF),
        photoScrim = Color(0xFF000000).alpha(0.5f),
        photoBad = PhotoBad,
        botRound = Color(0xFF93B3CA).alpha(0.18f),
        mentionTint = Color(0xFF93B3CA).alpha(0.10f),
        pens = Pens,
    )

val LocalTokens = staticCompositionLocalOf { OceanDark }

/** The tokens, from anywhere in a composable: `Tt.colors.text3`. */
object Tt {
    val colors: TtTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTokens.current
}
