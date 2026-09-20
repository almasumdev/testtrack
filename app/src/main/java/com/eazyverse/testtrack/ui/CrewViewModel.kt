package com.eazyverse.testtrack.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.SeatIn
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * The crew tab, against the API.
 *
 * Every rule here belongs to the server and none of it is decided twice. Which crew a seat goes
 * into, whether a verdict counts, whose day it is: the phone asks and redraws whatever comes
 * back. A client that works out for itself when a crew is full is a client somebody will edit.
 *
 * The shape that matters is [crews] being a list. A person is in several at once, so nothing here
 * is about "the crew": the tab's own screen is built out of [tonight] and [waiting], which are
 * every crew's work in one column, and a single crew is something you open rather than something
 * you are in.
 */
class CrewViewModel : ViewModel() {
    /** Every crew this account is in, at whatever point each has reached. */
    var crews by mutableStateOf<List<Crew>>(emptyList())
        private set

    /**
     * The crew a new seat would go into.
     *
     * A list of one, kept as a list because the screens were built to show several and may again.
     * Which crew it is stays the server's answer: it sends back whichever is nearest to starting,
     * so the crew that most needs finishing is the one that fills.
     */
    var opens by mutableStateOf<List<Crew>>(emptyList())
        private set

    /** Which tab is showing. */
    var tab by mutableStateOf(CrewPhase.OPEN)
        private set

    /** Which crew's own page is showing, or null for the tab itself. */
    var opened by mutableStateOf<String?>(null)
        private set

    /** A one line result of the last action, shown once and cleared. */
    var message by mutableStateOf<String?>(null)
        private set

    var loading by mutableStateOf(true)
        private set

    /** Set once the API has refused us for good, so the screen can say so rather than spin. */
    var offline by mutableStateOf(false)
        private set

    /** The server's reason a seat cannot be taken, shown as it arrives. */
    var blocked by mutableStateOf<String?>(null)
        private set

    private var settledTab = false

    fun clearMessage() {
        message = null
    }

    fun show(id: String?) {
        opened = id
    }

    fun showTab(phase: CrewPhase) {
        tab = phase
        opened = null
    }

