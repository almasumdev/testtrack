package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchDestinationTest {
    @Test
    fun firstInstallGuestSeesOnboarding() {
        assertEquals(LaunchDestination.INTRO, launchDestination(false, false, false))
    }

    @Test
    fun everyReturningUserGoesHomeWithoutPermissionRequirements() {
        for (onboarded in listOf(false, true)) {
            for (savedAccount in listOf(false, true)) {
                for (authenticated in listOf(false, true)) {
                    if (onboarded || savedAccount || authenticated) {
                        assertEquals(LaunchDestination.HOME, launchDestination(onboarded, savedAccount, authenticated))
                    }
                }
            }
        }
    }
}
