package com.eazyverse.testtrack.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import com.composables.icons.lucide.AtSign
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.ImagePlus
import com.composables.icons.lucide.Lock
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Send
import com.composables.icons.lucide.X
import com.eazyverse.testtrack.domain.ChatAllowance
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.DraftProblem
import com.eazyverse.testtrack.ui.theme.Tt
import com.eazyverse.testtrack.ui.theme.tabular
import kotlinx.coroutines.delay

/**
 * Writing to the crew: one pill with the picture button, the field growing to five lines, the
 * messages left once few are, and an @ button; Send pops in beside it while there is text. A reply
 * shows what it answers on a strip over the pill; a link stops Send and says why under it. With no
 * messages left the pill is shut and says so.
 */
@Composable
internal fun ChatComposer(
    view: ChatView,
    actions: ChatActions,
    field: TextFieldValue,
) {
    val t = Tt.colors
    val canSend = ChatAllowance.canSend(view.draft, view.problem, view.messagesLeft, view.canPost)
    val showSend = field.text.isNotBlank() && view.messagesLeft > 0
    ComposerFrame {
        view.replyTo?.let { reply ->
            val quote = view.quoteOf(ChatQuote(reply.id, reply.authorSeatId, reply.text, reply.picture != null))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(t.glass2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Quote(
                    quote.copy(who = "Replying to ${quote.who}"),
                    Modifier.weight(1f).padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                    fill = t.bg,
                    maxWidth = Dp.Unspecified,
                )
                FieldButton(Lucide.X, "Stop replying", 44.dp, 36.dp, 17.dp, t.text2, onClick = actions.onClearReply)
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            ChatField(field, view, actions, Modifier.weight(1f))
            AnimatedVisibility(
                visible = showSend,
                enter = fadeIn(tween(150)) + scaleIn(tween(150), initialScale = 0.6f),
                exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.6f),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    RoundButton(
                        Lucide.Send,
                        "Send",
                        48.dp,
                        20.dp,
                        // The accent own bubbles wear, the same as Send in the picture editor.
                        if (canSend) t.accent else t.glass2,
                        if (canSend) t.onAccent else t.text4,
                        enabled = canSend,
                        onClick = actions.onSend,
                    )
                }
            }
        }
        if (view.problem != DraftProblem.NONE) ComposerLine(Lucide.CircleAlert, problemLine(view), bad = true)
    }
}

/** The picture button at the start of the pill, with the pictures left on its corner. */
@Composable
private fun PictureButton(
    view: ChatView,
    actions: ChatActions,
    ring: Color,
) {
    val t = Tt.colors
    val canAttach = ChatAllowance.canAttach(view.messagesLeft, view.picturesLeft, view.canPost)
    var tip by remember { mutableStateOf(false) }
    Box {
        FieldButton(
            Lucide.ImagePlus,
            if (canAttach) "Send a picture" else "No pictures left today",
            48.dp,
            48.dp,
            21.dp,
            if (canAttach) t.tileInk else t.text4,
            onClick = { if (canAttach) actions.onPickPicture() else tip = true },
        )
        ChatAllowance.pictureBadge(view.messagesLeft, view.picturesLeft, view.limits, view.canPost)?.let {
            PictureBadge(it, ring, Modifier.align(Alignment.TopEnd))
        }
        if (tip) PictureTip(pictureTip(view)) { tip = false }
    }
}

/** Why the picture button is grey, said briefly when it is tapped. */
private fun pictureTip(view: ChatView): String =
    when {
        view.messagesLeft <= 0 -> "No messages left today. More after midnight."
        view.limits.picturesPerDay <= 0 -> "Pictures are off in this chat."
        else -> "No pictures left today. You get ${view.limits.picturesPerDay} more after midnight."
    }

/** How many pictures are left, on the corner of the picture button. */
@Composable
private fun PictureBadge(
    count: String,
    ring: Color,
    modifier: Modifier,
) {
    val t = Tt.colors
    Box(
        modifier
            .offset(x = (-3).dp, y = 6.dp)
            .heightIn(min = 18.dp)
            .widthIn(min = 18.dp)
            .clip(CircleShape)
            .background(t.badgeFill)
            .border(1.5.dp, ring, CircleShape)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            count,
            style = chatStyle(10.5f, 13f).tabular(),
            fontWeight = FontWeight.Bold,
            color = t.onAccent,
        )
    }
}

/** A short note over the picture button, gone after a few seconds or a tap. */
@Composable
private fun PictureTip(
    text: String,
    onGone: () -> Unit,
) {
    val t = Tt.colors
    LaunchedEffect(text) {
        delay(3_000)
        onGone()
    }
    val gap = with(LocalDensity.current) { 8.dp.roundToPx() }
    Popup(popupPositionProvider = remember(gap) { AboveAnchor(gap) }, onDismissRequest = onGone) {
        Text(
            text,
            Modifier
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.inverse)
                .clickable(onClick = onGone)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            style = chatStyle(13f, 17f),
            color = t.onInverse,
        )
    }
}