    /** Everything the tab shows, in one call. Safe to run on every resume. */
    fun load(context: Context) {
        viewModelScope.launch {
            try {
                if (!ApiAuth.ensure(context)) {
                    offline = true
                    loading = false
                    return@launch
                }
                apply(Api.crews())
                offline = false
            } catch (e: ApiError) {
                message = e.message
                offline = e.isAuth
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    private fun apply(answer: com.eazyverse.testtrack.net.CrewList) {
        crews = answer.mine.map(ApiCrew::toCrew)
        opens = listOfNotNull(answer.open?.toCrew())
        blocked = answer.blocked

        // Opened on whichever has something owed rather than always the same one, and only the
        // first time: moving somebody's tab under them after they have chosen is worse than
        // opening on the wrong one.
        if (!settledTab) {
            settledTab = true
            tab =
                when {
                    crews.any { it.phase == CrewPhase.RUN } -> CrewPhase.RUN
                    crews.any { it.phase == CrewPhase.LOBBY } -> CrewPhase.LOBBY
                    crews.any { it.phase == CrewPhase.RECORD } -> CrewPhase.RECORD
                    else -> CrewPhase.OPEN
                }
        }
    }

    /** What the pill for a tab counts, or zero where the tab has nothing outstanding. */
    fun badge(phase: CrewPhase) =
        when (phase) {
            CrewPhase.RUN -> tonight.size
            CrewPhase.LOBBY -> waiting.size
            CrewPhase.OPEN -> 0
            CrewPhase.RECORD -> 0
        }

    /** Which crew a seat belongs to, since a merged list only carries the crew's name. */
    fun crewIdOf(seat: CrewSeat) = (crews + opens).first { crew -> crew.seats.any { it.id == seat.id } }.id

    /** The crew being looked at, which may be the open one this account has not joined. */
    val current: Crew?
        get() = opened?.let { id -> (crews + opens).firstOrNull { it.id == id } }

    /**
     * Everything owed tonight, across every running crew.
     *
     * One column rather than one per crew. Which crew an app belongs to is not the reason anybody
     * opens it, and a member with two runs going has one evening, not two.
     */
    val tonight: List<CrewApp>
        get() = tonightIn(crews)

    /** Everything settled tonight, for the count under the list. */
    val doneTonight: Int
        get() = doneTonightIn(crews)

    /** Seats in any crew still filling that this account has not answered on. */
    val waiting: List<CrewSeat>
        get() = waitingIn(crews)

    /**
     * Whether this account holds a seat anywhere that has not started.
     *
     * The server decides this and sends the reason; the flag is only what the screen branches on.
     */
    val holdsSeat: Boolean
        get() = blocked != null

    /** The crew holding your one unstarted seat, by name, or null where you are free. */
    val holding: String?
        get() = holdingIn(crews)

    /** How many runs are going, which is what the nightly figure is made of. */
    val running: Int
        get() = crews.count { it.phase == CrewPhase.RUN }

    /**
     * Whether this account may reject anything in a given crew.
     *
     * Asked of what the server already sent rather than of the server again. A red only counts
     * from somebody who has already got one app in that same crew working, because a Play "not
     * found" far more often means the reader is not set up than that the app is broken. The
     * server refuses it either way; this is so the button can say so before it is pressed.
     */
    fun mayJudge(crewId: String) = mayJudgeIn(crews + opens, crewId)

    /** Say whether an app installed. */
    fun judge(
        crewId: String,
        seat: CrewSeat,
        installed: Boolean,
    ) = call(
        onDone = { answer ->
            message =
                when {
                    answer.state == "confirmed" -> "${seat.appName} is in. Seat confirmed."
                    answer.state == "rejected" ->
                        "${seat.appName} is out. ${seat.owner} has been told why."
                    installed -> "Green sent. One more and ${seat.appName} is in."
                    else -> "Reported. Somebody else will try it before anything happens."
                }
        },
    ) { Api.verdict(seat.id, installed) }

    /**
     * Take a seat with an app of your own.
     *
     * The crew is not named. The server puts a seat into whichever is nearest to starting, and a
     * client that picks its own crew is a client picking which fourteen people test its app.
     */
    fun takeSeat(
        context: Context,
        appName: String,
        packageName: String,
        notes: String = "",
    ) {
        val fingerprint = Device.fingerprint(context)
        if (fingerprint == null) {
            message = "This phone will not identify itself, so it cannot hold a seat."
            return
        }
        call(
            context = context,
            onDone = { message = "Seat taken. Two members have to install it before it counts." },
        ) {
            Api.takeSeat(
                SeatIn(
                    appName = appName,
                    packageName = packageName,
                    notes = notes,
                    deviceFp = fingerprint,
                    timezone = TimeZone.getDefault().id,
                ),
            )
            opened = null
            tab = CrewPhase.LOBBY
        }
    }

    /**
     * The app somebody was sent to, and when. Null when nothing is in flight.
     *
     * Held because the measurement cannot be taken at the moment of leaving. It is taken on the
     * way back, from what UsageStatsManager saw while this app was not on screen.
     */
    private data class Away(
        val seatId: String,
        val packageName: String,
        val label: String,
        val since: Long,
    )

    private var away: Away? = null

    /** What is left of a round, so the night can be walked without fourteen returns to this list. */
    private var round: List<CrewApp> = emptyList()

    /**
     * Tonight's open: go to the app, stay a moment, come back.
     *
     * The whole obligation is opening somebody's app, so this launches it. It used to post the
     * visit immediately and launch nothing, which meant one press credited an app nobody had
     * seen, and the button that did fourteen of them credited a night nobody had spent. Every
     * figure the product reports about testing rested on that.
     */
    fun openApp(
        context: Context,
        app: CrewApp,
    ) {
        round = emptyList()
        sendTo(context, app)
    }

    /**
     * Walk the whole night, one app at a time.
     *
     * A queue rather than a loop: each app has to actually be on screen for its seconds, and only
     * one app can be on screen at once. Every return lands in [settle], which records the one
     * just visited and opens the next.
     */
    fun openAll(
        context: Context,
        crewId: String? = null,
    ) {
        val owed =
            crews
                .filter { crewId == null || it.id == crewId }
                .flatMap { crew -> crew.apps.filter { !it.done && !it.reported } }
        if (owed.isEmpty()) return

        round = owed.drop(1)
        sendTo(context, owed.first())
    }

    private fun sendTo(
        context: Context,
        app: CrewApp,
    ) {
        if (app.packageName.isBlank()) {
            message = "${app.label} did not come with a package name, so it cannot be opened."
            return
        }
        away = Away(app.id, app.packageName, app.label, System.currentTimeMillis())
        message = "Stay in ${app.label} for ${CrewRules.VISIT_SECONDS} seconds."
        openApp(context, app.packageName)
    }

    /**
     * Back from an app. Measure what happened and report it.
     *
     * Called on every resume, and does nothing unless somebody was actually sent somewhere. The
     * reading is of real foreground time, which is the only thing here the phone cannot be talked
     * into inventing, and the server refuses anything under its own floor regardless.
     */
    fun settle(context: Context) {
        val trip = away ?: return
        away = null

        viewModelScope.launch {
            val used = UsageRepo.foregroundMsSince(context, trip.packageName, trip.since)
            val short = used < CrewRules.VISIT_SECONDS * 1000L
            try {
                if (short) {
                    round = emptyList()
                    message =
                        "That was ${used / 1000}s in ${trip.label}. " +
                        "It needs ${CrewRules.VISIT_SECONDS}, so it has not counted yet."
                } else {
                    Api.visit(trip.seatId, used)
                }
            } catch (e: ApiError) {
                round = emptyList()
                message = e.message
            } catch (_: Exception) {
                round = emptyList()
                message = "We could not reach TestTrack. Check your connection."
            }

            val next = round.firstOrNull()
            if (!short && next != null) {
                round = round.drop(1)
                sendTo(context, next)
            } else {
                if (!short && round.isEmpty()) message = "That is tonight done."
                load(context)
            }
        }
    }

    /** Excused rather than owed, for an app nobody in the crew can get into. */
    fun report(
        context: Context,
        app: CrewApp,
    ) = call(
        context = context,
        onDone = { message = "Reported. It stops counting against you from today." },
    ) { Api.verdict(app.id, ok = false, reason = "Could not open it during the run.") }

    /**
     * One call, one reload, one place failure is handled.
     *
     * Reloaded rather than patched in memory: the server's answer changes more than the one row
     * that was touched, since a verdict can fill a crew and filling one starts a fortnight.
     */
    private fun <T> call(
        context: Context? = null,
        onDone: (T) -> Unit = {},
        block: suspend () -> T,
    ) {
        viewModelScope.launch {
            try {
                onDone(block())
                context?.let { load(it) } ?: reload()
            } catch (e: ApiError) {
                message = e.message
            } catch (_: Exception) {
                message = "We could not reach TestTrack. Check your connection."
            }
        }
    }

    private suspend fun reload() {
        runCatching { apply(Api.crews()) }
    }
}
