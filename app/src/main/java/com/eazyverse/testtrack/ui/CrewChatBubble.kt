package com.eazyverse.testtrack.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.lucide.BadgeCheck
import com.composables.icons.lucide.Ban
import com.composables.icons.lucide.HandHeart
import com.composables.icons.lucide.Lucide
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatSelect
import com.eazyverse.testtrack.domain.SendState
import com.eazyverse.testtrack.ui.theme.Tt

/*
 * A member's message, drawn the way chat apps do: yours on the right in the accent, theirs on the
 * left on a soft surface. A run of messages by one author sits close together, with the name on
 * its first and the face on its last, and the corners toward the author tucked in where the run
 * goes on. No time sits inside a bubble: the thread's time lines carry it, as in Messenger.
 * The bot and the team write the same way, under the bot's name and face, with a check mark after
 * the name and a lighter wash of the accent on the bubble.
 */

/** The page's side margin: the thread keeps this much clear, and a picked row's wash reaches across it. */
internal val ChatGutter = 12.dp

private val Round = 18.dp
private val Tucked = 4.dp

/**
 * About three quarters of the screen: the widest a bubble grows. The one helper for every bubble.
 * [inset] comes off the screen's width first, such as a review thread's two gutters.
 */
@Composable
internal fun bubbleMax(
    share: Float = 0.75f,
    inset: Dp = 0.dp,
): Dp = (LocalConfiguration.current.screenWidthDp.dp - inset) * share

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

/** A bubble's fill and the ink on it. */
internal data class BubbleInk(
    val fill: Color,
    val ink: Color,
)

@Composable
internal fun bubbleInk(mine: Boolean): BubbleInk {
    val t = Tt.colors
    return if (mine) BubbleInk(t.accent, t.onAccent) else BubbleInk(t.glass2, t.text)
}

/** A thin red edge round a message that did not go. */
@Composable
internal fun Modifier.bubbleEdge(
    line: ChatLine,
    shape: RoundedCornerShape,
): Modifier = if (line.send == SendState.FAILED) border(1.5.dp, Tt.colors.badBorder, shape) else this

/** What a quote inside a message does: a tap goes to the message it quotes, a hold picks the message. */
internal class QuoteTap(
    val onTap: () -> Unit,
    val onHold: () -> Unit,
)

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.quoteTap(tap: QuoteTap): Modifier =
    combinedClickable(
        onClickLabel = "Go to the message",
        onLongClickLabel = "Select",
        onClick = tap.onTap,
        onLongClick = tap.onHold,
    )

/** Someone's message that names you: a faint wash of the accent over its fill, never an edge. */
@Composable
internal fun Modifier.mentionWash(you: Boolean): Modifier = if (you) background(Tt.colors.mentionTint) else this

/** A message from the bot or the team: a lighter wash of the accent, so it reads as theirs. */
@Composable
internal fun Modifier.teamWash(team: Boolean): Modifier = if (team) background(Tt.colors.botTint) else this

/** The space over a message: tight inside a run by one author, wider where a new run starts. */
internal fun groupGap(first: Boolean): Dp = if (first) 12.dp else 4.dp

/** The shortest a one-line bubble is: its line of text with the bubble's own padding over and under. */
internal val BubbleMinHeight = 34.dp
internal val BubblePadX = 12.dp
internal val BubblePadY = 7.dp

/** Behind a picked message, across the whole row: a light wash of the accent, reaching to the screen's edges. */
@Composable
internal fun Modifier.pickedWash(picked: Boolean): Modifier {
    if (!picked) return this
    val tint = Tt.colors.primary.copy(alpha = 0.14f)
    return drawBehind {
        val edge = ChatGutter.toPx()
        drawRect(tint, topLeft = Offset(-edge, 0f), size = Size(size.width + 2 * edge, size.height))
    }
}

