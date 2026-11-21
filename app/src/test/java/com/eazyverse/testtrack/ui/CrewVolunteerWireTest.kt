package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiChatPerson
import com.eazyverse.testtrack.net.ApiCrewPictures
import com.eazyverse.testtrack.net.ApiVolunteerSpots
import com.eazyverse.testtrack.net.CrewList
import com.eazyverse.testtrack.viewmodel.SpotState
import com.eazyverse.testtrack.viewmodel.toSpot
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Volunteers, read from a server that does not know them and from one that does. Every new field
 * has a default, so the older answer reads exactly as before.
 */
class CrewVolunteerWireTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun seat(
        id: String,
        state: String = "confirmed",
        opened: Boolean = false,
        firstNight: Int = 1,
    ) = "{\"id\":\"$id\",\"crew_id\":\"c1\",\"app_name\":\"App $id\",\"package_name\":\"com.example.$id\"," +
        "\"owner_name\":\"Owner $id\",\"state\":\"$state\",\"mine\":false,\"opened_today\":$opened," +
        "\"first_night\":$firstNight,\"created_at\":\"2026-11-01T00:00:00+00:00\"}"

    private fun crew(
        day: Int,
        extra: String = "",
    ) = "{\"id\":\"c1\",\"number\":58,\"name\":\"Crew 58\",\"state\":\"full\",\"filled\":15,\"taken\":15," +
        "\"seats\":15,\"day\":$day,\"created_at\":\"2026-11-01T00:00:00+00:00\"," +
        "\"members\":[${seat("a", opened = true)},${seat("b")},${seat("c", firstNight = 9)},${seat("d", state = "removed")}]$extra}"

    /** The one crew a list holds as volunteering, read as the volunteer sees it. */
    private fun volunteering(extra: String) =
        json
            .decodeFromString<CrewList>("{\"volunteering\":[${crew(6, extra)}]}")
            .volunteering
            .single()
            .toVolunteerCrew()

    @Test
    fun `a crew list from an older server has no volunteering and no volunteers`() {
        val list = json.decodeFromString<CrewList>("""{"mine":[${crew(6)}]}""")
        assertTrue(list.volunteering.isEmpty())
        val read = list.mine.single().toCrew()
        assertTrue(read.volunteers.isEmpty())
        assertNull(read.volunteer)
        assertEquals(CrewRole.MEMBER, read.role)
    }

    @Test
    fun `a volunteer's crew owes the apps whose run has reached their night`() {
        val read =
            volunteering(
                ",\"volunteer\":{\"id\":\"v1\",\"state\":\"active\",\"day\":6,\"first_night\":5," +
                    "\"nights_paid\":1,\"tix_earned\":5}",
            )
        assertEquals(CrewRole.VOLUNTEER, read.role)
        assertEquals(CrewPhase.RUN, read.phase)
        assertNull(read.mine)
        assertFalse(read.out)
        // a and b are owed; c starts on night 9 and d is out of the run.
        assertEquals(listOf("a", "b"), read.apps.map { it.id })
        assertEquals(1, read.owed)
        assertEquals(5, read.volunteer?.tixEarned)
        assertEquals(5, read.firstNight)
    }

    @Test
    fun `before the first night a volunteer's crew is a lobby with nothing owed`() {
        val read = volunteering(",\"first_night\":7,\"starts_in_ms\":60000,\"volunteer\":{\"id\":\"v1\",\"first_night\":7}")
        assertEquals(CrewPhase.LOBBY, read.phase)
        assertTrue(read.apps.isEmpty())
        assertEquals(7, read.firstNight)
        assertEquals(60000L, read.startsIn)
    }

    @Test
    fun `a volunteer taken out has nothing to open and is out`() {
        val read =
            volunteering(",\"volunteer\":{\"id\":\"v1\",\"state\":\"out\",\"end_reason\":\"Missed night 5.\",\"last_night\":4}")
        assertTrue(read.out)
        assertTrue(read.apps.isEmpty())
        assertEquals("You're out", read.standing)
        assertEquals("Missed night 5.", read.volunteer?.endReason)
        assertEquals(4, read.volunteer?.lastNight)
    }

    @Test
    fun `active volunteers count among the testers of a member's app`() {
        val rows =
            ",\"volunteers\":[{\"id\":\"v1\",\"name\":\"Rafi\",\"state\":\"active\",\"first_night\":7," +
                "\"opened_mine_today\":true},{\"id\":\"v2\",\"name\":\"Tania\",\"state\":\"out\"}]"
        val read =
            json
                .decodeFromString<CrewList>("{\"mine\":[${crew(6, rows)}]}")
                .mine
                .single()
                .toCrew()
        assertEquals(2, read.volunteers.size)
        assertTrue(read.volunteers.all { it.volunteer })
        assertEquals(7, read.volunteers.first().firstNight)
        // Three members still in (d is out), plus Rafi; Tania is out.
        assertEquals(4, read.testers.size)
        assertEquals(1, read.openedMineTonight)
    }

    @Test
    fun `pictures read with and without volunteers`() {
        val old = json.decodeFromString<ApiCrewPictures>("""{"seat_id":"s1","app_name":"Budget","testers":[{"seat_id":"a","name":"A"}]}""")
        assertTrue(old.volunteers.isEmpty())
        assertEquals("a", old.testers.single().seatId)
        val new =
            json.decodeFromString<ApiCrewPictures>(
                "{\"seat_id\":\"s1\",\"app_name\":\"Budget\",\"testers\":[],\"volunteers\":[{\"volunteer_id\":\"v1\"," +
                    "\"name\":\"Rafi\",\"first_night\":7,\"out\":true,\"out_day\":8}]}",
            )
        val v = new.volunteers.single()
        assertEquals("v1", v.volunteerId)
        assertEquals("", v.seatId)
        assertEquals(8, v.outDay)
    }

    @Test
    fun `a chat person is a member unless the server says volunteer`() {
        assertFalse(json.decodeFromString<ApiChatPerson>("""{"seat_id":"s1","owner_name":"A"}""").volunteer)
        assertTrue(json.decodeFromString<ApiChatPerson>("""{"seat_id":"v1","owner_name":"Rafi","volunteer":true}""").volunteer)
    }

    @Test
    fun `spots read, and work out the first night and tix when the server leaves them out`() {
        val got =
            json.decodeFromString<ApiVolunteerSpots>(
                "{\"spots\":[{\"crew_id\":\"c58\",\"number\":58,\"name\":\"Crew 58\",\"day\":6,\"run_days\":14,\"free\":2," +
                    "\"icons\":[\"x\"]},{\"crew_id\":\"c55\",\"number\":55,\"day\":3,\"state\":\"full\"}," +
                    "{\"crew_id\":\"c49\",\"state\":\"out\"}],\"can_join\":true}",
            )
        val spots = got.spots.map { it.toSpot() }
        assertEquals(7, spots[0].firstNight)
        assertEquals(40, spots[0].tix)
        assertEquals(SpotState.OPEN, spots[0].state)
        assertEquals("Crew 55", spots[1].name)
        assertEquals(SpotState.FULL, spots[1].state)
        assertEquals(SpotState.OUT, spots[2].state)
        assertTrue(json.decodeFromString<ApiVolunteerSpots>("{}").canJoin)
    }
}
