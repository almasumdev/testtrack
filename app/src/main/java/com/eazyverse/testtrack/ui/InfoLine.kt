package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
 * words in the strongest ink, so a warning reads first. [infoLook] [InfoDotLook.ACCENT] is the Ask
 * page's (i): a 22dp round that turns the accent when open.
 */
@Composable
internal fun InfoLine(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    tone: Tone = Tone.QUIET,
    icon: ImageVector = Lucide.Info,
    info: AnnotatedString? = null,
    infoTitle: String = "More about this",
    infoLook: InfoDotLook = InfoDotLook.FADE,
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
        trailing =
            info?.let { more ->
                {
                    if (infoLook == InfoDotLook.ACCENT) {
                        InfoDot(infoTitle, t.text3, iconInset = 4.dp, look = infoLook, round = 22.dp) { CalloutLine(more) }
                    } else {
                        InfoDot(infoTitle, t.text3, look = infoLook) { CalloutLine(more) }
                    }
                }
            },
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
    infoLook: InfoDotLook = InfoDotLook.FADE,
) = InfoLine(AnnotatedString(text), modifier, tone, icon, info?.let(::AnnotatedString), infoTitle, infoLook)

/**
 * A soft tinted bar with an accent [icon] at the start and up to two lines of [text] after it, such as
 * the offer under a section heading. The icon sits level with the first line; space around it goes in [modifier].
 */
@Composable
internal fun TintedNote(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    icon: ImageVector = Lucide.Info,
) {
    val t = Tt.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(t.glass2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Centred on the first line, whatever the font size.
        val lineHeight = with(LocalDensity.current) { TextStyles.PageLine.lineHeight.toDp() }
        Icon(icon, null, Modifier.padding(top = ((lineHeight - 16.dp) / 2).coerceAtLeast(0.dp)).size(16.dp), tint = t.accentInk)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            style = TextStyles.PageLine,
            color = t.text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

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
