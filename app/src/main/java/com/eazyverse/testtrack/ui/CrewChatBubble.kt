package com.eazyverse.testtrack.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.theme.Tt
import kotlin.math.ceil
import kotlin.math.max

/*
 * A member's message, drawn the way chat apps do: yours on the right in the accent, theirs on the
 * left on a soft surface. A run of messages by one author sits close together, with the name on
 * its first and the face on its last, and the corners toward the author tucked in where the run
 * goes on. The time sits inside the bubble at its bottom right.
 */

private val Round = 18.dp
private val Tucked = 4.dp

/** About three quarters of the screen: the widest a bubble grows. */
@Composable
internal fun bubbleMax(share: Float = 0.75f): Dp = (LocalConfiguration.current.screenWidthDp * share).dp

/** The corners of a bubble: all round, except those toward its author where the run goes on. */
internal fun bubbleShape(
    mine: Boolean,
    first: Boolean,
    last: Boolean,
): RoundedCornerShape {
    val top = if (first) Round else Tucked
    val bottom = if (last) Round else Tucked
    return if (mine) {
        RoundedCornerShape(topStart = Round, topEnd = top, bottomEnd = bottom, bottomStart = Round)
    } else {
        RoundedCornerShape(topStart = top, topEnd = Round, bottomEnd = Round, bottomStart = bottom)
    }
}

/** A bubble's fill, the ink on it, and the softer ink of its time. */
internal data class BubbleInk(
    val fill: Color,
    val ink: Color,
    val meta: Color,
)

@Composable
internal fun bubbleInk(mine: Boolean): BubbleInk {
    val t = Tt.colors
    return if (mine) BubbleInk(t.accent, t.onAccent, t.onAccent.copy(alpha = 0.72f)) else BubbleInk(t.glass2, t.text, t.text4)
}

/** A thin edge where a bubble needs one: a failed send, or a message naming you. */
@Composable
internal fun Modifier.bubbleEdge(
    line: ChatLine,
    you: Boolean,
    shape: RoundedCornerShape,
): Modifier {
    val t = Tt.colors
    return when {
        line.send == SendState.FAILED -> border(1.5.dp, t.badBorder, shape)
        you -> border(1.5.dp, t.accent, shape)
        else -> this
    }
}

/** One member's message, with its face, name and what can be done with it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessage(
    line: ChatLine,
    first: Boolean,
    last: Boolean,
    view: ChatView,
    actions: ChatActions,
) {
    val t = Tt.colors
    val person = line.authorSeatId?.let { view.people[it] }
    val lifted by animateFloatAsState(if (view.lifted == line.id) 1.04f else 1f, label = "chat-lift")
    val picture = line.picture
    val opens = picture != null && !line.deleted && !picture.expired && line.send == SendState.SENT
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 10.dp else 2.dp),
        horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!line.mine) {
            if (last) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .clickable(onClickLabel = "See who this is") { line.authorSeatId?.let(actions.onFace) },
                ) { Avatar(person?.photo.orEmpty(), initialOf(person?.name ?: "?"), size = 28.dp) }
            } else {
                Spacer(Modifier.width(28.dp))
            }
            Spacer(Modifier.width(8.dp))
        }
        Column(horizontalAlignment = if (line.mine) Alignment.End else Alignment.Start) {
            if (!line.mine && first) {
                Text(
                    person?.name ?: "Someone",
                    Modifier.widthIn(max = bubbleMax()).padding(start = 12.dp, bottom = 2.dp),
                    style = chatStyle(12f, 16f),
                    fontWeight = FontWeight.SemiBold,
                    color = t.text3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = lifted
                        scaleY = lifted
                    }.clip(bubbleShape(line.mine, first, last))
                    .combinedClickable(
                        onClickLabel =
                            when {
                                line.send == SendState.FAILED -> "Try again"
                                opens -> "Open the picture"
                                else -> null
                            },
                        onLongClickLabel = "Message options",
                        onClick = {
                            when {
                                line.send == SendState.FAILED -> actions.onRetryLine(line)
                                opens -> actions.onPicture(line)
                            }
                        },
                        onLongClick = { if (!line.deleted && line.send != SendState.SENDING) actions.onLongPress(line) },
                    ),
            ) {
                when {
                    line.deleted -> DeletedStub(line, first, last)
                    picture != null -> PictureBubble(line, picture, first, last, view)
                    else -> TextBubble(line, first, last, view)
                }
            }
            if (line.send == SendState.FAILED) FailedRow(line, actions)
        }
    }
}

/** Words only: an optional quote, then the words with the time tucked after them. */
@Composable
private fun TextBubble(
    line: ChatLine,
    first: Boolean,
    last: Boolean,
    view: ChatView,
) {
    val shape = bubbleShape(line.mine, first, last)
    val ink = bubbleInk(line.mine)
    val you = !line.mine && view.mentionsMe(line)
    Column(
        Modifier
            .widthIn(max = bubbleMax())
            .width(IntrinsicSize.Max)
            .clip(shape)
            .background(ink.fill)
            .bubbleEdge(line, you, shape)
            .padding(start = 12.dp, end = 10.dp, top = if (line.quote != null) 6.dp else 7.dp, bottom = 6.dp),
    ) {
        line.quote?.let { quote ->
            Quote(
                view.quoteOf(quote),
                Modifier.padding(bottom = 5.dp).fillMaxWidth(),
                fill = if (line.mine) ink.ink.copy(alpha = 0.16f) else Tt.colors.bg,
                maxWidth = bubbleMax() - 22.dp,
                ink = if (line.mine) ink.ink else null,
            )
        }
        WordsWithMeta(line, view, ink, Modifier.fillMaxWidth())
    }
}

