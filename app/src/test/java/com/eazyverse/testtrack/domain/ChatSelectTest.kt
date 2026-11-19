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
        // With the chat closed to the reader it has nothing left to offer.
        assertFalse(ChatSelect.canPick(picture, writable = false))
        assertTrue(picks("p", writable = false).ids.isEmpty())
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
}
