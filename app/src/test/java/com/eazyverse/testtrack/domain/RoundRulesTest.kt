package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A round found on disk after Android closed TestTrack mid-visit.
 *
 * It used to come back without the camera, open the next app as soon as TestTrack was in front,
 * and could even record last night's use as today's.
 */
class RoundRulesTest {
    private val today = 1_000_000_000L

    @Test
    fun `a round with pictures whose camera went with the process ends`() {
        assertFalse(RoundRules.resumes(pictures = true, cameraOn = false, startedAt = today + 5, startOfToday = today))
    }

    @Test
    fun `a round from an earlier day ends`() {
        assertFalse(RoundRules.resumes(pictures = true, cameraOn = true, startedAt = today - 5, startOfToday = today))
        assertFalse(RoundRules.resumes(pictures = false, cameraOn = false, startedAt = today - 5, startOfToday = today))
    }

    @Test
    fun `a round kept by an older build, with no start, ends`() {
        assertFalse(RoundRules.resumes(pictures = false, cameraOn = false, startedAt = 0, startOfToday = today))
    }

    @Test
    fun `a round from today with its camera, or without pictures, goes on`() {
        assertTrue(RoundRules.resumes(pictures = true, cameraOn = true, startedAt = today + 5, startOfToday = today))
        assertTrue(RoundRules.resumes(pictures = false, cameraOn = false, startedAt = today + 5, startOfToday = today))
    }

    @Test
    fun `a restored visit is measured from today at the earliest`() {
        assertEquals(today, RoundRules.measureFrom(today - 3_600_000, today))
        assertEquals(today + 60_000, RoundRules.measureFrom(today + 60_000, today))
    }
}
