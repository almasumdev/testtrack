package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.Live
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

/**
 * A screen fetching again while it shows, when the server says something on it changed.
 *
 * [matters] picks the events that concern this screen out of everything on the shared socket, and
 * a burst of them is one fetch. [every] adds a plain refresh on a timer, for a screen whose
 * changes the server does not announce one by one. Start on resume, stop on pause: a screen
 * nobody is looking at has no reason to hold a socket open.
 */
class LiveReload(
    private val who: String,
    private val scope: CoroutineScope,
    private val every: Long? = null,
    private val matters: (Live.Event) -> Boolean,
) {
    private var job: Job? = null
    private var channels: Set<String> = emptySet()

    fun start(reload: () -> Unit) {
        job?.cancel()
        job =
            scope.launch {
                var pending: Job? = null
                launch {
                    Live.events.filter(matters).collect {
                        pending?.cancel()
                        pending =
                            launch {
                                delay(700)
                                reload()
                            }
                    }
                }
                every?.let {
                    launch {
                        while (true) {
                            delay(it)
                            reload()
                        }
                    }
                }
            }
        Live.watch(who, channels)
    }

    /** Which channels beyond your own and the feed's, kept for the next start if not showing. */
    fun channels(wanted: Set<String>) {
        channels = wanted
        if (job?.isActive == true) Live.watch(who, wanted)
    }

    fun stop() {
        job?.cancel()
        job = null
        Live.stop(who)
    }
}
