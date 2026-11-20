package com.eazyverse.testtrack.ui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.ui.theme.Tt
import kotlinx.coroutines.delay
import java.time.LocalDate

/*
 * The pieces of a crew's chat thread: the time lines, the divider before what is new, and the
 * empty chat. Every message, the bot's and the team's too, is a bubble from CrewChatBubble.kt.
 * Times: none inside a message, a time line over every new sender group and wherever the same
 * author talks again after a pause, and a tapped message shows its own time line over it.
 */

/** One row of the thread. */
@Composable
internal fun ChatRowView(
    row: ChatRow,
    view: ChatView,
    actions: ChatActions,
) {
    when (row) {
        is ChatRow.Time -> TimeLine(row.label)
        is ChatRow.New -> NewDivider(row.count)
        is ChatRow.Line -> {
            val open = row.line.at != null && !row.stamped && view.timeShown == row.line.id
            val reveal = remember { BringIntoViewRequester() }
            RevealWhenOpened(open, reveal)
            Column(Modifier.fillMaxWidth().bringIntoViewRequester(reveal)) {
                TappedTime(row, open)
                ChatMessage(row, view, actions)
            }
        }
    }
}

/** Over a new sender group, or where the same author talks again after a pause: the time, small and centred, with no chip. */
@Composable
internal fun TimeLine(
    label: String,
    bottom: Dp = 0.dp,
) {
    Text(
        label,
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = bottom),
        style = chatStyle(11.5f, 15f),
        fontWeight = FontWeight.Medium,
        color = Tt.colors.text3,
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

private const val TAP_MS = 150

/** A tapped message's own time line, over it, eased in and out. One shows at a time. */
@Composable
private fun TappedTime(
    row: ChatRow.Line,
    open: Boolean,
) {
    val at = row.line.at
    val hour24 = DateFormat.is24HourFormat(LocalContext.current)
    AnimatedVisibility(
        visible = open,
        enter = expandVertically(tween(TAP_MS)) + fadeIn(tween(TAP_MS)),
        exit = shrinkVertically(tween(TAP_MS)) + fadeOut(tween(TAP_MS)),
    ) {
        val label = remember(at, hour24) { at?.let { ChatThread.timeLabel(it, LocalDate.now(), hour24 = hour24) }.orEmpty() }
        TimeLine(label, bottom = 6.dp)
    }
}

/**
 * The thread grows upward, so a time line opening over a message near the top edge would land out
 * of sight. Once it has eased in, the message and its time scroll into view. One open already when
 * the row is first drawn stays where it is.
 */
@Composable
private fun RevealWhenOpened(
    open: Boolean,
    reveal: BringIntoViewRequester,
) {
    val was = remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open && !was.value) {
            delay(TAP_MS.toLong())
            reveal.bringIntoView()
        }
        was.value = open
    }
}

@Composable
private fun NewDivider(count: Int) {
    val t = Tt.colors
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(1.dp).background(t.glassBorder))
        Text(
            "$count new",
            Modifier.padding(horizontal = 8.dp),
            style = chatStyle(12f, 16f),
            fontWeight = FontWeight.Bold,
            color = t.accentInk,
        )
        Box(Modifier.weight(1f).height(1.dp).background(t.glassBorder))
    }
}

/**
 * The message a reply quotes: who wrote it and its first line. [ink] draws it on a coloured bubble,
 * where the theme's own text colours would not show. Inside a message, [tap] makes it a way to the
 * message it quotes.
 */
@Composable
internal fun Quote(
    quote: QuoteView,
    modifier: Modifier = Modifier,
    fill: Color = Tt.colors.bg,
    maxWidth: Dp = 240.dp,
    ink: Color? = null,
    tap: QuoteTap? = null,
    flat: Boolean = false,
) {
    val t = Tt.colors
    val bar = ink ?: t.accentInk
    // Flat: drawn straight on whatever holds it, with no box of its own, only the bar and the text.
    val box =
        if (flat) {
            Modifier
                .drawBehind { drawRoundRect(bar, size = Size(3.dp.toPx(), size.height), cornerRadius = CornerRadius(1.5.dp.toPx())) }
                .padding(start = 11.dp)
        } else {
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .then(tap?.let { Modifier.quoteTap(it) } ?: Modifier)
                .background(fill)
                .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                .padding(start = 11.dp, end = 8.dp, top = 4.dp, bottom = 5.dp)
        }
    Row(modifier.widthIn(max = maxWidth).then(box)) {
        Column {
            Text(quote.who, style = chatStyle(12f, 16f), fontWeight = FontWeight.Bold, color = bar, maxLines = 1)
            Text(
                quote.text,
                style = chatStyle(13f, 17f),
                fontStyle = if (quote.deleted) FontStyle.Italic else FontStyle.Normal,
                color = ink?.copy(alpha = 0.82f) ?: if (quote.deleted) t.text4 else t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Under a message that did not go: the red mark, the way to try again, and why. */
@Composable
internal fun FailedRow(
    line: ChatLine,
    actions: ChatActions,
) {
    val t = Tt.colors
    Column(Modifier.padding(top = 3.dp), horizontalAlignment = Alignment.End) {
        Row(
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClickLabel = "Try again") { actions.onRetryLine(line) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Lucide.CircleAlert, null, Modifier.size(14.dp), tint = t.bad)
            Spacer(Modifier.width(5.dp))
            Text("Not sent. Tap to retry", style = chatStyle(12f, 16f), fontWeight = FontWeight.Medium, color = t.bad)
        }
        if (line.error.isNotBlank()) Text(line.error, Modifier.padding(end = 4.dp), style = chatStyle(12f, 16f), color = t.text3)
    }
}

/** Under your own message: "Sending" with a small clock while it goes, "Sent" on your newest once it has. */
@Composable
internal fun SendNote(sending: Boolean) {
    val t = Tt.colors
    Row(Modifier.padding(top = 3.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (sending) {
            Icon(Lucide.Clock, null, Modifier.size(12.dp), tint = t.text3)
            Spacer(Modifier.width(4.dp))
        }
        Text(if (sending) "Sending" else "Sent", style = chatStyle(11.5f, 15f), fontWeight = FontWeight.Medium, color = t.text3)
    }
}

/** Nobody has written yet: the app's robot holding up a speech bubble, then a title and one line, centred where it is put. */
@Composable
internal fun ChatEmptyNote(
    canPost: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    RobotState(
        Prop.Chat,
        "No messages yet",
        if (canPost) "Say hi to your crew." else "Nobody wrote in this chat.",
        modifier,
        fill = false,
        robot = 140.dp,
        pad = PaddingValues(horizontal = 32.dp, vertical = 24.dp),
        titleStyle = chatStyle(17f, 22f).copy(fontWeight = FontWeight.SemiBold),
        titleColor = t.text,
        titleGap = 4.dp,
        lineColor = t.text3,
        lineMax = Dp.Unspecified,
    )
}
