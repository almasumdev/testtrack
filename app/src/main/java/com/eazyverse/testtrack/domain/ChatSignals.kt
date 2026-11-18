package com.eazyverse.testtrack.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Chat news between the push service and the screens, with nothing Android in it.
 *
 * A chat push ticks [news], so the tab counts and the crew tiles read their numbers again.
 * [openCrew] is the crew whose chat is on screen: a push for it is not drawn, since the chat
 * itself shows the message within seconds.
 */
object ChatSignals {
    private val ticks = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** A crew id, each time a chat push arrives for it. */
    val news: SharedFlow<String> = ticks.asSharedFlow()

    @Volatile
    var openCrew: String? = null

    fun tick(crewId: String) {
        ticks.tryEmit(crewId)
    }
}
