package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiSeat
import com.eazyverse.testtrack.net.PictureIn
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fields added on 30 October read from today's server, which does not send them yet, and
 * from the new one, which does. Every one has a default that keeps the app as it was.
 */
class CrewWireDefaultsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private val seat =
        """{"id":"s1","crew_id":"c1","app_name":"Steps","package_name":"com.example.steps","owner_name":"Kenji",
        |"state":"removed","mine":true,"created_at":"2026-10-01T00:00:00+00:00"}
        """.trimMargin()

    @Test
    fun `a seat from an older server may still be asked back`() {
        val read = json.decodeFromString<ApiSeat>(seat).toSeat()
        assertTrue(read.canAppeal)
        assertEquals("", read.appealBlocked)
    }

    @Test
    fun `a seat the server will not take an appeal for says why`() {
        val read =
            json
                .decodeFromString<ApiSeat>(seat.replace("}", ""","can_appeal":false,"appeal_blocked":"This app is in Crew 07 now"}"""))
                .toSeat()
        assertFalse(read.canAppeal)
        assertEquals("This app is in Crew 07 now", read.appealBlocked)
    }

    @Test
    fun `a crew without its length is the usual fortnight`() {
        val crew =
            json.decodeFromString<ApiCrew>(
                """{"id":"c1","number":7,"name":"Crew 07","state":"running","filled":15,"taken":15,"seats":15,
                |"created_at":"2026-10-01T00:00:00+00:00"}
                """.trimMargin(),
            )
        assertEquals(14, crew.runDays)
    }

    @Test
    fun `a picture is sent with its night, and without one when it is not known`() {
        val wire = Json { encodeDefaults = false }
        assertTrue(wire.encodeToString(PictureIn.serializer(), PictureIn("drive:x", "t", day = 5)).contains("\"day\":5"))
        assertFalse(wire.encodeToString(PictureIn.serializer(), PictureIn("drive:x", "t")).contains("day"))
    }
}
