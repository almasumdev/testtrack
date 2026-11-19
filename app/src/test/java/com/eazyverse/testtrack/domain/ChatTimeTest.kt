package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

/** The chat's time lines: when one shows, what it says, and how it splits a run. */
class ChatTimeTest {
    private val today = LocalDate.parse("2026-11-19")

    private fun label(
        at: String,
        hour24: Boolean = false,
    ) = ChatThread.timeLabel(Instant.parse(at), today, ZoneOffset.UTC, hour24, Locale.US)

    private fun line(
        id: String,
        at: String,
        author: String? = "a",
        mine: Boolean = false,
        send: SendState = SendState.SENT,
        kind: ChatKind = ChatKind.USER,
    ) = ChatLine(id, seq = id.hashCode().toLong(), authorSeatId = author, mine = mine, at = Instant.parse(at), send = send, kind = kind)

    private fun rows(lines: List<ChatLine>) = ChatThread.rows(lines, null, zone = ZoneOffset.UTC, today = today, locale = Locale.US)

    private fun keys(rows: List<ChatRow>) = rows.map { if (it is ChatRow.Line) "${it.line.id}${if (it.first) "*" else ""}" else it.key }

    @Test
    fun `today shows the time only`() {
        assertEquals("2:20 PM", label("2026-11-19T14:20:00Z"))
        assertEquals("12:06 AM", label("2026-11-19T00:06:00Z"))
    }

    @Test
    fun `the last six days show the weekday`() {
        assertEquals("TUE AT 3:45 AM", label("2026-11-17T03:45:00Z"))
        assertEquals("WED AT 11:59 PM", label("2026-11-18T23:59:00Z"))
        assertEquals("FRI AT 9:00 AM", label("2026-11-13T09:00:00Z"))
    }

    @Test
    fun `a week or more back this year shows the date`() {
        assertEquals("NOV 12 AT 8:55 PM", label("2026-11-12T20:55:00Z"))
        assertEquals("JAN 2 AT 7:05 AM", label("2026-01-02T07:05:00Z"))
    }

    @Test
    fun `another year shows the year`() {
        assertEquals("NOV 15, 2025 AT 8:55 PM", label("2025-11-15T20:55:00Z"))
    }

    @Test
    fun `a 24 hour phone gets a 24 hour clock`() {
        assertEquals("14:20", label("2026-11-19T14:20:00Z", hour24 = true))
        assertEquals("TUE AT 03:45", label("2026-11-17T03:45:00Z", hour24 = true))
    }

    @Test
    fun `a time line heads the first message, every new author run and every pause of 15 minutes or more`() {
        val lines =
            listOf(
                line("1", "2026-11-19T10:00:00Z"),
                line("2", "2026-11-19T10:14:59Z"),
                line("3", "2026-11-19T10:29:59Z"),
                line("4", "2026-11-19T10:31:00Z", author = "b"),
                line("5", "2026-11-19T11:00:00Z", kind = ChatKind.BOT, author = "bot"),
                line("6", "2026-11-19T11:10:00Z", author = "b"),
            )
        assertEquals(
            listOf("time-1", "1*", "2", "time-3", "3*", "time-4", "4*", "time-5", "5*", "time-6", "6*"),
            keys(rows(lines)),
        )
    }

    @Test
    fun `a time line heads each author run, none inside one`() {
        val lines =
            listOf(
                line("1", "2026-11-19T10:00:00Z", author = "a"),
                line("2", "2026-11-19T10:00:30Z", author = "a"),
                line("3", "2026-11-19T10:01:00Z", author = "b"),
                line("4", "2026-11-19T10:02:00Z", author = "a"),
            )
        assertEquals(listOf("time-1", "1*", "2", "time-3", "3*", "time-4", "4*"), keys(rows(lines)))
        val laid = rows(lines).filterIsInstance<ChatRow.Line>()
        assertEquals(listOf(true, false, true, true), laid.map { it.stamped })
    }

    @Test
    fun `a time line splits one author's run`() {
        val rows = rows(listOf(line("1", "2026-11-19T10:00:00Z"), line("2", "2026-11-19T10:20:00Z")))
        val lines = rows.filterIsInstance<ChatRow.Line>()
        assertTrue(lines.all { it.first && it.last && it.stamped })
        assertEquals("10:20 AM", (rows[2] as ChatRow.Time).label)
    }

    @Test
    fun `only messages under a time line are stamped`() {
        val rows = rows(listOf(line("1", "2026-11-19T10:00:00Z"), line("2", "2026-11-19T10:05:00Z")))
        val (one, two) = rows.filterIsInstance<ChatRow.Line>()
        assertTrue(one.stamped)
        assertFalse(two.stamped)
    }

    @Test
    fun `only your newest message is marked for its status`() {
        val lines =
            listOf(
                line("1", "2026-11-19T10:00:00Z", mine = true),
                line("2", "2026-11-19T10:01:00Z", mine = true),
                line("3", "2026-11-19T10:02:00Z", author = "b"),
            )
        val marked = rows(lines).filterIsInstance<ChatRow.Line>().filter { it.newestMine }.map { it.line.id }
        assertEquals(listOf("2"), marked)
    }

    @Test
    fun `the bot and the team run together as one author, never with a member`() {
        val lines =
            listOf(
                line("1", "2026-11-19T10:00:00Z", kind = ChatKind.BOT, author = null),
                line("2", "2026-11-19T10:01:00Z", kind = ChatKind.TEAM, author = null),
                line("3", "2026-11-19T10:02:00Z"),
                line("4", "2026-11-19T10:03:00Z", kind = ChatKind.TEAM, author = null),
                line("5", "2026-11-19T10:04:00Z", kind = ChatKind.TEAM, author = null),
            )
        assertEquals(listOf("time-1", "1*", "2", "time-3", "3*", "time-4", "4*", "5"), keys(rows(lines)))
        val laid = rows(lines).filterIsInstance<ChatRow.Line>()
        assertEquals(listOf(false, true, true, false, true), laid.map { it.last })
    }
}
