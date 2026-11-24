package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.data.RoundStore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class NightRoundWordsTest {
    private val zone = ZoneId.of("Asia/Dhaka")

    private fun at(
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = LocalDateTime
        .of(2026, 11, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun same_local_date_is_the_same_night() {
        assertFalse(isNewNight(at(22, 21), at(22, 23, 59), zone))
    }

    @Test
    fun past_local_midnight_is_a_new_night() {
        assertTrue(isNewNight(at(22, 23, 50), at(23, 0, 5), zone))
    }

    @Test
    fun a_round_with_no_start_is_never_a_new_night() {
        assertFalse(isNewNight(0L, at(23, 12), zone))
    }

    @Test
    fun nothing_is_said_when_every_app_counted_with_its_picture() {
        val stop = RoundStore.Stop("s1", "com.a", "A")
        assertNull(lastSnack(RoundStore.Round(listOf(stop), 0, 5L)))
    }

    @Test
    fun last_word_names_missing_pictures_instead_of_done() {
        assertEquals("Counted, but 1 picture is missing. Add it from its row.", roundLastWord(emptyList(), 1))
        assertEquals("Counted, but 3 pictures are missing. Add them from their rows.", roundLastWord(emptyList(), 3))
    }

    @Test
    fun last_word_keeps_the_skipped_line_and_adds_missing_pictures() {
        assertEquals(
            "1 app skipped. Update it first. 2 pictures are missing. Add them from their rows.",
            roundLastWord(listOf("Update it first."), 2),
        )
    }

    @Test
    fun a_stored_round_keeps_its_held_mark_and_missing_count() {
        val stop = RoundStore.Stop("s1", "com.a", "A")
        val round = RoundStore.Round(listOf(stop), 0, 5L, recordedIndex = 0, noPicture = 2)
        val json = Json { ignoreUnknownKeys = true }
        val back = json.decodeFromString(RoundStore.Round.serializer(), json.encodeToString(RoundStore.Round.serializer(), round))
        assertEquals(0, back.recordedIndex)
        assertEquals(2, back.noPicture)
        // A round saved by an older build has neither.
        val old = json.decodeFromString(RoundStore.Round.serializer(), """{"stops":[],"index":0,"since":1}""")
        assertEquals(-1, old.recordedIndex)
        assertEquals(0, old.noPicture)
    }
}
