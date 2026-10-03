package com.eazyverse.testtrack.domain

enum class LaunchDestination { INTRO, HOME }

/** Returning users go home. Feature permissions never decide the start destination. */
fun launchDestination(
    onboardingDone: Boolean,
    hasSavedAccount: Boolean,
    authenticated: Boolean,
): LaunchDestination = if (!onboardingDone && !hasSavedAccount && !authenticated) LaunchDestination.INTRO else LaunchDestination.HOME
