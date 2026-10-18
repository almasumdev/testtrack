package com.eazyverse.testtrack.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiApp
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.ui.AppCrewStatus
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.AppFeedbackStatus
import com.eazyverse.testtrack.ui.MyAppsChanged
import kotlinx.coroutines.launch

/**
 * My apps: every app added, where each is with crews and feedback, and the two things done to an
 * app itself, going live and being deleted.
 *
 * One model for the tab, an app's page and the pickers on Crew and Circle, borrowed from the home
 * screen's entry, so all of them show the same list and a change anywhere is seen everywhere.
 */
class AppsViewModel : ViewModel() {
    var apps by mutableStateOf<List<AppEntry>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    var offline by mutableStateOf(false)
        private set

    /** Nobody is signed in. Not a fault, and not the same thing as being unable to reach us. */
    var signedOut by mutableStateOf(false)
        private set

    /** The app an action is running on, so its page can show it busy. */
    var acting by mutableStateOf<String?>(null)
        private set

    /** Why the last action on an app did not happen, said on its page. */
    var actionError by mutableStateOf<String?>(null)
        private set

    fun clearActionError() {
        actionError = null
    }

    fun byId(id: String): AppEntry? = apps.firstOrNull { it.id == id }

    // The crews your apps are in and your own channel: seats, reviews, tix and apps all say so there.
    private val live =
        LiveReload("apps", viewModelScope) { event ->
            event.channel.startsWith("crew:") || event.channel.startsWith("user:")
        }

    fun startLive(context: Context) {
        if (!signedOut) live.start { load(context) }
    }

    fun stopLive() = live.stop()

    /** The application context from the first load, for reading again on news from elsewhere. */
    private var context: Context? = null

    // Your kept apps, before the first frame; none kept waits for the server rather than say so.
    init {
        Api.lastApps()?.takeIf { it.apps.isNotEmpty() }?.let {
            apps = it.apps.map(ApiApp::toEntry)
            loading = false
        }
        // An app added, put in a crew or up for feedback on another screen: read again.
        viewModelScope.launch { MyAppsChanged.events.collect { context?.let(::load) } }
    }

    fun load(context: Context) {
        this.context = context.applicationContext
        // Nothing shown yet: the shimmer while it comes, not "Nothing up yet" over an app just added.
        if (apps.isEmpty()) loading = true
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    signedOut = true
                    return@launch
                }
                signedOut = false
                apps = Api.apps().apps.map(ApiApp::toEntry)
                live.channels(apps.mapNotNull { it.crew?.crewId }.map { "crew:$it" }.toSet())
                offline = false
            } catch (e: ApiError) {
                if (e.isAuth) signedOut = true else offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    /** Mark an app live once Play's public page shows it. From then on: feedback, never a crew. */
    fun goLive(
        context: Context,
        id: String,
    ) = act(context, id) { Api.goLive(id) }

    /** Ask Play about an unverified app now. Play's refusal says what is still missing. */
    fun verify(
        context: Context,
        id: String,
    ) = act(context, id) { Api.verifyApp(id) }

    fun delete(
        context: Context,
        id: String,
        onDone: () -> Unit,
    ) = act(context, id, onDone) { Api.deleteApp(id) }

    private fun act(
        context: Context,
        id: String,
        onDone: () -> Unit = {},
        call: suspend () -> Unit,
    ) {
        if (acting != null) return
        acting = id
        actionError = null
        viewModelScope.launch {
            try {
                call()
                onDone()
                load(context)
            } catch (e: ApiError) {
                actionError = e.message
            } catch (_: Exception) {
                actionError = "We could not reach TestTrack. Check your connection."
            } finally {
                acting = null
            }
        }
    }
}

/** The server's app, as the screens draw it. */
fun ApiApp.toEntry(): AppEntry =
    AppEntry(
        id = id,
        pkg = packageName,
        name = name,
        icon = icon,
        developer = developer,
        live = live,
        byHand = addedBy == "hand",
        verifiedBy = verifiedBy,
        crew = crew?.let { AppCrewStatus(it.state, it.crewId, it.crewName, it.day, it.runDays) },
        crewsDone = crewsDone,
        feedback = feedback?.let { AppFeedbackStatus(it.state, it.postId, it.done, it.bought) },
        reviewsReceived = reviewsReceived,
        canCrew = canCrew,
        canFeedback = canFeedback,
        canDelete = canDelete,
        whyNotDelete = whyNotDelete,
    )
