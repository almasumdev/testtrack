package com.eazyverse.testtrack.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.MessagesSquare
import com.composables.icons.lucide.RotateCcw
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces of a crew's chat thread: the bot's lines, the team's posts, the dividers between days
 * and before what is new, and the empty chat. Members' bubbles are in CrewChatBubble.kt.
 */

/** Bot lines that are good news, drawn in the ok colour. */
private val GoodNews = setOf("started", "night", "halfway", "last_night", "ended")

/** One row of the thread. */
@Composable
internal fun ChatRowView(
    row: ChatRow,
    view: ChatView,
    actions: ChatActions,
) {
    when (row) {
        is ChatRow.Day -> DayDivider(row.label)
        is ChatRow.New -> NewDivider(row.count)
        is ChatRow.Line ->
            when (row.line.kind) {
                ChatKind.BOT -> BotLine(row.line, view)
                ChatKind.TEAM -> TeamPost(row.line, view)
                ChatKind.USER -> ChatMessage(row.line, row.first, row.last, view, actions)
            }
    }
}

/** A day's chip, centred between the days. */
@Composable
private fun DayDivider(label: String) {
    val t = Tt.colors
    Box(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
        Text(
            label,
            Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(t.glass2)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            style = chatStyle(11.5f, 15f),
            fontWeight = FontWeight.SemiBold,
            color = t.text3,
        )
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

/** The crew's own news: a small pill in the middle, green when it is good. */
@Composable
private fun BotLine(
    line: ChatLine,
    view: ChatView,
) {
    val t = Tt.colors
    val good = line.event in GoodNews
    Box(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(if (good) t.okBg else t.glass2)
                .padding(start = 5.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotFace(20.dp)
            Spacer(Modifier.width(7.dp))
            Text(
                view.plain(line.text),
                style = chatStyle(12.5f, 17f),
                fontWeight = if (good) FontWeight.SemiBold else FontWeight.Normal,
                color = if (good) t.ok else t.text2,
            )
        }
    }
}

/** A post from the TestTrack team: a dashed card, under the bot's name, marked as the team's. */
@Composable
private fun TeamPost(
    line: ChatLine,
    view: ChatView,
) {
    val t = Tt.colors
    Column(
        Modifier
            .padding(top = 12.dp, bottom = 2.dp)
            .fillMaxWidth()
            .dashed(t.glassBorder, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(t.bg)
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BotFace(26.dp)
            Spacer(Modifier.width(8.dp))
            Text(view.botName, style = chatStyle(13.5f, 18f), fontWeight = FontWeight.Bold, color = t.text)
            Spacer(Modifier.width(8.dp))
            Text(
                "TestTrack team",
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(t.tile)
                    .padding(horizontal = 8.dp, vertical = 1.dp),
                style = chatStyle(11.5f, 15f),
                fontWeight = FontWeight.Bold,
                color = t.accentInk,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            TimeText(ChatThread.clock(line.at))
        }
        Spacer(Modifier.height(4.dp))
        Text(view.plain(line.text), style = chatStyle(14.5f, 20f), color = t.text)
    }
}

/**
 * The message a reply quotes: who wrote it and its first line. [ink] draws it on a coloured bubble,
 * where the theme's own text colours would not show.
 */
@Composable
internal fun Quote(
    quote: QuoteView,
    modifier: Modifier = Modifier,
    fill: Color = Tt.colors.bg,
    maxWidth: Dp = 240.dp,
    ink: Color? = null,
) {
    val t = Tt.colors
    val bar = ink ?: t.accentInk
    Row(
        modifier
            .widthIn(max = maxWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 11.dp, end = 8.dp, top = 4.dp, bottom = 5.dp),
    ) {
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

/** Under a message that did not go: why, and the way to try again. */
@Composable
internal fun FailedRow(
    line: ChatLine,
    actions: ChatActions,
) {
    val t = Tt.colors
    Column(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), horizontalAlignment = Alignment.End) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Lucide.CircleAlert, null, Modifier.size(14.dp), tint = t.bad)
            Spacer(Modifier.width(6.dp))
            Text("Not sent.", style = chatStyle(12.5f, 17f), color = t.bad)
            Row(
                Modifier
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { actions.onRetryLine(line) }
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Lucide.RotateCcw, null, Modifier.size(13.dp), tint = t.accentInk)
                Spacer(Modifier.width(4.dp))
                Text("Try again", style = chatStyle(12.5f, 17f), fontWeight = FontWeight.Bold, color = t.accentInk)
            }
        }
        if (line.error.isNotBlank()) Text(line.error, style = chatStyle(12f, 16f), color = t.text3)
    }
}

/** Nobody has written yet: a quiet mark, a title and one line, centred where it is put. */
@Composable
internal fun ChatEmptyNote(
    canPost: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = Tt.colors
    Column(modifier.padding(horizontal = 32.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(t.glass2), contentAlignment = Alignment.Center) {
            Box(Modifier.size(68.dp).clip(CircleShape).background(t.tile), contentAlignment = Alignment.Center) {
                Icon(Lucide.MessagesSquare, null, Modifier.size(30.dp), tint = t.tileInk)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("No messages yet", style = chatStyle(17f, 22f), fontWeight = FontWeight.SemiBold, color = t.text)
        Spacer(Modifier.height(4.dp))
        Text(
            if (canPost) "Say hi to your crew." else "Nobody wrote in this chat.",
            style = chatStyle(14f, 20f),
            color = t.text3,
            textAlign = TextAlign.Center,
        )
    }
}
