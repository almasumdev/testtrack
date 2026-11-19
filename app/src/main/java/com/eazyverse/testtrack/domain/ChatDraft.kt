package com.eazyverse.testtrack.domain

/** A mention picked from the suggestion list, sitting at [start] until [end] in the draft as "@Label". */
data class MentionSpan(
    val start: Int,
    val end: Int,
    val app: Boolean,
    val seatId: String,
)

/**
 * What is being written: the words as typed, and where in them a picked mention sits.
 *
 * A mention is shown as its label while it is written and sent as a token the server resolves,
 * `@[seat:<id>]` for a member and `@[app:<id>]` for an app. Editing inside one turns it back into
 * plain words, so a half-deleted name is never sent as somebody.
 */
data class ChatDraft(
    val text: String = "",
    val spans: List<MentionSpan> = emptyList(),
) {
    /** Blank, with nothing but spaces in it. */
    val empty: Boolean get() = text.isBlank()

    /** The text as the server wants it, each mention as its token. */
    fun toWire(): String {
        val out = StringBuilder(text)
        spans.sortedByDescending { it.start }.forEach { span ->
            out.replace(span.start, span.end, ChatText.token(span.app, span.seatId))
        }
        return out.toString().trim()
    }

    /** The text the link check reads: each mention as a space, as the server does with tokens. */
    fun forLinkCheck(): String {
        val out = StringBuilder(text)
        spans.sortedByDescending { it.start }.forEach { out.replace(it.start, it.end, " ") }
        return out.toString()
    }

    /** Characters as the server counts them: by code point, a mention as one. */
    fun visibleLength(): Int {
        val trimmed = text.trim()
        val offset = text.indexOf(trimmed).coerceAtLeast(0)
        var count = trimmed.codePointCount(0, trimmed.length)
        spans.forEach { span ->
            val s = (span.start - offset).coerceIn(0, trimmed.length)
            val e = (span.end - offset).coerceIn(0, trimmed.length)
            if (e > s) count -= trimmed.codePointCount(s, e) - 1
        }
        return count
    }

    /**
     * The draft after the field became [next]. The changed stretch is found from both ends; a
     * mention wholly before it stays, one after it moves with it, and one it touches is dropped.
     */
    fun edit(next: String): ChatDraft {
        if (next == text) return this
        var prefix = 0
        val max = minOf(text.length, next.length)
        while (prefix < max && text[prefix] == next[prefix]) prefix++
        var suffix = 0
        while (suffix < max - prefix && text[text.length - 1 - suffix] == next[next.length - 1 - suffix]) suffix++
        val oldEnd = text.length - suffix
        val delta = next.length - text.length
        val kept =
            spans.mapNotNull { span ->
                when {
                    span.end <= prefix -> span
                    span.start >= oldEnd -> span.copy(start = span.start + delta, end = span.end + delta)
                    else -> null
                }
            }
        return ChatDraft(next, kept)
    }

    /**
     * [label] put in for the "@" at [at] and whatever was typed after it, up to [until] and on to
     * the end of that word, with a space after. Answers the draft and where the cursor goes.
     */
    fun mention(
        at: Int,
        label: String,
        app: Boolean,
        seatId: String,
        until: Int = 0,
    ): Pair<ChatDraft, Int> {
        val from = at.coerceIn(0, text.length)
        var to = maxOf(until.coerceIn(0, text.length), if (from < text.length && text[from] == '@') from + 1 else from)
        while (to < text.length && !text[to].isWhitespace()) to++
        val put = "@$label "
        val next = text.substring(0, from) + put + text.substring(to)
        val delta = put.length - (to - from)
        val kept =
            spans.mapNotNull { span ->
                when {
                    span.end <= from -> span
                    span.start >= to -> span.copy(start = span.start + delta, end = span.end + delta)
                    else -> null
                }
            }
        val added = MentionSpan(from, from + put.length - 1, app, seatId)
        return ChatDraft(next, (kept + added).sortedBy { it.start }) to from + put.length
    }
}

/** The pieces a sent message is drawn from: plain words and the mentions between them. */
sealed interface ChatPiece {
    data class Words(
        val text: String,
    ) : ChatPiece

    data class Mention(
        val app: Boolean,
        val seatId: String,
    ) : ChatPiece
}

object ChatText {
    private val TOKEN = Regex("@\\[(seat|app):([0-9a-fA-F-]{8,40})]")

    fun token(
        app: Boolean,
        seatId: String,
    ) = "@[${if (app) "app" else "seat"}:$seatId]"

    /** [text] cut at its mention tokens. */
    fun pieces(text: String): List<ChatPiece> {
        val out = ArrayList<ChatPiece>()
        var from = 0
        for (match in TOKEN.findAll(text)) {
            if (match.range.first > from) out += ChatPiece.Words(text.substring(from, match.range.first))
            out += ChatPiece.Mention(match.groupValues[1] == "app", match.groupValues[2])
            from = match.range.last + 1
        }
        if (from < text.length) out += ChatPiece.Words(text.substring(from))
        return out
    }

    /** The words with each mention as "@Label", for copying and for a notification. */
    fun plain(
        text: String,
        label: (app: Boolean, seatId: String) -> String,
    ): String =
        pieces(text).joinToString("") {
            when (it) {
                is ChatPiece.Words -> it.text
                is ChatPiece.Mention -> "@" + label(it.app, it.seatId)
            }
        }
}