/** A message's words with its time after the last line when it fits there, else under it. */
@Composable
internal fun WordsWithMeta(
    line: ChatLine,
    view: ChatView,
    ink: BubbleInk,
    modifier: Modifier = Modifier,
) {
    TextWithMeta(
        text = { onLayout -> ChatMessageText(line, view, ink = ink.ink, onTextLayout = onLayout) },
        meta = { MessageMeta(line, ink.meta) },
        modifier = modifier,
    )
}

/**
 * [text] with [meta] at its bottom end: beside the last line when there is room, as chat apps do,
 * or on a line of its own under it.
 */
@Composable
internal fun TextWithMeta(
    text: @Composable ((TextLayoutResult) -> Unit) -> Unit,
    meta: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Written as the words are measured, and read straight after in the same pass.
    val laid = remember { arrayOfNulls<TextLayoutResult>(1) }
    val policy = remember { MetaPolicy(laid) }
    Layout(content = {
        text { laid[0] = it }
        meta()
    }, modifier, policy)
}

private val MetaGap = 8.dp

/** Lays out the words and their time; its intrinsic width is both on one line, for a bubble sized to fit. */
private class MetaPolicy(
    private val laid: Array<TextLayoutResult?>,
) : MeasurePolicy {
    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val gap = MetaGap.roundToPx()
        val words = measurables[0].measure(constraints.copy(minWidth = 0))
        val tag = measurables[1].measure(Constraints())
        val laidOut = laid[0]
        val lastLine = laidOut?.lineCount?.minus(1) ?: -1
        val lastRight =
            if (laidOut != null && lastLine >= 0) ceil(laidOut.getLineRight(lastLine)).toInt() else words.width
        val rtl =
            laidOut != null &&
                lastLine >= 0 &&
                laidOut.getParagraphDirection(laidOut.getLineStart(lastLine)) == ResolvedTextDirection.Rtl
        val beside = !rtl && lastRight + gap + tag.width <= constraints.maxWidth
        val width =
            (if (beside) max(words.width, lastRight + gap + tag.width) else max(words.width, tag.width))
                .coerceIn(constraints.minWidth, constraints.maxWidth)
        val tagY = if (beside) words.height - tag.height + 2.dp.roundToPx() else words.height
        val height = max(words.height, tagY + tag.height)
        return layout(width, height) {
            words.place(0, 0)
            tag.place(width - tag.width, tagY)
        }
    }

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = measurables[0].maxIntrinsicWidth(height) + MetaGap.roundToPx() + measurables[1].maxIntrinsicWidth(height)

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<IntrinsicMeasurable>,
        height: Int,
    ): Int = max(measurables[0].minIntrinsicWidth(height), measurables[1].maxIntrinsicWidth(height))

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables[0].minIntrinsicHeight(width) + measurables[1].minIntrinsicHeight(width)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<IntrinsicMeasurable>,
        width: Int,
    ): Int = measurables[0].maxIntrinsicHeight(width) + measurables[1].maxIntrinsicHeight(width)
}

/** The time, with a clock while it goes and a mark when it did not. */
@Composable
internal fun MessageMeta(
    line: ChatLine,
    ink: Color,
) {
    val t = Tt.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        TimeText(ChatThread.clock(line.at), color = ink)
        when (line.send) {
            SendState.SENDING -> {
                Spacer(Modifier.width(3.dp))
                Icon(Lucide.Clock, "Sending", Modifier.size(11.dp), tint = ink)
            }
            SendState.FAILED -> {
                Spacer(Modifier.width(3.dp))
                Icon(Lucide.CircleAlert, "Not sent", Modifier.size(11.dp), tint = t.bad)
            }
            SendState.SENT -> Unit
        }
    }
}

/** A message taken back: its outline and a word, with the time it was sent. */
@Composable
private fun DeletedStub(
    line: ChatLine,
    first: Boolean,
    last: Boolean,
) {
    val t = Tt.colors
    val shape = bubbleShape(line.mine, first, last)
    Row(
        Modifier
            .dashed(t.glassBorder, Round, shape = shape)
            .padding(start = 12.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Ban, null, Modifier.size(14.dp), tint = t.text4)
        Spacer(Modifier.width(6.dp))
        Text("Message deleted", style = chatStyle(13.5f, 18f), fontStyle = FontStyle.Italic, color = t.text4)
        Spacer(Modifier.width(8.dp))
        TimeText(ChatThread.clock(line.at), Modifier.padding(top = 3.dp))
    }
}
