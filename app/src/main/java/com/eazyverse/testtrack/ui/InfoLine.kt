package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Info
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Plain lines on a page: one with its icon as the first glyph, and a small grey note.
 */

/**
 * A page line with its [icon] as the first glyph, so a wrapped line runs back to the left edge
 * rather than hanging under the icon. With [info] it ends on an (i) that opens a callout holding
 * the rest, titled [infoTitle] for TalkBack.
 *
 * A quiet [tone] is a grey icon and ordinary words; any other tone colours the icon and sets the
 * words in the strongest ink, so a warning reads first.
 */
@Composable
internal fun InfoLine(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.QUIET,
    icon: ImageVector = Lucide.Info,
    info: AnnotatedString? = null,
    infoTitle: String = "More about this",
) {
    val t = Tt.colors
    val quiet = tone == Tone.QUIET
    IconLine(
        icon,
        if (quiet) t.text3 else tone.ink(),
        text,
        TextStyles.PageLine,
        if (quiet) t.text2 else t.text,
        modifier,
        trailing = info?.let { more -> { InfoDot(infoTitle, t.text3) { CalloutLine(more) } } },
        trailingWidth = 26.dp,
    )
}

/** [InfoLine] with plain words. */
@Composable
internal fun InfoLine(
    text: String,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.QUIET,
    icon: ImageVector = Lucide.Info,
    info: String? = null,
    infoTitle: String = "More about this",
) = InfoLine(AnnotatedString(text), modifier, tone, icon, info?.let(::AnnotatedString), infoTitle)

/**
 * A small grey line, such as what happens to your tix under a card. Centred across the width
 * unless [center] is false. Space around it goes in [modifier].
 */
@Composable
internal fun QuietNote(
    text: String,
    modifier: Modifier = Modifier,
    center: Boolean = true,
) {
    Text(
        text,
        modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = Tt.colors.text4,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
    )
}
