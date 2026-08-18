package com.eazyverse.testtrack

import android.app.Application
import com.eazyverse.testtrack.data.AppUpdateService
import com.eazyverse.testtrack.data.Telemetry

/**
 * Process start, which is not the same thing as the app being opened.
 *
 * TestTrack's process comes up without [MainActivity] more often than most apps: a reminder wakes
 * the worker, and the capture service is started from a round the tester has already walked away
 * from. Anything that has to be true for the whole process belongs here rather than on a screen.
 *
 * [AppUpdateService.init] is here for the same reason it is in the app this was ported from: it
 * reads the running build, and everything the gate decides is measured against that number.
 * [Session] is not, deliberately — it is loaded synchronously in `onCreate` so the first screen
 * resolves before anything is composed, and it needs to stay in the activity for that.
 */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppUpdateService.init(this)
        Telemetry.init(this)
    }
}
