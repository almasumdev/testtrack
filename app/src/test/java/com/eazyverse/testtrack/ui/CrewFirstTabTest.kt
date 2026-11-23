package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.CrewList
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Crews opens on Open, and slides to Run only when tonight has an app owed in a running crew. */
class CrewFirstTabTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun seat(
        id: String,
        mine: Boolean = false,
        extra: String = "",
    ) = "{\"id\":\"$id\",\"crew_id\":\"c\",\"app_name\":\"App $id\",\"package_name\":\"com.example.$id\"," +
        "\"owner_name\":\"Owner $id\",\"state\":\"confirmed\",\"mine\":$mine," +
        "\"created_at\":\"2026-11-01T00:00:00+00:00\"$extra}"

    private fun crew(
        id: String,
        state: String,
        day: Int = 0,
        extra: String = "",
        members: List<String> = emptyList(),
    ) = "{\"id\":\"$id\",\"number\":1,\"name\":\"Crew $id\",\"state\":\"$state\",\"filled\":15,\"taken\":15," +
        "\"seats\":15,\"day\":$day,\"created_at\":\"2026-11-01T00:00:00+00:00\",\"members\":[${members.joinToString(",")}]$extra}"

    private fun slides(list: String) = slidesToRun(crewsOf(json.decodeFromString<CrewList>(list)))

    @Test
    fun `no crews stays on Open`() {
        assertFalse(slides("{}"))
    }

    @Test
    fun `a running crew with an app owed tonight slides to Run`() {
        val running = crew("c", "full", day = 3, members = listOf(seat("me", mine = true), seat("a")))
        assertTrue(slides("{\"mine\":[${crew("a", "done", day = 14)},${crew("b", "filling")},$running]}"))
    }

    @Test
    fun `a running crew with tonight done stays on Open`() {
        val done = crew("c", "full", day = 3, members = listOf(seat("me", mine = true), seat("a", extra = ",\"opened_today\":true")))
        assertFalse(slides("{\"mine\":[$done]}"))
    }

    @Test
    fun `a crew filling or finished stays on Open`() {
        assertFalse(slides("{\"mine\":[${crew("a", "done", day = 14)},${crew("b", "filling")}]}"))
        assertFalse(slides("{\"mine\":[${crew("a", "done", day = 14)}]}"))
    }

    @Test
    fun `a volunteer crew with an app owed tonight slides to Run`() {
        val spot = ",\"volunteer\":{\"id\":\"s1\",\"state\":\"active\",\"first_night\":2}"
        val going = crew("v", "full", day = 3, extra = spot, members = listOf(seat("a")))
        assertTrue(slides("{\"mine\":[${crew("a", "done", day = 14)}],\"volunteering\":[$going]}"))
    }

    @Test
    fun `a volunteer before their first night owes nothing yet and stays on Open`() {
        val spot = ",\"first_night\":5,\"volunteer\":{\"id\":\"s1\",\"state\":\"active\",\"first_night\":5}"
        val waiting = crew("v", "full", day = 2, extra = spot, members = listOf(seat("a")))
        assertFalse(slides("{\"volunteering\":[$waiting]}"))
    }

    @Test
    fun `a volunteer spot the reader left does not slide to Run`() {
        val left = crew("v", "full", day = 6, extra = ",\"volunteer\":{\"id\":\"s1\",\"state\":\"left\"}", members = listOf(seat("a")))
        assertFalse(slides("{\"volunteering\":[$left]}"))
    }
}
