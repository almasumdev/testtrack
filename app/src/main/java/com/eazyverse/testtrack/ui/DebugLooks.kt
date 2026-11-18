package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.BuildConfig

/**
 * Debug-only switches for looking at states that are normally gone too fast to see.
 *
 * A release build ignores every one of them, whatever they are set to here.
 */
object DebugLooks {
    /**
     * Hold every tab on its loading state, as if the answer never came, so the shimmer (and the
     * robot on Profile) can be looked at for as long as needed.
     */
    private const val HOLD_LOADING = false

    val holdLoading: Boolean get() = BuildConfig.DEBUG && HOLD_LOADING

    /**
     * Open the after-a-value-moment rating ask ([RatingAsk]) as soon as the app starts, so it can be
     * looked at without waiting for the moment that really triggers it.
     */
    private const val SHOW_RATING_ASK = false

    val showRatingAsk: Boolean get() = BuildConfig.DEBUG && SHOW_RATING_ASK

    /** Temporary Crew UI preview: only seeded member apps, only a debug build on dev. */
    private const val CREW_DEMO_INSTALLED = true

    internal fun crewDemoCopy(pkg: String): HereCopy? =
        demoCrewCopy(pkg, CREW_DEMO_INSTALLED && BuildConfig.DEBUG && BuildConfig.VERSION_NAME.endsWith("-dev"))

    internal fun crewDemoRoundMessage(apps: List<CrewApp>): String? =
        if (apps.any { crewDemoCopy(it.packageName) != null }) {
            "Demo preview: these apps are shown as installed. A real installed app is needed to start a testing round."
        } else {
            null
        }
}
