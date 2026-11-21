package com.eazyverse.testtrack.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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
    /** A volunteer: in the crew with no app of their own, and [seatId] is their volunteer id. */
    val volunteer: Boolean = false,
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

    /** A time line over [before], the id of the message it heads: where talk starts again after a pause. */
    data class Time(
        val at: Instant,
        val label: String,
        val before: String,
    ) : ChatRow {
        override val key = "time-$before"
    }

    data class New(
        val count: Int,
    ) : ChatRow {
        override val key = "new"
    }

    /**
     * [first] starts a run by one author, where their name shows; [last] ends it, where their face
     * shows. A line on its own is both. [stamped] sits right under a time line, so tapping it shows
     * no second one. [newestMine] is the reader's own latest message, where "Sent" shows.
     */
    data class Line(
        val line: ChatLine,
        val first: Boolean,
        val last: Boolean = true,
        val stamped: Boolean = false,
        val newestMine: Boolean = false,
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

    /** The pause after which the same author writing again gets a new time line. */
    val GAP: Duration = Duration.ofMinutes(15)

    /**
     * The rows, oldest first: a time line over the first message, over every message that starts a
     * new run (the author or the voice changes), and wherever [GAP] or more passed since the one
     * before. The "new" line goes over the first message unread between [newFromRev] and [newToRev],
     * after any time line. Each message is marked [ChatRow.Line.first] where a run by one author
     * starts and [ChatRow.Line.last] where it ends. A time line ends a run, as the new line does.
     * The bot and the team are one author: their lines run together, never with a member's.
     */
    fun rows(
        lines: List<ChatLine>,
        newFromRev: Long?,
        newToRev: Long = Long.MAX_VALUE,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        hour24: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): List<ChatRow> {
        val rows = ArrayList<ChatRow>(lines.size + 8)
        val fresh = if (newFromRev == null) emptyList() else unread(lines, newFromRev).filter { it.seq <= newToRev }
        val firstNew = fresh.firstOrNull()?.id
        val newestMine = lines.lastOrNull { it.mine && it.kind == ChatKind.USER }?.id
        var said: Instant? = null
        var last: ChatLine? = null
        for (line in lines) {
            val at = line.at
            var broke = false
            var stamped = false
            val prev = last
            val sameAuthor = prev != null && prev.authorSeatId == line.authorSeatId && prev.mine == line.mine
            // The bot's news and the team's posts all speak as the bot, so they run together as one author.
            val sameVoice = prev != null && (prev.kind == ChatKind.USER) == (line.kind == ChatKind.USER)
            val newRun = !sameAuthor || !sameVoice
            if (at != null && (said == null || newRun || Duration.between(said, at) >= GAP)) {
                rows += ChatRow.Time(at, timeLabel(at, today, zone, hour24, locale), line.id)
                broke = true
                stamped = true
            }
            if (at != null) said = at
            if (line.id == firstNew) {
                rows += ChatRow.New(fresh.size)
                broke = true
            }
            val joined = !broke && sameAuthor && sameVoice
            if (joined) {
                val end = rows.lastIndex
                rows[end] = (rows[end] as ChatRow.Line).copy(last = false)
            }
            rows += ChatRow.Line(line, first = !joined, stamped = stamped, newestMine = line.id == newestMine)
            last = line
        }
        return rows
    }

    /**
     * A time line's words, Messenger's way, in capitals: "2:20 PM" today, "TUE AT 3:45 AM" within
     * the last six days, "NOV 15 AT 8:55 PM" earlier this year, "NOV 15, 2025 AT 8:55 PM" before.
     * [hour24] follows the phone's own clock setting.
     */
    fun timeLabel(
        at: Instant,
        today: LocalDate,
        zone: ZoneId = ZoneId.systemDefault(),
        hour24: Boolean = false,
        locale: Locale = Locale.getDefault(),
    ): String {
        val time = at.atZone(zone)
        val date = time.toLocalDate()
        val clock = clockTime(at, hour24, zone).uppercase(locale)
        val days = ChronoUnit.DAYS.between(date, today)
        val day =
            when {
                days == 0L -> return clock
                days in 1L..6L -> "EEE"
                date.year == today.year -> "MMM d"
                else -> "MMM d, yyyy"
            }
        return time.format(DateTimeFormatter.ofPattern(day, locale)).uppercase(locale) + " AT " + clock
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

    /** "8:15 PM", or "20:15" when [hour24], in the phone's own time; blank without a time. */
    fun clock(
        at: Instant?,
        zone: ZoneId = ZoneId.systemDefault(),
        hour24: Boolean = false,
    ): String = at?.let { clockTime(it, hour24, zone) }.orEmpty()

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
