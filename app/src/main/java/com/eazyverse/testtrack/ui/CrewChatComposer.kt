package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.ImageOff
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessageSquare
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.domain.Left
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular

/**
 * Writing to the crew: what is left today over the field, a picture button, the field growing to
 * five lines, and Send. A reply shows what it answers over the field; a link stops Send and says
 * why under it.
 */
@Composable
internal fun ChatComposer(
    view: ChatView,
    actions: ChatActions,
    field: TextFieldValue,
) {
    val t = Tt.colors
    val canSend = ChatAllowance.canSend(view.draft, view.problem, view.messagesLeft, view.canPost)
    val canPicture = ChatAllowance.canPickPicture(view.problem, view.messagesLeft, view.picturesLeft, view.canPost)
    ComposerFrame {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
        ) {
            LeftCount(Lucide.MessageSquare, view.messagesLeft, " left today", ChatAllowance.level(view.messagesLeft))
            LeftCount(
                Lucide.Image,
                view.picturesLeft,
                " " + ChatAllowance.pictureWord(view.picturesLeft),
                ChatAllowance.pictureLevel(view.picturesLeft),
            )
        }
        view.replyTo?.let { reply ->
            Row(Modifier.padding(start = 2.dp, end = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                val quote = view.quoteOf(ChatQuote(reply.id, reply.authorSeatId, reply.text, reply.picture != null))
                Quote(quote.copy(who = "Replying to ${quote.who}"), Modifier.weight(1f), fill = t.glass2, maxWidth = Dp.Unspecified)
                Spacer(Modifier.width(8.dp))
                RoundButton(Lucide.X, "Stop replying", 32.dp, 15.dp, t.glass2, t.text2, onClick = actions.onClearReply)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            RoundButton(
                Lucide.ImagePlus,
                if (canPicture) "Send a picture" else "No pictures left today",
                48.dp,
                21.dp,
                if (canPicture) t.tile else t.glass2,
                if (canPicture) t.tileInk else t.text4,
                enabled = canPicture,
                onClick = actions.onPickPicture,
            )
            Spacer(Modifier.width(8.dp))
            ChatField(field, view, actions.onField, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            RoundButton(
                Lucide.Send,
                "Send",
                48.dp,
                20.dp,
                if (canSend) t.primary else t.glass2,
                if (canSend) t.onPrimary else t.text4,
                enabled = canSend,
                onClick = actions.onSend,
            )
        }
        when {
            view.problem != DraftProblem.NONE -> ComposerLine(Lucide.CircleAlert, problemLine(view), bad = true)
            view.picturesLeft <= 0 ->
                ComposerLine(
                    Lucide.ImageOff,
                    "No pictures left today. You get ${view.limits.picturesPerDay} more after midnight.",
                )
        }
    }
}

private fun problemLine(view: ChatView): String =
    when (view.problem) {
        DraftProblem.LINK -> "Links can't be sent here. Take it out to send. To point at an app, type @ and pick it."
        DraftProblem.TOO_LONG -> "That's over ${view.limits.maxLength} characters. Make it shorter to send."
        DraftProblem.TOO_MANY_MENTIONS -> "You can mention up to ${view.limits.maxMentions} people or apps in one message."
        DraftProblem.NONE -> ""
    }

/** The field: a pill that grows to five lines, its edge red while what is written cannot go. */
@Composable
private fun ChatField(
    value: TextFieldValue,
    view: ChatView,
    onValue: (TextFieldValue) -> Unit,
    modifier: Modifier,
) {
    val t = Tt.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bad = view.problem != DraftProblem.NONE
    val shape = RoundedCornerShape(24.dp)
    val mentions = remember(view.draft, t) { MentionInk(view.draft, t.accentInk) }
    BasicTextField(
        value = value,
        onValueChange = onValue,
        modifier = modifier,
        textStyle = chatStyle(15f, 20f).copy(color = t.text),
        cursorBrush = SolidColor(t.accentInk),
        maxLines = 5,
        visualTransformation = mentions,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        interactionSource = source,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(t.bg)
                    .border(
                        if (bad || focused) 1.6.dp else 1.2.dp,
                        when {
                            bad -> t.bad
                            focused -> t.text
                            else -> t.glassBorderStrong
                        },
                        shape,
                    ).padding(horizontal = 16.dp, vertical = 13.dp),
            ) {
                if (value.text.isEmpty()) Text("Message ${view.crewName}", style = chatStyle(15f, 20f), color = t.text4, maxLines = 1)
                inner()
            }
        },
    )
}

