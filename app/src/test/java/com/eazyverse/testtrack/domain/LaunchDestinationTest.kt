package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchDestinationTest {
    @Test
    fun `new guest sees intro and completed guest can browse`() {
        assertEquals(LaunchDestination.INTRO, launchDestination(false, false, false, false))
        assertEquals(LaunchDestination.HOME, launchDestination(true, false, false, false))
    }

    @Test
    fun `returning accounts bypass intro and keep unfinished setup`() {
        for (seen in listOf(false, true)) {
            assertEquals(LaunchDestination.SETUP, launchDestination(seen, true, true, false))
            assertEquals(LaunchDestination.HOME, launchDestination(seen, true, true, true))
        }
    }

    @Test
    fun `restoring or expired accounts are not mistaken for a new install`() {
        assertEquals(LaunchDestination.HOME, launchDestination(false, true, false, false))
        assertEquals(LaunchDestination.HOME, launchDestination(false, false, true, false))
    }
}
