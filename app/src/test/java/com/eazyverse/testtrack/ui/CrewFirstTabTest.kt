package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.CrewList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which tab Crew opens on, from an answer or the kept one: whichever has something owed. */
class CrewFirstTabTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun crew(
        id: String,
        state: String,
        day: Int = 0,
        extra: String = "",
    ) = "{\"id\":\"$id\",\"number\":1,\"name\":\"Crew $id\",\"state\":\"$state\",\"filled\":15,\"taken\":15," +
        "\"seats\":15,\"day\":$day,\"created_at\":\"2026-11-01T00:00:00+00:00\",\"members\":[]$extra}"

    private fun tabOf(list: String) = firstTabOf(crewsOf(json.decodeFromString<CrewList>(list)))

    @Test
    fun `no crews opens on Open`() {
        assertEquals(CrewPhase.OPEN, tabOf("{}"))
    }

    @Test
    fun `a running crew wins over the rest`() {
        val list = "{\"mine\":[${crew("a", "done", day = 14)},${crew("b", "filling")},${crew("c", "full", day = 3)}]}"
        assertEquals(CrewPhase.RUN, tabOf(list))
    }

    @Test
    fun `a crew filling opens on Lobby, a finished one alone on Record`() {
        assertEquals(CrewPhase.LOBBY, tabOf("{\"mine\":[${crew("a", "done", day = 14)},${crew("b", "filling")}]}"))
        assertEquals(CrewPhase.RECORD, tabOf("{\"mine\":[${crew("a", "done", day = 14)}]}"))
    }

    @Test
    fun `a volunteer crew counts as running, even before its first night`() {
        val spot = ",\"first_night\":5,\"volunteer\":{\"id\":\"s1\",\"state\":\"active\",\"first_night\":5}"
        val waiting = crew("v", "full", day = 2, extra = spot)
        assertEquals(CrewPhase.RUN, tabOf("{\"mine\":[${crew("a", "done", day = 14)}],\"volunteering\":[$waiting]}"))
    }

    @Test
    fun `a volunteer spot the reader left does not pull the tab to Run`() {
        val left = crew("v", "full", day = 6, extra = ",\"volunteer\":{\"id\":\"s1\",\"state\":\"left\"}")
        assertEquals(CrewPhase.RECORD, tabOf("{\"mine\":[${crew("a", "done", day = 14)}],\"volunteering\":[$left]}"))
    }
}
