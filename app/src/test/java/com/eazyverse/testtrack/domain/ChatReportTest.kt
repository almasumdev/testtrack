package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The report reasons the server fixes, and the note that goes with "Something else". */
class ChatReportTest {
    @Test
    fun `the codes are the server's`() {
        assertEquals(
            listOf("spam", "abuse", "sexual", "private", "scam", "other"),
            ChatReportReason.entries.map { it.code },
        )
    }

    @Test
    fun `a reason is needed, and the other reasons need no note`() {
        assertFalse(ChatReport.ready(null, ""))
        assertTrue(ChatReport.ready(ChatReportReason.SPAM, ""))
        assertTrue(ChatReport.ready(ChatReportReason.ABUSE, "  rude to everyone  "))
    }

    @Test
    fun `something else needs 10 to 280 characters after trimming`() {
        assertFalse(ChatReport.ready(ChatReportReason.OTHER, ""))
        assertFalse(ChatReport.ready(ChatReportReason.OTHER, "   too short  "))
        assertTrue(ChatReport.ready(ChatReportReason.OTHER, "x".repeat(10)))
        assertTrue(ChatReport.ready(ChatReportReason.OTHER, " " + "x".repeat(280) + " "))
        assertFalse(ChatReport.ready(ChatReportReason.OTHER, "x".repeat(281)))
    }

    @Test
    fun `no reason takes a note over 280`() {
        assertFalse(ChatReport.ready(ChatReportReason.SCAM, "x".repeat(281)))
        assertEquals("hello", ChatReport.note("  hello \n"))
    }
}
