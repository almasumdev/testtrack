package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Picking messages the WhatsApp way: what can be picked, and what the bar then offers. */
class ChatSelectTest {
    private val theirs = ChatLine(id = "t", authorSeatId = "kenji", text = "Did yours open?")
    private val mine = ChatLine(id = "m", authorSeatId = "me", mine = true, text = "Yes, fine.")
    private val sending = mine.copy(id = "s", send = SendState.SENDING)
    private val failed = mine.copy(id = "f", send = SendState.FAILED)
    private val gone = theirs.copy(id = "g", deleted = true, text = "")
    private val picture = theirs.copy(id = "p", text = "", picture = ChatPicture("pic"))
    private val thread = listOf(theirs, mine, sending, failed, gone, picture)

    private fun picks(
        vararg ids: String,
        writable: Boolean = true,
    ) = ChatSelect.picks(ids.toCollection(LinkedHashSet()), thread, writable)

    @Test
    fun `a deleted message or one still sending has nothing to pick`() {
        assertFalse(ChatSelect.canPick(gone, writable = true))
        assertFalse(ChatSelect.canPick(sending, writable = true))
        assertTrue(ChatSelect.canPick(theirs, writable = true))
    }

    @Test
    fun `a picture without words can be answered but not copied`() {
        val one = picks("p")
        assertTrue(one.reply)
        assertFalse(one.copy)
        assertFalse(one.delete)
        // With the chat closed to the reader only Report is left to offer.
        assertTrue(ChatSelect.canPick(picture, writable = false))
        assertFalse(picks("p", writable = false).reply)
        assertTrue(picks("p", writable = false).report)
        // Their own words are all there is to offer once the thing said is theirs and deleted.
        assertFalse(ChatSelect.canPick(picture.copy(mine = true, deleted = true), writable = false))
    }

    @Test
    fun `someone else's message offers reply and copy, never delete`() {
        val one = picks("t")
        assertTrue(one.reply)
        assertTrue(one.copy)
        assertFalse(one.delete)
    }

    @Test
    fun `your own sent message offers reply, copy and delete with a confirm`() {
        val one = picks("m")
        assertTrue(one.reply && one.copy && one.delete)
        assertTrue(one.ask)
        assertFalse(one.removeOnly)
    }

    @Test
    fun `your message that never sent is removed without asking, and can't be answered`() {
        val one = picks("f")
        assertFalse(one.reply)
        assertTrue(one.copy)
        assertTrue(one.delete)
        assertTrue(one.removeOnly)
        assertFalse(one.ask)
    }

    @Test
    fun `reply hides once more than one is picked`() {
        val two = picks("t", "m")
        assertEquals(2, two.count)
        assertFalse(two.reply)
        assertTrue(two.copy)
    }

    @Test
    fun `delete needs every picked message to be yours`() {
        assertFalse(picks("t", "m").delete)
        val own = picks("m", "f")
        assertTrue(own.delete)
        assertTrue(own.ask)
        assertFalse(own.removeOnly)
    }

    @Test
    fun `a pick that is no longer there drops out of the bar`() {
        val dropped = ChatSelect.picks(setOf("m", "nope", "g"), thread, writable = true)
        assertEquals(setOf("m"), dropped.ids)
        assertTrue(ChatSelect.picks(setOf("g"), thread, writable = true).ids.isEmpty())
        assertFalse(ChatSelect.picks(emptySet(), thread, writable = true).active)
    }

    @Test
    fun `a tap picks or unpicks`() {
        val one = ChatSelect.toggle(emptySet(), "t")
        val two = ChatSelect.toggle(one, "m")
        assertEquals(listOf("t", "m"), two.toList())
        assertEquals(setOf("m"), ChatSelect.toggle(two, "t"))
        assertTrue(ChatSelect.toggle(setOf("m"), "m").isEmpty())
    }

    @Test
    fun `copied words come in thread order, skipping a picture with none`() {
        val picked = ChatSelect.lines(setOf("m", "p", "t"), thread, writable = true)
        assertEquals(listOf("t", "m", "p"), picked.map { it.id })
        assertEquals("Did yours open?\nYes, fine.", ChatSelect.copyText(picked) { it })
    }

    @Test
    fun `only someone else's sent member message can be reported`() {
        assertTrue(ChatSelect.canReport(theirs))
        assertFalse(ChatSelect.canReport(mine))
        assertFalse(ChatSelect.canReport(gone))
        assertFalse(ChatSelect.canReport(theirs.copy(send = SendState.SENDING)))
        assertFalse(ChatSelect.canReport(theirs.copy(kind = ChatKind.BOT)))
        assertFalse(ChatSelect.canReport(theirs.copy(kind = ChatKind.TEAM)))
        assertFalse(ChatSelect.canReport(theirs.copy(blocked = true)))
        // A held stub is still someone's message the team may want to hear about.
        assertTrue(ChatSelect.canReport(theirs.copy(held = true)))
    }

    @Test
    fun `report is offered for one message, and a closed chat can still report`() {
        assertTrue(picks("t").report)
        assertFalse(picks("m").report)
        assertFalse(picks("t", "p").report)
        val closed = picks("t", writable = false)
        assertTrue(closed.report)
        assertFalse(closed.reply)
        assertEquals(setOf("t"), closed.ids)
    }

    @Test
    fun `a blocked or held stub offers no reply and no copy`() {
        val blocked = theirs.copy(id = "b", text = "", blocked = true)
        val held = theirs.copy(id = "h", text = "", held = true)
        assertFalse(ChatSelect.canReply(blocked, writable = true))
        assertFalse(ChatSelect.canReply(held, writable = true))
        assertFalse(ChatSelect.canPick(blocked, writable = true))
        assertTrue(ChatSelect.canPick(held, writable = true))
    }
}
