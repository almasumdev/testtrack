package com.eazyverse.testtrack.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a crew's chat stands, from the crew and from the chat itself. */
enum class ChatState {
    /** No chat on this server, or none for this crew. */
    OFF,
    OPEN,

    /** The crew ended, or the reader's app is out: readable, not writable. */
    READ_ONLY,

    /** Deleted with the crew's details. */
    GONE,
    ;

    companion object {
        fun of(wire: String): ChatState =
            when (wire) {
                "open" -> OPEN
                "read_only" -> READ_ONLY
                "gone" -> GONE
                else -> OFF
            }
    }
}

/** A seat in the crew: the person in it and their app. */
data class ChatPerson(
    val seatId: String,
    val name: String,
    val photo: String = "",
    val email: String = "",
    val appName: String = "",
    val appIcon: String = "",
    val packageName: String = "",
    /** Their own night of the run. */
    val day: Int = 0,
    val inCrew: Boolean = true,
    val mine: Boolean = false,
)

enum class ChatKind {
    /** A member's own message. */
    USER,

    /** The crew's own news, such as a night starting. */
    BOT,

    /** A post from the TestTrack team, shown under the bot's name. */
    TEAM,
}

/** A mention inside a message: an app or a member, by their seat. */
data class ChatMention(
    val app: Boolean,
    val seatId: String,
    val label: String,
)

/** The message a reply quotes. */
data class ChatQuote(
    val id: String,
    val authorSeatId: String?,
    val excerpt: String,
    val picture: Boolean = false,
    val deleted: Boolean = false,
)

/** A message's picture: [expired] once its day is up. [local] is a picture still being sent. */
data class ChatPicture(
    val id: String,
    val width: Int = 0,
    val height: Int = 0,
    val expired: Boolean = false,
    val expiresAt: Instant? = null,
    val local: String = "",
)

enum class SendState { SENT, SENDING, FAILED }

/** One line of the chat as the phone holds it. */
data class ChatLine(
    val id: String,
    val seq: Long = 0,
    val rev: Long = 0,
    val kind: ChatKind = ChatKind.USER,
    val authorSeatId: String? = null,
    val mine: Boolean = false,
    /** Mention tokens kept inline: `@[seat:<id>]` and `@[app:<id>]`. */
    val text: String = "",
    val event: String = "",
    val mentions: List<ChatMention> = emptyList(),
    val quote: ChatQuote? = null,
    val picture: ChatPicture? = null,
    val deleted: Boolean = false,
    val at: Instant? = null,
    val send: SendState = SendState.SENT,
    /** Why it was not sent, for a failed line. */
    val error: String = "",
)

/** What the list draws, in order from the top. */
sealed interface ChatRow {
    val key: String

    data class Day(
        val label: String,
    ) : ChatRow {
        override val key = "day-$label"
    }

    data class New(
        val count: Int,
    ) : ChatRow {
        override val key = "new"
    }

    /**
     * [first] starts a run by one author, where their name shows; [last] ends it, where their face
     * shows. A line on its own is both.
     */
    data class Line(
        val line: ChatLine,
        val first: Boolean,
        val last: Boolean = true,
    ) : ChatRow {
        override val key = line.id
    }
}

/** The thread's arithmetic: merging what each poll brings, and laying it out with its dividers. */
object ChatThread {
    /**
     * [incoming] laid over [current]. A message the server sent again (a delete, an expired
     * picture) replaces the one held; [reset] starts again from [incoming]. Lines still sending, or
     * failed, have no place yet and stay last, in the order they were written.
     */
    fun merge(
        current: List<ChatLine>,
        incoming: List<ChatLine>,
        reset: Boolean = false,
    ): List<ChatLine> {
        val byId = LinkedHashMap<String, ChatLine>()
        if (!reset) current.filter { it.send == SendState.SENT }.forEach { byId[it.id] = it }
        incoming.forEach { byId[it.id] = it }
        val sent = byId.values.sortedBy { it.seq }
        val waiting = current.filter { it.send != SendState.SENT && it.id !in byId }
        return sent + waiting
    }

    /** The oldest seq held, for asking for the page before it; null with nothing held. */
    fun oldest(lines: List<ChatLine>): Long? = lines.filter { it.send == SendState.SENT && it.seq > 0 }.minOfOrNull { it.seq }

    /**
     * Others' messages added after [readRev]: what the "new" line counts. A message's seq is the rev
     * it was added at, so it is what the read mark is compared with. Bot lines are news, not messages.
     */
    fun unread(
        lines: List<ChatLine>,
        readRev: Long,
    ): List<ChatLine> = lines.filter { it.seq > readRev && !it.mine && !it.deleted && it.kind != ChatKind.BOT && it.send == SendState.SENT }

    /**
     * The rows, oldest first: a day label wherever the day changes, the "new" line over the first
     * message unread between [newFromRev] and [newToRev], and each message marked
     * [ChatRow.Line.first] where a new author starts and [ChatRow.Line.last] where that run ends.
     */
    fun rows(
        lines: List<ChatLine>,
        newFromRev: Long?,
        newToRev: Long = Long.MAX_VALUE,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): List<ChatRow> {
        val rows = ArrayList<ChatRow>(lines.size + 8)
        val fresh = if (newFromRev == null) emptyList() else unread(lines, newFromRev).filter { it.seq <= newToRev }
        val firstNew = fresh.firstOrNull()?.id
        var day: LocalDate? = null
        var last: ChatLine? = null
        for (line in lines) {
            val date = line.at?.atZone(zone)?.toLocalDate()
            var broke = false
            if (date != null && date != day) {
                rows += ChatRow.Day(dayLabel(date, today))
                day = date
                broke = true
            }
            if (line.id == firstNew) {
                rows += ChatRow.New(fresh.size)
                broke = true
            }
            val prev = last
            val sameAuthor = prev != null && prev.authorSeatId == line.authorSeatId && prev.mine == line.mine
            val bothUser = prev?.kind == ChatKind.USER && line.kind == ChatKind.USER
            val joined = !broke && sameAuthor && bothUser
            if (joined) {
                val at = rows.lastIndex
                rows[at] = (rows[at] as ChatRow.Line).copy(last = false)
            }
            rows += ChatRow.Line(line, first = !joined)
            last = line
        }
        return rows
    }

    /** "Today", "Yesterday", or the date, with the year only when it is not this one. */
    fun dayLabel(
        date: LocalDate,
        today: LocalDate,
    ): String =
        when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> {
                val pattern = if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"
                date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
            }
        }

    /** "8:15 PM", in the phone's own time. */
    fun clock(
        at: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String = at?.atZone(zone)?.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).orEmpty()

    /** "Gone in 21 h" for a picture still kept; blank once it is gone or with no time. */
    fun pictureLeft(
        expiresAt: Instant?,
        now: Instant = Instant.now(),
    ): String {
        if (expiresAt == null || !expiresAt.isAfter(now)) return ""
        val minutes = Duration.between(now, expiresAt).toMinutes()
        return if (minutes >= 60) "Gone in ${minutes / 60} h" else "Gone in ${minutes.coerceAtLeast(1)} min"
    }
}
