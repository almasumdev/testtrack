package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.domain.ChatSignals
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiActions
import com.eazyverse.testtrack.net.ApiSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * The numbers on the bottom bar: what waits on this account, one sum a tab.
 *
 * Asked of the server rather than added up here, because each tab's screens load their own lists
 * only when they are opened, and a number that is right only after you have looked is no use on
 * a tab you have not opened. Asked again on every tab change, on every resume and whenever the
 * account's own or a crew's news arrives. Signed out, or from a server without the count, it is
 * nothing, and the bar shows no numbers.
 */
class TabCountsViewModel : ViewModel() {
    var actions by mutableStateOf<ApiActions?>(null)
        private set

    /** Unread chat over this account's unmuted crews: a dot on Crew when it has nothing else to do. */
    val crewChat: Int get() = actions?.parts?.crewChat ?: 0

    private val live =
        LiveReload("tabs", viewModelScope) { it.channel.startsWith("user:") || it.channel.startsWith("crew:") }

    // Chat is not on the live socket: a chat push is what says the count moved.
    private var chat: Job? = null

    fun refresh() {
        if (!ApiSession.signedIn) {
            actions = null
            return
        }
        viewModelScope.launch {
            try {
                actions = Api.actions()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Kept as it was: a number a minute old beats a bar that flickers empty.
            }
        }
    }

    fun start() {
        live.start { refresh() }
        chat?.cancel()
        chat = viewModelScope.launch { ChatSignals.news.collect { refresh() } }
    }

    fun stop() {
        live.stop()
        chat?.cancel()
        chat = null
    }
}
