package com.eazyverse.testtrack.viewmodel

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.eazyverse.testtrack.domain.ConsentResume

/**
 * TestTrack's own resume just after Android's screen-share yes, which the round lets pass once
 * ([ConsentResume]). Only the yes arms it: a private test or a round found on disk never does.
 */
internal object OwnResume {
    private val consent = ConsentResume()

    /** That resume was just let pass, so a second call for it (two pages listening) passes too. */
    private var passing = false

    /** Android said yes to the screen share; TestTrack resumes just after. */
    fun yes() = consent.yes(SystemClock.elapsedRealtime())

    /**
     * Whether this call is for TestTrack's own resume just after the yes. Only the first resume
     * after it is; a second call in the same moment is that same resume.
     */
    fun take(): Boolean {
        if (passing) return true
        if (!consent.swallows(SystemClock.elapsedRealtime())) return false
        passing = true
        Handler(Looper.getMainLooper()).post { passing = false }
        return true
    }
}