private fun problemLine(view: ChatView): String =
    when (view.problem) {
        DraftProblem.LINK -> "Links can't be sent here. Take it out to send. To point at an app, type @ and pick it."
        DraftProblem.TOO_LONG -> "That's over ${view.limits.maxLength} characters. Make it shorter to send."
        DraftProblem.TOO_MANY_MENTIONS -> "You can mention up to ${view.limits.maxMentions} people or apps in one message."
        DraftProblem.NONE -> ""
    }

/** Puts a popup just over what it belongs to, kept inside the window. */
private class AboveAnchor(
    private val gap: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset =
        IntOffset(
            anchorBounds.left.coerceAtMost(windowSize.width - popupContentSize.width).coerceAtLeast(0),
            (anchorBounds.top - popupContentSize.height - gap).coerceAtLeast(0),
        )
}

/**
 * The field: one pill holding the picture button, the text growing to five lines, the messages
 * left once few are, and the @ button. Its edge is red while what is written cannot go. With none
 * left it is shut and says so.
 */
@Composable
private fun ChatField(
    value: TextFieldValue,
    view: ChatView,
    actions: ChatActions,
    modifier: Modifier,
) {
    val t = Tt.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val bad = view.problem != DraftProblem.NONE
    val shape = RoundedCornerShape(24.dp)
    val mentions = remember(view.draft, t) { MentionInk(view.draft, t.accentInk) }
    val open = view.messagesLeft > 0
    val count = ChatAllowance.fieldCount(view.messagesLeft)
    val canMention = open && view.people.values.any { it.inCrew && !it.mine }
    val fill = if (open) t.field else t.glass2
    BasicTextField(
        value = value,
        onValueChange = actions.onField,
        modifier = modifier,
        enabled = open,
        textStyle = chatStyle(15f, 20f).copy(color = t.text),
        cursorBrush = SolidColor(t.accentInk),
        maxLines = 5,
        visualTransformation = mentions,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        interactionSource = source,
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(fill)
                    .border(
                        when {
                            bad || focused -> 1.6.dp
                            open -> 1.2.dp
                            else -> 1.dp
                        },
                        when {
                            bad -> t.bad
                            focused -> t.text
                            !open -> t.glassBorder
                            else -> t.glassBorderStrong
                        },
                        shape,
                    ),
                verticalAlignment = Alignment.Bottom,
            ) {
                PictureButton(view, actions, fill)
                Box(Modifier.weight(1f).padding(vertical = 14.dp)) {
                    if (value.text.isEmpty()) {
                        Text(
                            if (open) "Message ${view.crewName}" else "No messages left today",
                            style = chatStyle(15f, 20f),
                            color = t.text4,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
                if (count != null) {
                    Text(
                        "$count left",
                        Modifier
                            .padding(start = 6.dp, top = 14.dp, bottom = 14.dp)
                            .semantics {
                                contentDescription = if (count == 1) "1 message left today" else "$count messages left today"
                            },
                        style = chatStyle(12f, 20f).tabular(),
                        color = t.text4,
                    )
                }
                if (canMention) {
                    FieldButton(Lucide.AtSign, "Mention someone", 44.dp, 48.dp, 20.dp, t.text3, onClick = actions.onAt)
                } else {
                    Spacer(Modifier.width(16.dp))
                }
            }
        },
    )
}

/** Picked mentions in the field drawn semibold in the accent, the same as they read once sent. */
private class MentionInk(
    private val draft: ChatDraft,
    private val ink: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (draft.spans.isEmpty() || draft.text != text.text) return TransformedText(text, OffsetMapping.Identity)
        val styled =
            buildAnnotatedString {
                append(text)
                draft.spans.forEach { addStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold), it.start, it.end) }
            }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is MentionInk && other.draft == draft && other.ink == ink

    override fun hashCode() = draft.hashCode() * 31 + ink.hashCode()
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

/** A bare icon button inside the pill, with a round press mark. */
@Composable
private fun FieldButton(
    icon: ImageVector,
    label: String,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    ink: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width, height)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, Modifier.size(iconSize), tint = ink) }
}

/** The ground under the composer and its stand-ins. */
@Composable
private fun ComposerFrame(content: @Composable () -> Unit) {
    val t = Tt.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.bg)
            .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp),
    ) { content() }
}

/** The composer while the chat loads: a pill that shimmers. */
@Composable
internal fun ChatComposerLoading() {
    ComposerFrame { Skeleton(height = 48.dp, corner = 24.dp) }
}

/** In place of the composer when the chat can be read but not written in. */
@Composable
internal fun ChatClosedBar(text: String) {
    val t = Tt.colors
    ComposerFrame {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(t.glass2)
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.Lock, null, Modifier.size(18.dp), tint = t.text2)
            Spacer(Modifier.width(10.dp))
            Text(text, style = chatStyle(13.5f, 18f), color = t.text2)
        }
    }
}
