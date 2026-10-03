package com.eazyverse.testtrack.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * What the header shows, on every screen.
 *
 * The first inhabitant of `viewmodel/`, which is where the rest of them are going. Everything in
 * `ui/` is barred from reaching the network, and until now every view model sat in `ui/` and did
 * exactly that: forty three of those are written down in the architecture baseline as work owed.
 * A new one had a choice of joining that queue or starting the package that empties it.
 *
 * Held by the shell rather than by a screen, because the header outlives all five tabs. A model
 * per screen would mean five requests for the same two numbers and five chances for them to
 * disagree about the balance somebody is looking at.
 */
class HudViewModel : ViewModel() {
    /** Null until the first answer, which is what tells the header to show nothing yet. */
    var level by mutableStateOf<Int?>(null)
        private set

    var intoLevel by mutableStateOf(0)
        private set

    /** How wide the current level is. Never drawn as a denominator until it is known to be above zero. */
    var forNext by mutableStateOf(0)
        private set

    var tix by mutableStateOf(0)
        private set

    /** Nobody is signed in, so there is no level and no balance to show. */
    var signedOut by mutableStateOf(!ApiSession.signedIn)
        private set

    /**
     * The crew filling right now, for somebody who has not signed in.
     *
     * The header would otherwise be a ring with no progress in it, which answers nothing and asks
     * nothing. This is the one true thing a stranger can be told, and it happens to be the only
     * question they have: whether anybody is actually in here. Null when no crew is filling, and
     * then the header says nothing rather than inventing something.
     */
    var openCrew by mutableStateOf<String?>(null)
        private set

    var openTaken by mutableStateOf(0)
        private set

    var openSeats by mutableStateOf(0)
        private set

    /**
     * Read it again.
     *
     * Called on every resume rather than once, because tix move while somebody is inside another
     * app being tested, and coming back to a stale balance is the screen telling them their last
     * ten seconds did not count.
     */
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            ApiSession.signedInState.collect { signedIn ->
                signedOut = !signedIn
                if (!signedIn) {
                    loadJob?.cancel()
                    level = null
                    intoLevel = 0
                    forNext = 0
                    tix = 0
                }
            }
        }
    }

    fun load(context: Context) {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                try {
                    if (!ApiAuth.ensure(context)) {
                        signedOut = true
                        val open = Api.crews().open
                        openCrew = open?.name
                        openTaken = open?.taken ?: 0
                        openSeats = open?.seats ?: 0
                        return@launch
                    }
                    signedOut = false
                    val generation = ApiSession.generation
                    val me = Api.me()
                    if (generation != ApiSession.generation || !ApiSession.signedIn) return@launch
                    level = me.level
                    intoLevel = me.expIntoLevel
                    forNext = me.expForNext
                    tix = me.tix
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep cached profile data during a network failure; auth is a separate state.
                }
            }
    }
}
