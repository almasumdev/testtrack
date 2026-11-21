package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiSeat
import com.eazyverse.testtrack.net.ApiSeatReport
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an owner sees of their reports: the wire's notes, the old server's bare reasons, and the date labels. */
class SeatReportsTest {
    private fun apiSeat(
        reasons: List<String> = emptyList(),
        notes: List<ApiSeatReport> = emptyList(),
    ) = ApiSeat(
        id = "s1",
        crewId = "c1",
        appName = "App",
        packageName = "com.example.app",
        ownerName = "Rafiul",
        state = "confirmed",
        mine = true,
        reasons = reasons,
        reportNotes = notes,
        createdAt = "2026-09-20T00:00:00+00:00",
    )

    @Test
    fun toSeat_maps_report_notes() {
        val seat =
            apiSeat(
                reasons = listOf("a", "b"),
                notes = listOf(ApiSeatReport("b", 3, "2026-11-21"), ApiSeatReport("a", 0, "2026-11-18")),
            ).toSeat()
        assertEquals(listOf(SeatReport("b", 3, "2026-11-21"), SeatReport("a", 0, "2026-11-18")), seat.reportNotes)
        assertEquals(seat.reportNotes, seat.reportList)
    }

    @Test
    fun reportList_falls_back_to_reasons_newest_first() {
        val seat = apiSeat(reasons = listOf("old", "new")).toSeat()
        assertEquals(listOf(SeatReport("new"), SeatReport("old")), seat.reportList)
    }

    @Test
    fun reportWhen_says_night_and_day() {
        assertEquals("Night 3, Nov 21", reportWhen(SeatReport("x", 3, "2026-11-21")))
        assertEquals("Before the run, Nov 18", reportWhen(SeatReport("x", 0, "2026-11-18")))
        assertEquals("Nov 2", reportWhen(SeatReport("x", null, "2026-11-02")))
    }

    @Test
    fun reportWhen_date_only_and_missing_date() {
        assertEquals("Nov 18", reportWhen(SeatReport("x", 0, "2026-11-18"), dateOnly = true))
        assertEquals("Night 3", reportWhen(SeatReport("x", 3, "soon")))
        assertEquals("", reportWhen(SeatReport("x")))
    }
}