/** Picked mentions in the field drawn bold in the accent, the same as they read once sent. */
private class MentionInk(
    private val draft: ChatDraft,
    private val ink: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (draft.spans.isEmpty() || draft.text != text.text) return TransformedText(text, OffsetMapping.Identity)
        val styled =
            buildAnnotatedString {
                append(text)
                draft.spans.forEach { addStyle(SpanStyle(color = ink, fontWeight = FontWeight.Bold), it.start, it.end) }
            }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is MentionInk && other.draft == draft && other.ink == ink

    override fun hashCode() = draft.hashCode() * 31 + ink.hashCode()
}

/** "7 left today", in the warning colour at two or fewer and the bad one at none. */
@Composable
private fun LeftCount(
    icon: ImageVector,
    count: Int,
    words: String,
    level: Left,
) {
    val t = Tt.colors
    val ink =
        when (level) {
            Left.NONE -> t.bad
            Left.LOW -> t.warn
            Left.PLENTY -> t.text3
        }
    val weight = if (level == Left.PLENTY) FontWeight.Normal else FontWeight.SemiBold
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = ink)
        Spacer(Modifier.width(4.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = chatStyle(12f, 16f).tabular().fontFamily, fontWeight = FontWeight.Bold)) {
                    append(count.coerceAtLeast(0).toString())
                }
                append(words)
            },
            style = chatStyle(12f, 16f),
            fontWeight = weight,
            color = ink,
        )
    }
}

@Composable
private fun ComposerLine(
    icon: ImageVector,
    text: String,
    bad: Boolean = false,
) {
    val ink = if (bad) Tt.colors.bad else Tt.colors.text3
    Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 7.dp)) {
        Icon(icon, null, Modifier.padding(top = 1.dp).size(15.dp), tint = ink)
        Spacer(Modifier.width(6.dp))
        Text(text, style = chatStyle(13f, 17f), color = ink)
    }
}

@Composable
private fun RoundButton(
    icon: ImageVector,
    label: String,
    size: Dp,
    iconSize: Dp,
    fill: Color,
    ink: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(iconSize), tint = ink) }
}

/** The ground under the composer and its stand-ins, with a hairline over it. */
@Composable
private fun ComposerFrame(content: @Composable () -> Unit) {
    val t = Tt.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.bg)
            .drawBehind { drawLine(t.cardLine, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp),
    ) { content() }
}

/** The composer while the chat loads: its counts shimmer, its buttons wait. */
@Composable
internal fun ChatComposerLoading() {
    val t = Tt.colors
    ComposerFrame {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.End),
        ) {
            Skeleton(width = 80.dp, height = 12.dp)
            Skeleton(width = 64.dp, height = 12.dp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            RoundButton(Lucide.ImagePlus, "Send a picture", 48.dp, 21.dp, t.glass2, t.text4, enabled = false) {}
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.2.dp, t.glassBorderStrong, RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.width(8.dp))
            RoundButton(Lucide.Send, "Send", 48.dp, 20.dp, t.glass2, t.text4, enabled = false) {}
        }
    }
}

/** In place of the composer when the chat can be read but not written in. */
@Composable
internal fun ChatClosedBar(text: String) {
    val t = Tt.colors
    ComposerFrame {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(t.glass2)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Lock, null, Modifier.size(18.dp), tint = t.text2)
            Spacer(Modifier.width(10.dp))
            Text(text, style = chatStyle(13.5f, 18f), color = t.text2)
        }
    }
}

/** The day's messages are used up: no field, only when more come. */
@Composable
internal fun ChatLimitCard(perDay: Int) {
    val t = Tt.colors
    ComposerFrame {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(t.warnBg)
                .border(1.dp, t.warnBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Clock, null, Modifier.size(22.dp), tint = t.warn)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("That's your $perDay for today", style = chatStyle(14.5f, 19f), fontWeight = FontWeight.SemiBold, color = t.text)
                Text("You can write again after midnight, your time.", style = chatStyle(12.5f, 17f), color = t.text2)
            }
        }
    }
}
