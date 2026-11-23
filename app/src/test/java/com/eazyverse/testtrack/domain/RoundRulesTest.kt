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

    @Test
    fun `the first resume just after the screen-share yes is TestTrack's own`() {
        val resume = ConsentResume().apply { yes(50_000) }
        assertTrue(resume.swallows(50_000 + RoundRules.OWN_RESUME_MS - 1))
    }

    @Test
    fun `a second resume within the second is the tester back`() {
        val resume = ConsentResume().apply { yes(50_000) }
        assertTrue(resume.swallows(50_100))
        assertFalse(resume.swallows(50_400))
    }

    @Test
    fun `a resume without the yes is the tester back`() {
        assertFalse(ConsentResume().swallows(500))
    }

    @Test
    fun `a resume after the second, or before the yes, is the tester back and uses the yes up`() {
        val late = ConsentResume().apply { yes(50_000) }
        assertFalse(late.swallows(50_000 + RoundRules.OWN_RESUME_MS))
        assertFalse(late.swallows(50_000 + RoundRules.OWN_RESUME_MS + 1))
        assertFalse(ConsentResume().apply { yes(50_000) }.swallows(40_000))
    }
}
