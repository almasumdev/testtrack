package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiSeat
import com.eazyverse.testtrack.net.ApiSeatReport
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** What an owner sees of their reports: the wire's notes (reasons only) and the old server's bare reasons. */
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
                notes = listOf(ApiSeatReport("b"), ApiSeatReport("a")),
            ).toSeat()
        assertEquals(listOf(SeatReport("b"), SeatReport("a")), seat.reportNotes)
        assertEquals(seat.reportNotes, seat.reportList)
    }

    @Test
    fun reportList_falls_back_to_reasons_newest_first() {
        val seat = apiSeat(reasons = listOf("old", "new")).toSeat()
        assertEquals(listOf(SeatReport("new"), SeatReport("old")), seat.reportList)
    }

    @Test
    fun a_note_with_a_night_and_day_still_reads_but_only_the_reason_is_kept() {
        val json = Json { ignoreUnknownKeys = true }
        val note = json.decodeFromString<ApiSeatReport>("""{"reason":"x","night":3,"on":"2026-11-21"}""")
        assertEquals(ApiSeatReport("x"), note)
    }
}
