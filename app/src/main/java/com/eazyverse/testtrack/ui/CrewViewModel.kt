package com.eazyverse.testtrack.ui

import android.app.Activity
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
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.SeatIn
import com.eazyverse.testtrack.viewmodel.LiveReload
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * The crew tab, against the API.
 *
 * Every rule here belongs to the server and none of it is decided twice. Which crew a seat goes
 * into, whether an app is on Play, what a report does, whose day it is: the phone asks and redraws
 * whatever comes back. A client that works out for itself when a crew is full is a client
 * somebody will edit.
 *
 * The shape that matters is [crews] being a list. A person is in several at once, so nothing here
 * is about "the crew": the Run tab is built out of [tonight], which is every crew's work in one
 * column, and a single crew is something you open rather than something you are in.
 */
class CrewViewModel : ViewModel() {
    /** Every crew this account is in, at whatever point each has reached. */
    var crews by mutableStateOf<List<Crew>>(emptyList())
        private set

    /**
     * The crew a new seat would go into, or null when none is filling and the next seat starts
     * one. Which crew it is stays the server's answer: it sends back whichever is nearest to
     * starting, so the crew that most needs finishing is the one that fills.
     */
    var open by mutableStateOf<Crew?>(null)
        private set

    /** A seat is being taken: the server is looking the app up on Play, which takes a while. */
    var seating by mutableStateOf(false)
        private set

    /** Why the last seat was not taken, said inside the dialog that asked. */
    var seatError by mutableStateOf<String?>(null)
        private set

    fun clearSeatError() {
        seatError = null
    }

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

    /** The first answer has not come yet, so the tabs show where it will go rather than nothing. */
    val firstLoad: Boolean
        get() = DebugLooks.holdLoading || (loading && crews.isEmpty() && open == null)

    /** Set once the API has refused us for good, so the screen can say so rather than spin. */
    var offline by mutableStateOf(false)
        private set

    /**
     * Nobody is signed in, which is not the same as being in no crews.
     *
     * Open answers a stranger and the other three tabs cannot: which crews are yours is a
     * question about an account. Without this the three of them said "Nothing running" and the
     * rest, which is the app reporting a result it never got.
     */
    var signedOut by mutableStateOf(false)
        private set

    /** The server's reason a seat cannot be taken, shown as it arrives. */
    var blocked by mutableStateOf<String?>(null)
        private set

    /**
     * The app somebody tried to open without usage access, or null.
     *
     * Holds the label rather than a boolean so the ask can name what it is for. Set at the point
     * of the first open and cleared either way, so it is a question asked once about a thing they
     * were actually doing, not a banner that lives on the screen.
     */
    var needsUsageFor by mutableStateOf<String?>(null)
        private set

    fun clearUsageAsk() {
        needsUsageFor = null
    }

    /**
     * Send them to the switch.
     *
     * Here rather than in the screen so the screen does not have to reach into data for it. This
     * file already talks to UsageRepo to take the measurement, so the pair of it lives together.
     */
    fun openUsageSettings(activity: Activity) {
        needsUsageFor = null
        UsageRepo.openSettings(activity)
    }

    private var settledTab = false

    fun clearMessage() {
        message = null
    }

