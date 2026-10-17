package com.eazyverse.testtrack.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.ui.AskFailure
import com.eazyverse.testtrack.ui.FeedRules
import com.eazyverse.testtrack.ui.HandEntry
import com.eazyverse.testtrack.ui.lookupFailure
import com.eazyverse.testtrack.ui.packageFrom
import com.eazyverse.testtrack.ui.signInNotes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Putting an app into a crew, one step at a time: find it on Play, confirm it is the right one,
 * then take the seat.
 *
 * It was one dialog that took the seat on the spot with whatever was typed, so the first time
 * anybody saw which app the server had found was in the crew itself. A crew app is a closed
 * testing app by its nature, so it is always looked up the closed way.
 *
 * The one failure there is a way past is our own checker being down: the server takes the seat
 * anyway and checks it again shortly, so the name can be typed. A listing Play will not show is
 * refused here as the server refuses it, because all fourteen members would hit the same wall.
 */
class CrewAddViewModel :
    ViewModel(),
    HandEntry {
    var query by mutableStateOf("")
        private set

    /** Play's answer, or what was typed. Name, icon and package are shown to confirm. */
    var found by mutableStateOf<Found?>(null)
        private set

    var looking by mutableStateOf(false)
        private set

    var failure by mutableStateOf<AskFailure?>(null)
        private set

    var byHand by mutableStateOf(false)
        private set

    override var handName by mutableStateOf("")
        private set

    /** The name the crew will see, Play's unless changed. */
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

    private var lookupJob: Job? = null

    data class Found(
        val pkg: String,
        val name: String,
        val icon: String = "",
        val developer: String = "",
        /** Typed rather than read off Play. */
        val unchecked: Boolean = false,
    )

    val canLook: Boolean get() = query.trim().length >= 3 && !looking

    override val typedPackage: String? get() = packageFrom(query)

    override val canUseByHand: Boolean get() = typedPackage != null && handName.isNotBlank()

    fun type(next: String) {
        clearLookup()
        query = next.take(QUERY_MAX)
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

    fun rename(next: String) {
        name = next.take(FeedRules.APP_NAME_MAX)
    }

    override fun typeName(next: String) {
        handName = next.take(FeedRules.APP_NAME_MAX)
    }

    fun enterByHand() {
        failure = null
        byHand = true
    }

    override fun useByHand() {
        val pkg = typedPackage ?: return
        if (handName.isBlank()) return
        found = Found(pkg, handName.trim(), unchecked = true)
        name = handName.trim()
        byHand = false
    }

    /** Back to the link, keeping it, so a wrong paste can be corrected. */
    fun startOver() {
        clearLookup()
        found = null
    }

    fun look() {
        if (!canLook) return
        failure = null
        looking = true
        lookupJob =
            viewModelScope.launch {
                try {
                    val listing = Api.lookupApp(query.trim(), closed = true)
                    found = Found(listing.`package`, listing.name, listing.icon, listing.developer)
                    name = listing.name.take(FeedRules.APP_NAME_MAX)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = crewLookupFailure(error)
                } finally {
                    if (isActive) looking = false
                }
            }
    }

    private fun clearLookup() {
        lookupJob?.cancel()
        looking = false
        failure = null
        byHand = false
    }

    private companion object {
        const val QUERY_MAX = 2048
    }
}

/**
 * Ask for feedback's reading of a failed lookup, with one difference: a listing Play will not show
 * offers no way past, because the server refuses a seat on it and every member would be stuck.
 */
internal fun crewLookupFailure(error: Throwable): AskFailure {
    val failure = lookupFailure(error, closed = true)
    return if (error is ApiError && error.code == "listing_not_visible") {
        AskFailure(
            failure.title,
            "Check that the testers group is added to your closed track and the release is available, " +
                "then try again. A crew can only start on an app every member can install.",
        )
    } else {
        failure
    }
}