/**
 * One message, with its face, name and what can be done with it. A tap shows its time (or retries
 * a failed one, or opens a picture); your own show how the send went under them. The bot's and the
 * team's have the bot's face and name, and no profile behind the face. A hold picks it, and while
 * any are picked a tap picks or unpicks; otherwise a drag toward the middle replies.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChatMessage(
    row: ChatRow.Line,
    view: ChatView,
    actions: ChatActions,
) {
    val line = row.line
    val first = row.first
    val last = row.last
    val team = line.kind != ChatKind.USER
    val person = line.authorSeatId?.let { view.people[it] }
    val picked = view.picks.has(line.id)
    val picking = view.picks.active
    val swipe = remember { ReplySwipe() }
    val swipeable = !picking && line.kind != ChatKind.BOT && ChatSelect.canReply(line, view.writable)
    val picture = line.picture
    val opens = picture != null && !line.deleted && !picture.expired && line.send == SendState.SENT
    Box(
        Modifier
            .fillMaxWidth()
            .pickedWash(picked)
            .padding(top = groupGap(first))
            .semantics { selected = picked },
    ) {
        if (swipeable) ReplyMark(swipe, line.mine)
        Row(
            Modifier.fillMaxWidth().swipeToReply(swipe, swipeable, line.mine) { actions.onReply(line) },
            horizontalArrangement = if (line.mine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!line.mine) {
                if (last && team) {
                    BotFace(28.dp)
                } else if (last) {
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .clickable(onClickLabel = "See who this is") {
                                if (picking) actions.onSelect(line) else line.authorSeatId?.let(actions.onFace)
                            },
                    ) { Avatar(person?.photo.orEmpty(), initialOf(person?.name ?: "?"), size = 28.dp) }
                } else {
                    Spacer(Modifier.width(28.dp))
                }
                Spacer(Modifier.width(8.dp))
            }
            // Only the quote goes to the message it quotes; the rest of the bubble keeps its own tap.
            val hold = { if (ChatSelect.canPick(line, view.writable)) actions.onSelect(line) }
            val quoteTap =
                line.quote?.let { quote ->
                    QuoteTap(onTap = { if (picking) hold() else actions.onQuote(quote) }, onHold = hold)
                }
            Column(horizontalAlignment = if (line.mine) Alignment.End else Alignment.Start) {
                if (!line.mine && first) AuthorName(view.authorName(line), team, volunteer = person?.volunteer == true)
                Box(
                    Modifier
                        .clip(bubbleShape(line.mine, first, last))
                        .jumpFlash(view.flash, line.id)
                        .combinedClickable(
                            onClickLabel =
                                when {
                                    picking -> if (picked) "Unselect" else "Select"
                                    line.send == SendState.FAILED -> "Try again"
                                    opens -> "Open the picture"
                                    !row.stamped -> "Show the time"
                                    else -> null
                                },
                            onLongClickLabel = "Select",
                            onClick = {
                                when {
                                    picking -> hold()
                                    line.send == SendState.FAILED -> actions.onRetryLine(line)
                                    opens -> actions.onPicture(line)
                                    !row.stamped -> actions.onTime(line)
                                }
                            },
                            onLongClick = hold,
                        ),
                ) {
                    when {
                        line.deleted -> DeletedStub(line, first, last)
                        picture != null -> ChatPictureBubble(line, picture, first, last, view, quoteTap)
                        else -> TextBubble(line, first, last, view, quoteTap, team)
                    }
                }
                when {
                    line.send == SendState.FAILED -> FailedRow(line, actions)
                    line.send == SendState.SENDING -> SendNote(sending = true)
                    row.newestMine && line.mine && !line.deleted -> SendNote(sending = false)
                }
            }
        }
    }
}

/** Over the first of someone else's run: their name, and the check mark when it is the team. Shared with the thread. */
@Composable
internal fun AuthorName(
    name: String,
    team: Boolean,
    /** The widest it runs, the bubble's own; [Dp.Unspecified] for the column's whole width. */
    max: Dp = bubbleMax(),
    /** Round it: 12 in from the bubble's edge and 2 over it unless given. */
    padding: PaddingValues = PaddingValues(start = 12.dp, bottom = 2.dp),
    /** The author volunteers in the crew: their tag after the name. */
    volunteer: Boolean = false,
) {
    val t = Tt.colors
    Row(
        Modifier.widthIn(max = max).padding(padding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            Modifier.weight(1f, fill = false),
            style = chatStyle(12f, 16f),
            fontWeight = FontWeight.SemiBold,
            color = t.text3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (team) {
            Spacer(Modifier.width(3.dp))
            Icon(Lucide.BadgeCheck, "TestTrack team", Modifier.size(12.dp), tint = t.accentInk)
        }
        if (volunteer) {
            Spacer(Modifier.width(6.dp))
            StatusChip("Volunteer", Tone.TILE, Lucide.HandHeart, size = ChipSize.SMALL)
        }
    }
}

/** Words only: an optional quote, then the words. */
@Composable
private fun TextBubble(
    line: ChatLine,
    first: Boolean,
    last: Boolean,
    view: ChatView,
    quoteTap: QuoteTap?,
    team: Boolean,
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
            .teamWash(team)
            .mentionWash(you)
            .bubbleEdge(line, shape)
            .padding(horizontal = BubblePadX, vertical = BubblePadY),
    ) {
        line.quote?.let { quote ->
            Quote(
                view.quoteOf(quote),
                Modifier.padding(bottom = 5.dp).fillMaxWidth(),
                fill = if (line.mine) ink.ink.copy(alpha = 0.16f) else Tt.colors.bg,
                maxWidth = bubbleMax() - 24.dp,
                ink = if (line.mine) ink.ink else null,
                tap = quoteTap,
            )
        }
        ChatMessageText(line, view, ink = ink.ink)
    }
}

/**
 * A message taken back: its outline and a word. Sized like a normal one-line bubble of the same
 * author, with the same padding and corners, so it neither shrinks nor crowds the next message.
 */
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
            .heightIn(min = BubbleMinHeight)
            .dashed(t.glassBorder, Round, shape = shape)
            .padding(horizontal = BubblePadX, vertical = BubblePadY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Lucide.Ban, null, Modifier.size(14.dp), tint = t.text4)
        Spacer(Modifier.width(6.dp))
        Text("Message deleted", style = chatStyle(15f, 20f), fontStyle = FontStyle.Italic, color = t.text4)
    }
}