    /** Say something once, from an action defined beside this class. */
    internal fun say(text: String) {
        message = text
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
                // No guard. The crew list reads without an account and answers a stranger with
                // the one that is filling and its seat count, which is the only evidence anywhere
                // in the app that there are real people waiting to test each other's work.
                signedOut = !ApiAuth.ensure(context)
                apply(Api.crews())
                offline = false
            } catch (e: ApiError) {
                message = e.message
                offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
            }
        }
    }

    private fun apply(answer: com.eazyverse.testtrack.net.CrewList) {
        crews = answer.mine.map { it.toCrew() }
        open = answer.open?.toCrew(member = false)
        blocked = answer.blocked
        live.channels(crews.map { "crew:${it.id}" }.toSet())

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
            CrewPhase.LOBBY -> sentBack.size + toAnswer
            CrewPhase.OPEN -> 0
            CrewPhase.RECORD -> 0
        }

    /** The crew being looked at. Only ever one of yours. */
    val current: Crew?
        get() = opened?.let { id -> crews.firstOrNull { it.id == id } }

    /** Whether the open crew is one you already have an app in. */
    val inOpen: Boolean
        get() = open?.let { o -> crews.any { it.id == o.id && !it.out } } == true

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

    /** Apps waiting on members that this account may answer for and has not. */
    val toAnswer: Int
        get() = crews.filter { it.mayVote }.sumOf { c -> c.seats.count { it.state == SeatState.CHECKING && !it.mine && it.myVote == null } }

    /** Your apps sent back before a run, to fix and put in again. */
    val sentBack: List<CrewSeat>
        get() = sentBackIn(crews)

    /**
     * Whether this account holds a seat anywhere that has not started.
     *
     * The server decides this and sends the reason; the flag is only what the screen branches on.
     */
    val holdsSeat: Boolean
        get() = blocked != null

    /** The crew holding your one waiting app, by name, or null where you are free. */
    val holding: String?
        get() = holdingIn(crews)

    /** How many runs are going, which is what the nightly figure is made of. */
    val running: Int
        get() = crews.count { it.phase == CrewPhase.RUN }

    /**
     * Take a seat with an app of your own.
     *
     * The crew is not named. The server checks the app on Play for the testers group and puts it
     * into whichever crew is nearest to starting; a client that picks its own crew is a client
     * picking which fourteen people test its app. The dialog stays up while that happens, and a
     * refusal is said inside it, where the fix is: usually the testers group missing from the
     * track.
     */
    fun takeSeat(
        context: Context,
        packageName: String,
        appName: String,
        notes: String,
        onDone: () -> Unit,
    ) {
        val fingerprint = Device.fingerprint(context)
        if (fingerprint == null) {
            seatError = "This phone will not identify itself, so it cannot hold a seat."
            return
        }
        if (seating) return
        seating = true
        seatError = null
        viewModelScope.launch {
            try {
                val seat =
                    Api.takeSeat(
                        SeatIn(
                            appName = appName,
                            packageName = packageName,
                            notes = notes,
                            deviceFp = fingerprint,
                            timezone = TimeZone.getDefault().id,
                        ),
                    )
                message =
                    if (seat.state == "confirmed") {
                        "${seat.appName} is in. Play shows it to the testers group."
                    } else {
                        "${seat.appName} is waiting. We could not reach Play just now and will " +
                            "check it again shortly."
                    }
                opened = null
                tab = CrewPhase.LOBBY
                onDone()
                load(context)
            } catch (e: ApiError) {
                seatError = e.message
            } catch (_: Exception) {
                seatError = "We could not reach TestTrack. Check your connection."
            } finally {
                seating = false
            }
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
        // Asked for here, at the first app somebody tries to open, rather than on a checklist
        // they walked before they had seen a crew. Without the grant [settle] reads zero
        // foreground milliseconds, the server refuses the visit for being under its floor, and
        // the evening silently does not count. A permission whose absence looks like a bug
        // belongs at the moment it is needed, where the reason for it is on screen.
        if (!UsageRepo.hasAccess(context)) {
            round = emptyList()
            needsUsageFor = app.label
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
            val used =
                UsageRepo.measure(
                    context,
                    trip.packageName,
                    trip.since,
                    needMs = CrewRules.VISIT_SECONDS * 1000L,
                    source = "crew",
                )
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

    /**
     * Give your seat up before the crew starts.
     *
     * Only possible before it fills. The server refuses it afterwards, because by then fourteen
     * other people have committed a fortnight on the strength of it being there.
     */
    fun leave(
        context: Context,
        seat: CrewSeat,
    ) = call(
        context = context,
        onDone = { message = "Taken back out. You can put an app in again." },
    ) { Api.withdraw(seat.id) }

    /**
     * One call, one reload, one place failure is handled.
     *
     * Reloaded rather than patched in memory: the server's answer changes more than the one row
     * that was touched, since a report can send an app back and reopen a crew.
     */
    internal fun <T> call(
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

    // Your crews' own channels, and the two events about your seats that come on yours.
    private val live =
        LiveReload("crew", viewModelScope) {
            it.channel.startsWith("crew:") || it.type == "seat.mine" || it.type == "visit.recorded"
        }

    // The kept crews, before the first frame. With none kept the tabs wait for the server rather
    // than say "nothing running" from memory.
    init {
        Api.lastCrews()?.takeIf { it.mine.isNotEmpty() }?.let {
            apply(it)
            loading = false
        }
    }

    /** Redraw when anything in your crews changes while showing; a burst of events is one fetch. */
    fun startLive(context: Context) {
        if (!signedOut) live.start { load(context) }
    }

    fun stopLive() = live.stop()

    private suspend fun reload() {
        runCatching { apply(Api.crews()) }
    }
}
