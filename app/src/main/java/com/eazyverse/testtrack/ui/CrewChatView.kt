package com.eazyverse.testtrack.ui

import androidx.compose.ui.text.input.TextFieldValue
import com.eazyverse.testtrack.domain.ChatDraft
import com.eazyverse.testtrack.domain.ChatKind
import com.eazyverse.testtrack.domain.ChatLimits
import com.eazyverse.testtrack.domain.ChatLine
import com.eazyverse.testtrack.domain.ChatPerson
import com.eazyverse.testtrack.domain.ChatPicks
import com.eazyverse.testtrack.domain.ChatPiece
import com.eazyverse.testtrack.domain.ChatQuote
import com.eazyverse.testtrack.domain.ChatRow
import com.eazyverse.testtrack.domain.ChatState
import com.eazyverse.testtrack.domain.ChatText
import com.eazyverse.testtrack.domain.DraftProblem

// What the chat page draws and what it can do, as plain values the page and the gallery share.

/** Everything the chat page draws, as plain values. */
internal data class ChatView(
    val crewName: String,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val state: ChatState = ChatState.OPEN,
    val canPost: Boolean = true,
    /** Said in place of the composer when posting is closed. */
    val closedLine: String = "",
    val rows: List<ChatRow> = emptyList(),
    val people: Map<String, ChatPerson> = emptyMap(),
    val mySeat: String? = null,
    val muted: Boolean = false,
    val messagesLeft: Int = 0,
    val picturesLeft: Int = 0,
    val limits: ChatLimits = ChatLimits(),
    val botName: String = "Crew bot",
    val draft: ChatDraft = ChatDraft(),
    val replyTo: ChatLine? = null,
    val problem: DraftProblem = DraftProblem.NONE,
    val loadingOlder: Boolean = false,
    /** The messages picked with a hold, and what the bar over the page offers for them. */
    val picks: ChatPicks = ChatPicks(),
    /** The message tapped for its time, shown as a time line over it. */
    val timeShown: String? = null,
    /** A quote was tapped: the thread moves to the message it quotes. */
    val jump: ChatJump? = null,
    /** The message just gone to, washed with a fading tint. */
    val flash: ChatJump? = null,
    /** Older pages are being read to find a quoted message. */
    val seeking: Boolean = false,
    /** The crew has a members page to go to. */
    val members: Boolean = false,
    val message: String? = null,
) {
    /** Anything said by a member or the team, rather than only the bot's news. */
    val talk: Boolean get() = rows.any { it is ChatRow.Line && it.line.kind != ChatKind.BOT }

    /** The reader can post here, so a message can be answered. */
    val writable: Boolean get() = canPost && state == ChatState.OPEN

    fun name(seatId: String?): String = seatId?.let { people[it]?.name }?.ifBlank { null } ?: "Someone"

    /** Who wrote [line]: the bot's name for the bot's news and the team's posts, else the member's. */
    fun authorName(line: ChatLine): String = if (line.kind == ChatKind.USER) name(line.authorSeatId) else botName

    fun mentionLabel(
        line: ChatLine,
        app: Boolean,
        seatId: String,
    ): String =
        line.mentions
            .firstOrNull { it.app == app && it.seatId == seatId }
            ?.label
            ?.ifBlank { null }
            ?: people[seatId]?.let { if (app) it.appName else it.name }?.ifBlank { null }
            ?: if (app) "an app" else "someone"

    /** Words with each mention as "@Label", for a quote, a bot line or the clipboard. */
    fun plain(text: String): String =
        ChatText.plain(text) { app, id -> people[id]?.let { if (app) shortAppName(it.appName) else it.name } ?: "someone" }

    /** The message names the reader, or the reader's app. */
    fun mentionsMe(line: ChatLine): Boolean =
        mySeat != null &&
            (line.mentions.any { it.seatId == mySeat } || ChatText.pieces(line.text).any { it is ChatPiece.Mention && it.seatId == mySeat })

    fun quoteOf(quote: ChatQuote): QuoteView =
        QuoteView(
            // Only the bot and the team write with no seat, so a quote with none is theirs.
            who =
                when (quote.authorSeatId) {
                    null -> botName
                    mySeat -> "You"
                    else -> name(quote.authorSeatId)
                },
            text =
                when {
                    quote.deleted -> "Message deleted"
                    quote.excerpt.isBlank() && quote.picture -> "Picture"
                    else -> plain(quote.excerpt)
                },
            deleted = quote.deleted,
        )
}

/** A quoted message, ready to draw. */
internal data class QuoteView(
    val who: String,
    val text: String,
    val deleted: Boolean = false,
)

/** What the chat page can do. */
internal class ChatActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onMute: () -> Unit = {},
    val onMembers: () -> Unit = {},
    val onField: (TextFieldValue) -> Unit = {},
    /** The @ button: puts an "@" at the cursor, which brings up the list of people the same as typing it. */
    val onAt: () -> Unit = {},
    /** Someone picked from that list: the draft with the mention in, and where the cursor goes. */
    val onMention: (ChatDraft, Int) -> Unit = { _, _ -> },
    val onSend: () -> Unit = {},
    val onPickPicture: () -> Unit = {},
    val onClearReply: () -> Unit = {},
    /** A message held down picks it; while any are picked, a tap picks or unpicks. */
    val onSelect: (ChatLine) -> Unit = {},
    val onClearSelection: () -> Unit = {},
    /** Answer a message: a swipe on it, or Reply in the bar. */
    val onReply: (ChatLine) -> Unit = {},
    val onReplyPicked: () -> Unit = {},
    val onCopyPicked: () -> Unit = {},
    val onDeletePicked: () -> Unit = {},
    val onFace: (String) -> Unit = {},
    val onRetryLine: (ChatLine) -> Unit = {},
    val onPicture: (ChatLine) -> Unit = {},
    /** A message tapped: show its time, or hide it again. */
    val onTime: (ChatLine) -> Unit = {},
    /** A reply's quote tapped: go to the message it quotes. */
    val onQuote: (ChatQuote) -> Unit = {},
    val onLoadOlder: () -> Unit = {},
    val onClearMessage: () -> Unit = {},
)
