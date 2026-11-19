package com.eazyverse.testtrack.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPiece
import com.eazyverse.testtrack.domain.ChatText
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * Mentions read as words, the way Messenger and WhatsApp draw them: a member is "@Name" in
 * semibold accent ink, an app is its small icon then its short name in the same style. Neither
 * sits in a box. On your own accent bubble the name is bold and underlined in the bubble's ink.
 */

/** The app icon's height, in the text's own em: small enough that its line keeps its height. */
private const val ICON_EM = 1.1f
private val IconGap = 2.dp
private val IconCorner = 4.dp

/** A short name for an app mention: what comes before the colon in a store title. */
internal fun shortAppName(name: String) = name.substringBefore(':').trim().ifBlank { name }

/** How a mention reads on a bubble: semibold accent on theirs, bold underlined ink on yours. */
@Composable
internal fun mentionStyle(
    mine: Boolean,
    ink: Color,
): SpanStyle =
    if (mine) {
        SpanStyle(color = ink, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
    } else {
        SpanStyle(color = Tt.colors.accentInk, fontWeight = FontWeight.SemiBold)
    }

/**
 * A message's words, with its mentions drawn in place. Nothing in it is drawn as a link: links
 * cannot be sent, and a mention is a name, not a way somewhere.
 */
@Composable
internal fun ChatMessageText(
    line: ChatLine,
    view: ChatView,
    modifier: Modifier = Modifier,
    ink: Color = Tt.colors.text,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val style = chatStyle(15f, 20f)
    val density = LocalDensity.current
    val mention = mentionStyle(line.mine, ink)
    val pieces = remember(line.text) { ChatText.pieces(line.text) }
    // The icon plus its gap, as a share of the text's em, so the slot scales with the font.
    val slot = with(density) { ICON_EM + IconGap.toPx() / style.fontSize.toPx() }
    val inline = HashMap<String, InlineTextContent>()
    val text =
        buildAnnotatedString {
            pieces.forEachIndexed { index, piece ->
                when (piece) {
                    is ChatPiece.Words -> append(piece.text)
                    is ChatPiece.Mention -> {
                        val label = view.mentionLabel(line, piece.app, piece.seatId)
                        if (piece.app) {
                            val key = "app$index"
                            val name = shortAppName(label)
                            appendInlineContent(key, "@")
                            inline[key] =
                                InlineTextContent(Placeholder(slot.em, ICON_EM.em, PlaceholderVerticalAlign.TextCenter)) {
                                    MentionIcon(view.people[piece.seatId], name)
                                }
                            withStyle(mention) { append(name) }
                        } else {
                            withStyle(mention) { append("@$label") }
                        }
                    }
                }
            }
        }
    Text(text, modifier, style = style, color = ink, inlineContent = inline, onTextLayout = onTextLayout)
}

/** An app's icon inside a line of text: the placeholder's height, softly rounded, then the gap. */
@Composable
private fun MentionIcon(
    person: ChatPerson?,
    name: String,
) {
    Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(Modifier.fillMaxHeight().aspectRatio(1f).clip(RoundedCornerShape(IconCorner))) {
            AppMark(person?.packageName.orEmpty(), person?.appName ?: name, person?.appIcon.orEmpty(), size = maxHeight)
        }
        Spacer(Modifier.width(IconGap))
    }
}
