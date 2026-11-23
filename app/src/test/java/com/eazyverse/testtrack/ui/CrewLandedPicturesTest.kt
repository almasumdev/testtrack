package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.AppNight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewLandedPicturesTest {
    private fun app(
        id: String,
        day: Int = 3,
        done: Boolean = false,
        due: Boolean = true,
    ) = CrewApp(id, id, "pkg.$id", "Owner", day = day, done = done, pictureDue = due)

    private val crew =
        Crew(
            id = "c",
            name = "Crew",
            phase = CrewPhase.RUN,
            apps = listOf(app("a"), app("b"), app("d", done = true, due = false)),
            optional = listOf(app("a")),
            strip = listOf(app("a"), app("b")),
        )

    @Test fun a_landed_picture_marks_that_app_and_night_in_all_three_lists() {
        val out = withLandedPictures(listOf(crew), setOf(AppNight("a", 3))).single()
        assertTrue(out.apps[0].done)
        assertFalse(out.apps[0].pictureDue)
        assertTrue(out.optional[0].done)
        assertTrue(out.strip[0].done)
        assertFalse(out.apps[1].done)
        assertTrue(out.apps[1].pictureDue)
        assertFalse(out.strip[1].done)
    }

    @Test fun another_night_of_the_same_app_is_not_marked() {
        val out = withLandedPictures(listOf(crew), setOf(AppNight("a", 2))).single()
        assertFalse(out.apps[0].done)
        assertTrue(out.apps[0].pictureDue)
    }

    @Test fun an_app_already_done_is_left_as_it_is() {
        val out = withLandedPictures(listOf(crew), setOf(AppNight("d", 3))).single()
        assertEquals(crew.apps[2], out.apps[2])
    }

    @Test fun nothing_landed_returns_the_same_list() {
        val list = listOf(crew)
        assertSame(list, withLandedPictures(list, emptySet()))
    }
}
