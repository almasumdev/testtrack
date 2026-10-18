package com.eazyverse.testtrack.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.eazyverse.testtrack.R

/**
 * Barlow for words, IBM Plex Mono for figures. Bundled rather than fetched,
 * so the first launch on a slow connection looks the same as every other.
 *
 * Nothing is heavier than bold. The hierarchy comes from size and from weight steps of one, not
 * from a black weight shouting over everything else.
 */
val Barlow =
    FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

val PlexMono =
    FontFamily(
        Font(R.font.plexmono_medium, FontWeight.Normal),
        Font(R.font.plexmono_medium, FontWeight.Medium),
        Font(R.font.plexmono_semibold, FontWeight.SemiBold),
        Font(R.font.plexmono_bold, FontWeight.Bold),
    )

private val Base = Typography()

private fun TextStyle.barlow(weight: FontWeight? = null) = copy(fontFamily = Barlow, fontWeight = weight ?: fontWeight)

/** Material's 2021 sizes in Barlow, with headings one weight up. */
val TtTypography =
    Typography(
        displayLarge = Base.displayLarge.barlow(FontWeight.SemiBold),
        displayMedium = Base.displayMedium.barlow(FontWeight.SemiBold),
        displaySmall = Base.displaySmall.barlow(FontWeight.SemiBold),
        headlineLarge = Base.headlineLarge.barlow(FontWeight.SemiBold),
        headlineMedium = Base.headlineMedium.barlow(FontWeight.SemiBold),
        headlineSmall = Base.headlineSmall.barlow(FontWeight.SemiBold),
        titleLarge = Base.titleLarge.barlow(FontWeight.Bold),
        titleMedium = Base.titleMedium.barlow(FontWeight.SemiBold),
        titleSmall = Base.titleSmall.barlow(FontWeight.SemiBold),
        bodyLarge = Base.bodyLarge.barlow(),
        bodyMedium = Base.bodyMedium.barlow(),
        bodySmall = Base.bodySmall.barlow(),
        labelLarge = Base.labelLarge.barlow(FontWeight.SemiBold),
        labelMedium = Base.labelMedium.barlow(FontWeight.Medium),
        labelSmall = Base.labelSmall.barlow(FontWeight.Medium),
    )

/**
 * A figure: places, points, counts, days.
 *
 * Mono with tabular figures, so a column of numbers stacks into a straight edge and a count that
 * ticks from 9 to 10 does not shove the text beside it. Keeps the size it was given.
 */
fun TextStyle.tabular(): TextStyle = copy(fontFamily = PlexMono, fontFeatureSettings = "tnum")
