package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.eazyverse.testtrack.ui.theme.Tt

/** Open sections on the page ground, with one shared icon and type treatment. */
@Composable
internal fun ReviewSectionTitle(
    icon: ImageVector,
    title: String,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconSquare(icon, Tone.ACCENT, size = 28)
        Spacer(Modifier.width(10.dp))
        Text(title, style = ReviewHeadingStyle, color = Tt.colors.text)
    }
}

@Composable
internal fun ReviewSection(
    icon: ImageVector,
    title: String,
    body: String,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ReviewLineGap)) {
        ReviewSectionTitle(icon, title)
        ReviewBullet(body = body)
    }
}

/** Reuses the crew guide's bullet so wrapped lines and text share the same app treatment. */
@Composable
internal fun ReviewBullet(
    lead: String = "",
    body: String,
    highlights: List<String> = emptyList(),
) {
    val strong = SpanStyle(fontWeight = FontWeight.SemiBold, color = Tt.colors.text)
    Box(Modifier.padding(start = ReviewBulletInset)) {
        RuleLine(
            buildAnnotatedString {
                if (lead.isNotBlank()) {
                    withStyle(strong) { append(lead) }
                    append(" ")
                }
                val bodyStart = length
                append(body)
                highlights.filter { it.isNotBlank() }.distinct().forEach { phrase ->
                    var at = body.indexOf(phrase)
                    while (at >= 0) {
                        addStyle(strong, bodyStart + at, bodyStart + at + phrase.length)
                        at = body.indexOf(phrase, at + phrase.length)
                    }
                }
            },
        )
    }
}

// A 4dp bullet centred under the section heading's 28dp icon.
internal val ReviewBulletInset = 12.dp
internal val ReviewSectionGap = 24.dp
internal val ReviewLineGap = 8.dp

private val ReviewHeadingStyle = tileStyle(16f, 19f, FontWeight.Bold)
