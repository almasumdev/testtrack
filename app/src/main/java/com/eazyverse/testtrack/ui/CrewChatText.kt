package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPiece
import com.eazyverse.testtrack.domain.ChatText
import com.eazyverse.testtrack.ui.theme.Tt

/** An app named in a message is a chip with its icon and short name; a member is "@Name" in bold. */
private const val CHIP_TEXT_SP = 14f
private val ChipIcon = 17.dp

/** A short name for an app chip: what comes before the colon in a store title. */
internal fun shortAppName(name: String) = name.substringBefore(':').trim().ifBlank { name }

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
) {
    val t = Tt.colors
    val style = chatStyle(15f, 20f)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val chipStyle = style.copy(fontSize = CHIP_TEXT_SP.sp, fontWeight = FontWeight.SemiBold)
    val pieces = remember(line.text) { ChatText.pieces(line.text) }
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
                            val wide =
                                with(density) {
                                    val words =
                                        measurer
                                            .measure(name, chipStyle)
                                            .size.width
                                            .toFloat()
                                    // Icon, gaps and padding round the name, then the whole in the text's own em.
                                    (words + (ChipIcon + 5.dp + 3.dp + 8.dp + 4.dp).toPx()) / style.fontSize.toPx()
                                }
                            appendInlineContent(key, "@$name")
                            inline[key] =
                                InlineTextContent(Placeholder(wide.em, 1.4.em, PlaceholderVerticalAlign.TextCenter)) {
                                    AppChip(view.people[piece.seatId], name, chipStyle)
                                }
                        } else {
                            val you = piece.seatId == view.mySeat
                            withStyle(
                                if (you) {
                                    SpanStyle(fontWeight = FontWeight.Bold, color = t.tileInk, background = t.tile)
                                } else {
                                    SpanStyle(fontWeight = FontWeight.Bold, color = if (line.mine) ink else t.accentInk)
                                },
                            ) { append("@$label") }
                        }
                    }
                }
            }
        }
    Text(text, modifier, style = style, color = ink, inlineContent = inline)
}

@Composable
private fun AppChip(
    person: com.eazyverse.testtrack.domain.ChatPerson?,
    name: String,
    style: TextStyle,
) {
    val t = Tt.colors
    val shape = RoundedCornerShape(percent = 50)
    Row(
        Modifier
            .fillMaxSize()
            .padding(vertical = 1.dp)
            .clip(shape)
            .background(t.bg)
            .border(1.dp, t.cardLine, shape)
            .padding(start = 3.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppMark(person?.packageName.orEmpty(), person?.appName ?: name, person?.appIcon.orEmpty(), size = ChipIcon)
        Spacer(Modifier.width(5.dp))
        Text(name, style = style, color = t.text, maxLines = 1, overflow = TextOverflow.Clip)
    }
}
