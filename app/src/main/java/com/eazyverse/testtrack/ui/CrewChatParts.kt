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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.CircleAlert
import com.composables.icons.lucide.Clock
import com.composables.icons.lucide.ImageOff
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.RotateCcw
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatThread
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * The pieces of a crew's chat thread, as the approved design draws them: members' bubbles, the
 * bot's lines, the team's posts, and the dividers between days and before what is new.
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
                ChatKind.USER -> Message(row.line, row.first, view, actions)
            }
    }
}

@Composable
private fun DayDivider(label: String) {
    Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
        Text(label, style = chatStyle(12f, 16f), fontWeight = FontWeight.SemiBold, color = Tt.colors.text3)
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
            BotFace(view.botAvatar, 20.dp)
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
            BotFace(view.botAvatar, 26.dp)
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

/** A member's message: theirs on the left with a face, yours on the right in the container colour. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Message(
    line: ChatLine,
    first: Boolean,
    view: ChatView,
    actions: ChatActions,
) {
    val t = Tt.colors
    val person = line.authorSeatId?.let { view.people[it] }
    val lifted by animateFloatAsState(if (view.lifted == line.id) 1.04f else 1f, label = "chat-lift")
    Row(
        Modifier.fillMaxWidth().padding(top = if (first) 10.dp else 3.dp),
        horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!line.mine) {
            if (first) {
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
        Column(
            Modifier.fillMaxWidth(0.78f),
            horizontalAlignment = if (line.mine) Alignment.End else Alignment.Start,
        ) {
            if (!line.mine && first) {
                Text(
                    person?.name ?: "Someone",
                    Modifier.padding(start = 12.dp, bottom = 3.dp),
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
                    }.combinedClickable(
                        onClickLabel = if (line.send == SendState.FAILED) "Try again" else null,
                        onLongClickLabel = "Message options",
                        onClick = { if (line.send == SendState.FAILED) actions.onRetryLine(line) },
                        onLongClick = { if (!line.deleted && line.send != SendState.SENDING) actions.onLongPress(line) },
                    ),
            ) {
                if (line.deleted) DeletedStub(first, line.mine) else Bubble(line, first, view, actions)
            }
            when (line.send) {
                SendState.SENDING ->
                    Row(Modifier.padding(start = 10.dp, end = 10.dp, top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Lucide.Clock, null, Modifier.size(11.dp), tint = t.text4)
                        Spacer(Modifier.width(4.dp))
                        TimeText("Sending")
                    }
                SendState.FAILED -> FailedRow(line, actions)
                SendState.SENT -> TimeText(ChatThread.clock(line.at), Modifier.padding(start = 10.dp, end = 10.dp, top = 3.dp))
            }
        }
    }
}

@Composable
private fun Bubble(
    line: ChatLine,
    first: Boolean,
    view: ChatView,
    actions: ChatActions,
) {
    val t = Tt.colors
    val shape = bubbleShape(line.mine, first)
    val you = !line.mine && view.mentionsMe(line)
    val picture = line.picture
    Column(
        Modifier
            .alpha(if (line.send == SendState.SENDING) 0.62f else 1f)
            .clip(shape)
            .background(if (line.mine) t.container else t.sheet)
            .border(
                when {
                    line.send == SendState.FAILED -> 1.5.dp
                    you -> 1.5.dp
                    line.mine -> 0.dp
                    else -> 1.dp
                },
                when {
                    line.send == SendState.FAILED -> t.badBorder
                    you -> t.accent
                    else -> t.cardLine
                },
                shape,
            ).then(if (picture != null) Modifier.width(226.dp) else Modifier)
            .padding(
                if (picture != null) {
                    PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 7.dp)
                } else {
                    PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                },
            ),
    ) {
        val inner = if (picture != null) Modifier.padding(horizontal = 8.dp) else Modifier
        line.quote?.let { quote ->
            val room = if (picture != null) Modifier.padding(4.dp) else Modifier
            Quote(view.quoteOf(quote), Modifier.padding(bottom = 6.dp).then(room))
        }
        if (picture != null) {
            when {
                picture.expired -> ExpiredPicture()
                picture.local.isNotBlank() -> {
                    Box(Modifier.width(216.dp).clip(RoundedCornerShape(14.dp))) {
                        SubcomposeAsyncImage(
                            model = picture.local,
                            contentDescription = "Your picture",
                            modifier = Modifier.width(216.dp).height(160.dp),
                            contentScale = ContentScale.Crop,
                        )
                        if (line.send == SendState.SENDING) {
                            LinearProgressIndicator(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(10.dp)
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = t.primary,
                                trackColor = t.track,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                else -> {
                    Box(
                        Modifier
                            .width(216.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(onClickLabel = "Open the picture") { actions.onPicture(picture.id) },
                    ) { ProofImage(picture.id) }
                    Spacer(Modifier.height(6.dp))
                    val left = ChatThread.pictureLeft(picture.expiresAt)
                    if (left.isNotBlank()) {
                        Row(inner.padding(bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Lucide.Clock, null, Modifier.size(12.dp), tint = t.text4)
                            Spacer(Modifier.width(4.dp))
                            Text(left, style = chatStyle(11.5f, 15f), color = t.text4)
                        }
                    }
                }
            }
        }
        if (line.text.isNotBlank()) {
            ChatMessageText(line, view, inner, ink = if (line.mine) t.onContainer else t.text)
        }
    }
}

/** The corners of a bubble: round, with the one by its author tucked in, and both on a run's later lines. */
private fun bubbleShape(
    mine: Boolean,
    first: Boolean,
): RoundedCornerShape {
    val big = 18.dp
    val small = 6.dp
    return when {
        mine && first -> RoundedCornerShape(big, big, small, big)
        mine -> RoundedCornerShape(big, small, small, big)
        first -> RoundedCornerShape(big, big, big, small)
        else -> RoundedCornerShape(small, big, big, small)
    }
}

/** The message a reply quotes: who wrote it and its first line. */
@Composable
internal fun Quote(
    quote: QuoteView,
    modifier: Modifier = Modifier,
    fill: Color = Tt.colors.bg,
    maxWidth: Dp = 240.dp,
) {
    val t = Tt.colors
    Row(
        modifier
            .widthIn(max = maxWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(fill)
            .drawBehind { drawRect(t.accentInk, size = Size(3.dp.toPx(), size.height)) }
            .padding(start = 11.dp, end = 8.dp, top = 4.dp, bottom = 5.dp),
    ) {
        Column {
            Text(quote.who, style = chatStyle(12f, 16f), fontWeight = FontWeight.Bold, color = t.accentInk, maxLines = 1)
            Text(
                quote.text,
                style = chatStyle(13f, 17f),
                fontStyle = if (quote.deleted) FontStyle.Italic else FontStyle.Normal,
                color = if (quote.deleted) t.text4 else t.text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpiredPicture() {
    val t = Tt.colors
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .width(216.dp)
            .height(88.dp)
            .dashed(t.glassBorder, 12.dp, width = 1.5.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(t.glass2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.ImageOff, null, Modifier.size(17.dp), tint = t.text3)
        Spacer(Modifier.width(8.dp))
        Text("Picture expired", style = chatStyle(13.5f, 18f), fontWeight = FontWeight.SemiBold, color = t.text3)
    }
}

@Composable
private fun DeletedStub(
    first: Boolean,
    mine: Boolean,
) {
    val t = Tt.colors
    val corner = if (mine) bubbleShape(true, first) else bubbleShape(false, first)
    Row(
        Modifier
            .dashed(t.glassBorder, 18.dp, shape = corner)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Ban, null, Modifier.size(14.dp), tint = t.text4)
        Spacer(Modifier.width(6.dp))
        Text("Message deleted", style = chatStyle(13.5f, 18f), fontStyle = FontStyle.Italic, color = t.text4)
    }
}

@Composable
private fun FailedRow(
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
