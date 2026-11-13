package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.BuildConfig
import com.eazyverse.testtrack.data.DebugOverrides
import com.eazyverse.testtrack.data.Session

/**
 * What the debug section on the You page reads and changes. The screens never see
 * [DebugOverrides], so the layers stay apart. [visible] is false in a release build.
 */
object DebugSwitches {
    val visible: Boolean get() = BuildConfig.DEBUG

    val noGroup: Boolean get() = DebugOverrides.noGroup
    val noDrive: Boolean get() = DebugOverrides.noDrive

    /** Turning it on forgets the remembered yes, so turning it off asks the server again. */
    fun setNoGroup(on: Boolean) {
        DebugOverrides.updateNoGroup(on)
        if (on) Session.updateMember(false)
    }

    fun setNoDrive(on: Boolean) = DebugOverrides.updateNoDrive(on)
}
