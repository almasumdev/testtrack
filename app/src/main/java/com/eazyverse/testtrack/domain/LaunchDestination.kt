package com.eazyverse.testtrack.domain

enum class LaunchDestination { INTRO, SETUP, HOME }

/** Returning accounts keep their flow, even if they installed while the old intro was disabled. */
fun launchDestination(
    onboardingDone: Boolean,
    hasSavedAccount: Boolean,
    authenticated: Boolean,
    setupComplete: Boolean,
): LaunchDestination =
    when {
        !onboardingDone && !hasSavedAccount && !authenticated -> LaunchDestination.INTRO
        hasSavedAccount && authenticated && !setupComplete -> LaunchDestination.SETUP
        else -> LaunchDestination.HOME
    }
