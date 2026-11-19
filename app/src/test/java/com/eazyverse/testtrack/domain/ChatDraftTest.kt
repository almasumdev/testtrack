package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Mentions in a draft, and the thread's merge and layout. */
class ChatDraftTest {
    private val amir = "11111111-2222-3333-4444-555555555555"

    @Test
    fun `a picked mention is sent as its token`() {
        val (draft, cursor) = ChatDraft("Can @am see").mention(4, "Amir Haddad", app = false, seatId = amir)
        assertEquals("Can @Amir Haddad  see", draft.text)
        assertEquals(17, cursor)
        assertEquals("Can @[seat:$amir]  see", draft.toWire())
        assertEquals("Can    see", draft.forLinkCheck())
    }

    @Test
    fun `a mention picked after a longer typed name replaces all of it`() {
        val (draft, cursor) = ChatDraft("Hi @Amir Ha now").mention(3, "Amir Haddad", app = false, seatId = amir, until = 11)
        assertEquals("Hi @Amir Haddad  now", draft.text)
        assertEquals(16, cursor)
        assertEquals(MentionSpan(3, 15, false, amir), draft.spans.single())
    }

    @Test
    fun `editing inside a mention makes it plain words`() {
        val (draft, _) = ChatDraft("@").mention(0, "Amir", app = false, seatId = amir)
        val cut = draft.edit("@Amr ")
        assertTrue(cut.spans.isEmpty())
        assertEquals("@Amr", cut.toWire())
    }

    @Test
    fun `typing after a mention moves nothing, typing before shifts it`() {
        val (draft, _) = ChatDraft("@").mention(0, "Amir", app = true, seatId = amir)
        assertEquals("@[app:$amir] look", draft.edit("@Amir look").toWire())
        assertEquals("Hi @[app:$amir]", draft.edit("Hi @Amir ").toWire())
    }

    @Test
    fun `tokens split into words and mentions`() {
        val text = "Same @[seat:$amir] and @[app:$amir]!"
        val pieces = ChatText.pieces(text)
        assertEquals(5, pieces.size)
        assertEquals(ChatPiece.Mention(false, amir), pieces[1])
        assertEquals(ChatPiece.Mention(true, amir), pieces[3])
        assertEquals("Same @A and @A!", ChatText.plain(text) { _, _ -> "A" })
    }

    private fun line(
        id: String,
        seq: Long,
        author: String = "a",
        at: String = "2026-11-19T10:00:00Z",
        send: SendState = SendState.SENT,
        kind: ChatKind = ChatKind.USER,
    ) = ChatLine(id, seq = seq, rev = seq, authorSeatId = author, at = Instant.parse(at), send = send, kind = kind)

    @Test
    fun `a merge replaces by id, orders by seq and keeps unsent lines last`() {
        val held = listOf(line("1", 1), line("2", 2), line("p", 0, send = SendState.SENDING))
        val merged = ChatThread.merge(held, listOf(line("3", 3), line("2", 2).copy(deleted = true)))
        assertEquals(listOf("1", "2", "3", "p"), merged.map { it.id })
        assertTrue(merged[1].deleted)
        assertEquals(listOf("4", "p"), ChatThread.merge(held, listOf(line("4", 4)), reset = true).map { it.id })
        // Once the server has it, the sending copy gives way to the server's.
        val sent = ChatThread.merge(held, listOf(line("p", 9)))
        assertEquals(listOf("1", "2", "p"), sent.map { it.id })
        assertEquals(SendState.SENT, sent.last().send)
    }

    @Test
    fun `rows carry time lines, the new line and author runs`() {
        val lines =
            listOf(
                line("1", 1, "a", "2026-11-18T10:00:00Z"),
                line("2", 2, "a", "2026-11-19T10:00:00Z"),
                line("3", 3, "a", "2026-11-19T10:01:00Z"),
                line("4", 4, "b", "2026-11-19T10:02:00Z"),
                line("5", 5, "b", "2026-11-19T10:03:00Z", kind = ChatKind.BOT),
            )
        val rows = ChatThread.rows(lines, newFromRev = 3, zone = ZoneOffset.UTC, today = LocalDate.parse("2026-11-19"))
        val keys = rows.map { if (it is ChatRow.Line) "${it.line.id}${if (it.first) "*" else ""}" else it.key }
        assertEquals(listOf("time-1", "1*", "time-2", "2*", "3", "time-4", "new", "4*", "time-5", "5*"), keys)
        assertEquals(1, (rows[6] as ChatRow.New).count)
    }

    @Test
    fun `the new line counts only what was unread as the chat opened`() {
        val lines = (1L..5L).map { line("$it", it, "b", "2026-11-19T10:0$it:00Z") }
        val rows = ChatThread.rows(lines, newFromRev = 2, newToRev = 4, zone = ZoneOffset.UTC, today = LocalDate.parse("2026-11-19"))
        val new = rows.filterIsInstance<ChatRow.New>().single()
        assertEquals(2, new.count)
        assertEquals("3", (rows[rows.indexOf(new) + 1] as ChatRow.Line).line.id)
        assertTrue(ChatThread.rows(lines, newFromRev = 5, zone = ZoneOffset.UTC).none { it is ChatRow.New })
    }

    @Test
    fun `a picture says how long it has left`() {
        val now = Instant.parse("2026-11-19T10:00:00Z")
        assertEquals("Gone in 21 h", ChatThread.pictureLeft(now.plusSeconds(21 * 3600 + 120), now))
        assertEquals("Gone in 5 min", ChatThread.pictureLeft(now.plusSeconds(300), now))
        assertEquals("", ChatThread.pictureLeft(now.minusSeconds(1), now))
    }
}
