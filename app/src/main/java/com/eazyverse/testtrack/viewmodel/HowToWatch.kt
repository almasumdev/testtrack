package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.data.Telemetry

/** A how-to video opened, passed on from the screens, which do not reach data themselves. */
object HowToWatch {
    fun opened(
        video: String,
        bangla: Boolean,
        fromList: Boolean,
    ) = Telemetry.howToOpened(video, bangla, fromList)
}
