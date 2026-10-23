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
}
