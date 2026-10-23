package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.AppIconData
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiApp
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.IconIn
import com.eazyverse.testtrack.ui.AppCrewStatus
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.AppFeedbackStatus
import com.eazyverse.testtrack.ui.MyAppsChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /**
     * A read of the list is under way, first or not. A page waiting on an app it does not have
     * shows it loading while this is set, rather than saying the app is gone.
     */
    var reading by mutableStateOf(false)
        private set

    /** Nobody is signed in. Not a fault, and not the same thing as being unable to reach us. */
    var signedOut by mutableStateOf(false)
        private set

    /** The app an action is running on, so its page can show it busy. */
    var acting by mutableStateOf<String?>(null)
        private set

    /** Which action that is, so the page shows it on the row or the icon it came from. */
    var actingOn by mutableStateOf<AppAction?>(null)
        private set

    /** Why the last action on an app did not happen, said on its page. */
    var actionError by mutableStateOf<String?>(null)
        private set

    fun clearActionError() {
        actionError = null
    }

    fun byId(id: String): AppEntry? = apps.firstOrNull { it.id == id }

    /**
     * An app the server has just answered with, shown before the list is read again: the page
     * opened for a newly added app otherwise found nothing and said it had been deleted.
     */
    fun put(app: AppEntry) {
        apps = listOf(app) + apps.filterNot { it.id == app.id }
    }

    // The crews your apps are in and your own channel: seats, reviews, tix and apps all say so there.
    private val live =
        LiveReload("apps", viewModelScope) { event ->
            event.channel.startsWith("crew:") || event.channel.startsWith("user:")
        }

    fun startLive(context: Context) {
        if (!signedOut) live.start { load(context) }
    }

    fun stopLive() = live.stop()

    /** Whether the server has said what the list is, even if it said there is nothing in it. */
    private var answered = false

    /** The application context from the first load, for reading again on news from elsewhere. */
    private var context: Context? = null

    /** Loads overlap (news, a return to the tab, an action); only the latest started may land. */
    private var loads = 0

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
        // Only before the first answer. An empty list is an answer too: shimmering again on every
        // live update or return to the tab made "Nothing added yet" flicker in and out.
        if (!answered) loading = true
        val load = ++loads
        reading = true
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    signedOut = true
                    return@launch
                }
                signedOut = false
                val read = Api.apps().apps.map(ApiApp::toEntry)
                // An older answer landing after a newer one would bring back an app just deleted.
                if (load != loads) return@launch
                apps = read
                answered = true
                live.channels(apps.mapNotNull { it.crew?.crewId }.map { "crew:$it" }.toSet())
                offline = false
            } catch (e: ApiError) {
                if (e.isAuth) signedOut = true else offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
                if (load == loads) reading = false
            }
        }
    }

    /** Mark an app live once Play's public page shows it. From then on: feedback, never a crew. */
    fun goLive(
        context: Context,
        id: String,
    ) = act(context, id, AppAction.LIVE) { Api.goLive(id) }

    /** Ask Play about an unverified app now. Play's refusal says what is still missing. */
    fun verify(
        context: Context,
        id: String,
    ) = act(context, id, AppAction.VERIFY) { Api.verifyApp(id) }

    fun delete(
        context: Context,
        id: String,
        onDone: () -> Unit,
    ) = act(context, id, AppAction.DELETE, onDone) { Api.deleteApp(id) }

    /** Give an app the icon it has on this phone. */
    fun iconFromPhone(
        context: Context,
        id: String,
        pkg: String,
    ) = act(context, id, AppAction.ICON) {
        Api.setAppIcon(id, IconIn(readIcon { AppIconData.ofInstalled(context.applicationContext, pkg) }))
    }

    /** Give an app a picture its owner chose, cropped square. */
    fun iconFromPicture(
        context: Context,
        id: String,
        uri: Uri,
    ) = act(context, id, AppAction.ICON) {
        Api.setAppIcon(id, IconIn(readIcon { AppIconData.ofPicture(context.applicationContext, uri) }))
    }

    private suspend fun readIcon(read: () -> AppIconData.Encoded?): String =
        withContext(Dispatchers.Default) { runCatching(read).getOrNull() }?.base64 ?: throw UnusablePicture()

    /** A picture that could not be read or made small enough. Said as that, not as a lost connection. */
    private class UnusablePicture : Exception()

    private fun act(
        context: Context,
        id: String,
        what: AppAction,
        onDone: () -> Unit = {},
        call: suspend () -> Unit,
    ) {
        if (acting != null) return
        acting = id
        actingOn = what
        actionError = null
        viewModelScope.launch {
            try {
                // A pair the server stopped taking is traded for a new one here, not refused.
                ApiAuth.ensure(context)
                call()
                onDone()
                load(context)
            } catch (e: ApiError) {
                actionError = e.message
            } catch (_: UnusablePicture) {
                actionError = "That picture could not be used. Try another one."
            } catch (_: Exception) {
                actionError = "We could not reach TestTrack. Check your connection."
            } finally {
                acting = null
                actingOn = null
            }
        }
    }
}

/** The things done to an app from its page, each shown busy where it was asked for. */
enum class AppAction { LIVE, VERIFY, DELETE, ICON }

/** The server's app, as the screens draw it. */
fun ApiApp.toEntry(): AppEntry =
    AppEntry(
        id = id,
        pkg = packageName,
        name = name,
        icon = icon,
        developer = developer,
        live = live,
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
