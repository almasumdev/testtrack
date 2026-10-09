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
     * robot on Profile) can be looked at for as long as needed. Set back to false when done.
     */
    private const val HOLD_LOADING = true

    val holdLoading: Boolean get() = BuildConfig.DEBUG && HOLD_LOADING
}
