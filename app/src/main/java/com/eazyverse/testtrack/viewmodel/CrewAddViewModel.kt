package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.signInNotes
import kotlinx.coroutines.launch

/**
 * Putting an app from My apps into a crew, from the seat sheet: the name the crew sees, how they get past its sign-in,
 * and the price. The app itself was found and confirmed when it was added, so nothing is looked
 * up here; the server checks it on Play again as the seat is taken.
 */
class CrewAddViewModel : ViewModel() {
    /** The name the crew sees, the app's own unless changed. */
    var name by mutableStateOf("")
        private set

    /** Whether the app needs a sign-in, and the test account the crew uses for it. */
    var needsSignIn by mutableStateOf(false)
        private set
    var username by mutableStateOf("")
        private set
    var password by mutableStateOf("")
        private set
    var also by mutableStateOf("")
        private set

    /** What the seat carries for the crew: the sign-in as labelled lines, or nothing. */
    val notes: String get() = if (needsSignIn) signInNotes(username, password, also) else ""

    /** No sign-in, or one the crew can actually use: a username and password, or instructions. */
    val signInReady: Boolean
        get() = !needsSignIn || (username.isNotBlank() && password.isNotBlank()) || also.isNotBlank()

    /** The balance a seat is paid from, once the server has said; null until then. */
    var tix by mutableStateOf<Int?>(null)
        private set

    /** Known to be short of a seat's price. Unknown is not short: the server has the last word. */
    val short: Boolean get() = tix?.let { it < CrewRules.SEAT_COST } ?: false

    init {
        viewModelScope.launch { runCatching { tix = Api.me().tix } }
    }

    /** The app the fields below are for; they start over when another is picked. */
    private var forApp: String? = null

    /** The app these details are for: its name, until somebody types another. Another app starts clean. */
    fun start(app: AppEntry) {
        if (forApp != app.id) {
            reset()
            forApp = app.id
        }
        if (name.isBlank()) name = app.name.take(FeedRules.APP_NAME_MAX)
    }

    /** Nothing kept: the sheet is gone, and a sign-in typed into it goes with it. */
    fun reset() {
        forApp = null
        name = ""
        needsSignIn = false
        username = ""
        password = ""
        also = ""
    }

    fun rename(next: String) {
        name = next.take(FeedRules.APP_NAME_MAX)
    }

    fun chooseSignIn(needed: Boolean) {
        needsSignIn = needed
    }

    fun typeUsername(next: String) {
        username = next
    }

    fun typePassword(next: String) {
        password = next
    }

    fun typeAlso(next: String) {
        also = next
    }
}
