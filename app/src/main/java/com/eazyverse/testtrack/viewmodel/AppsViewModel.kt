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
import com.eazyverse.testtrack.net.AppBoxesIn
import com.eazyverse.testtrack.net.IconIn
import com.eazyverse.testtrack.ui.AppCrewStatus
import com.eazyverse.testtrack.ui.AppEntry
import com.eazyverse.testtrack.ui.AppFeedbackStatus
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.MyAppsChanged
import com.eazyverse.testtrack.ui.SeatBoxStore
import com.eazyverse.testtrack.ui.SeatBoxes
import com.eazyverse.testtrack.ui.SeatFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime

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

    /** What the server says about each app beyond its entry: the boxes, the last check, why it can't go in. */
    var facts by mutableStateOf<Map<String, SeatFacts>>(emptyMap())
        private set

    /** The boxes kept on the phone, for a server that does not keep them yet. */
    private var stored by mutableStateOf<Map<String, SeatBoxes>>(emptyMap())

    /** Boxes just ticked here, shown at once; dropped once the server has them. */
    private var ticked by mutableStateOf<Map<String, SeatBoxes>>(emptyMap())

    /** Checks of Play that refused, this run, in epoch milliseconds, for an app whose server does not say. */
    var failedChecks by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    /**
     * The two boxes for [app]: just ticked here, else the server's, else the phone's. Both unticked
     * until somebody says otherwise.
     */
    fun boxes(app: AppEntry): SeatBoxes = ticked[app.id] ?: facts[app.id]?.boxes ?: stored[app.pkg] ?: SeatBoxes()

    /** Read what the phone keeps; once, and cheap. */
    fun attach(context: Context) {
        if (stored.isEmpty()) stored = SeatBoxStore.all(context.applicationContext)
    }

    /**
     * Keep the two boxes for [app]. On the phone first, so they hold with no connection or an older
     * server; then sent to the server, which is what a seat is checked against.
     */
    fun setBoxes(
        context: Context,
        app: AppEntry,
        next: SeatBoxes,
    ) {
        val appContext = context.applicationContext
        SeatBoxStore.write(appContext, app.pkg, next)
        stored = stored + (app.pkg to next)
        ticked = ticked + (app.id to next)
        viewModelScope.launch {
            try {
                ApiAuth.ensure(appContext)
                val row = Api.patch<AppBoxesIn, ApiApp>("/apps/${app.id}", AppBoxesIn(next.countries, next.devices))
                val saved = row.toSeatFacts()
                // The server has them now: its updated row replaces ours and is the one shown from here.
                if (saved.boxes != null && ticked[app.id] == next) {
                    apps = apps.map { if (it.id == row.id) row.toEntry() else it }
                    facts = facts + (row.id to saved)
                    ticked = ticked - app.id
                }
            } catch (_: Exception) {
                // An older server has no such call, or there is no connection: the phone holds them,
                // and the seat's own refusal says so if the server needed them after all.
            }
        }
    }

    /** The server refused a seat for unticked boxes: show them unticked, so ticking sends them again. */
    fun clearBoxes(
        context: Context,
        app: AppEntry,
    ) {
        SeatBoxStore.write(context.applicationContext, app.pkg, SeatBoxes())
        stored = stored + (app.pkg to SeatBoxes())
        ticked = ticked + (app.id to SeatBoxes())
    }

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

    // Your kept apps, only without a connection; online the list shimmers until the server answers.
    // None kept waits for the server rather than say so.
    private fun seedFromPhone(afterFailure: Boolean = false) {
        Api.lastApps(afterFailure)?.takeIf { it.apps.isNotEmpty() }?.let {
            apps = it.apps.map(ApiApp::toEntry)
            facts = it.apps.associate { app -> app.id to app.toSeatFacts() }
            loading = false
        }
    }

    init {
        seedFromPhone()
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
                val answer = Api.apps()
                val read = answer.apps.map(ApiApp::toEntry)
                // An older answer landing after a newer one would bring back an app just deleted.
                if (load != loads) return@launch
                apps = read
                facts = answer.apps.associate { it.id to it.toSeatFacts() }
                answered = true
                live.channels(apps.mapNotNull { it.crew?.crewId }.map { "crew:$it" }.toSet())
                offline = false
            } catch (e: ApiError) {
                if (e.isAuth) signedOut = true else offline = true
            } catch (e: Exception) {
                offline = true
                // No connection and nothing shown yet: the kept apps stand in. Not for a server fault.
                if (e is java.io.IOException && !answered && apps.isEmpty()) seedFromPhone(afterFailure = true)
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
        onDone: () -> Unit = {},
    ) = act(context, id, AppAction.VERIFY, onDone) { Api.verifyApp(id) }

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
                if (what == AppAction.VERIFY) failedChecks = failedChecks - id
                onDone()
                load(context)
            } catch (e: ApiError) {
                actionError = e.message
                // Play was asked and said no: the sheet says when, even if the server does not keep it.
                if (what == AppAction.VERIFY && !e.isAuth) failedChecks = failedChecks + (id to System.currentTimeMillis())
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
        crew =
            crew?.let {
                AppCrewStatus(
                    it.state,
                    it.crewId,
                    it.crewName,
                    it.day,
                    it.runDays.takeIf { n ->
                        n > 0
                    } ?: CrewRules.RUN_DAYS,
                )
            },
        crewsDone = crewsDone,
        feedback = feedback?.let { AppFeedbackStatus(it.state, it.postId, it.done, it.bought, it.toCheck, it.unlisted) },
        reviewsReceived = reviewsReceived,
        canCrew = canCrew,
        canFeedback = canFeedback,
        canDelete = canDelete,
        whyNotDelete = whyNotDelete,
    )

/** What the server says about an app beyond its entry: its boxes, its last check, and why it can't go in. */
fun ApiApp.toSeatFacts(): SeatFacts =
    SeatFacts(
        boxes = if (allCountries != null || allDevices != null) SeatBoxes(allCountries == true, allDevices == true) else null,
        checkedAt = lastCheckAt?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
        checkedOk = lastCheckOk,
        blockReason = crewBlockReason,
        blockCrew = crewBlockCrew,
    )
