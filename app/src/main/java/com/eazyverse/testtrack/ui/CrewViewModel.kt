package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.Device
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.SeatIn
import com.eazyverse.testtrack.viewmodel.LiveReload
import com.eazyverse.testtrack.viewmodel.NightRound
import com.eazyverse.testtrack.viewmodel.ReviewMoment
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

    /** Which load is the latest: an older answer arriving after a newer one is dropped. */
    private var loads = 0

    /** A load is on its way, first or not. A crew's page waits for it before calling a crew gone. */
    var reloading by mutableStateOf(false)
        private set

    /** Set by [preview]: the crews were put in by hand, and asking the server would replace them. */
    private var previewing = false

    /** Set by [preview]: which sample packages count as on this phone. */
    private var previewInstalled: Set<String>? = null

    /**
     * Whether an app is on this phone. Read during drawing, it subscribes to the installed-apps
     * cache, so a row redraws when [refreshInstalled] finds the app arrived.
     */
    fun isInstalled(
        context: Context,
        pkg: String,
    ): Boolean = previewInstalled?.contains(pkg) ?: (pkg.isNotBlank() && InstalledApps.cachedInfo(context, pkg).installed)

    /** Re-reads the installed apps, on the way back from Play. */
    fun refreshInstalled(context: Context) {
        if (previewInstalled == null) InstalledApps.refresh(context)
    }

    /**
     * Crews put in directly, for the screen gallery in androidTest. Nothing in the app calls this.
     */
    @VisibleForTesting
    internal fun preview(
        mine: List<Crew>,
        open: Crew? = null,
        blocked: String? = null,
        signedOut: Boolean = false,
        tab: CrewPhase = CrewPhase.OPEN,
        installed: Set<String> = emptySet(),
    ) {
        previewing = true
        previewInstalled = installed
        crews = mine
        this.open = open
        this.blocked = blocked
        this.signedOut = signedOut
        this.tab = tab
        opened = null
        loading = false
        offline = false
    }

    /** Everything the tab shows, in one call. Safe to run on every resume. */
    fun load(context: Context) {
        if (previewing) return
        val mine = ++loads
        reloading = true
        viewModelScope.launch {
            try {
                // No guard. The crew list reads without an account and answers a stranger with
                // the one that is filling and its seat count, which is the only evidence anywhere
                // in the app that there are real people waiting to test each other's work.
                signedOut = !ApiAuth.ensure(context)
                val answer = Api.crews()
                // Asked before a seat was taken and answered after: it would take the new crew
                // away again, and a crew's page reads that as the crew being gone.
                if (mine != loads) return@launch
                apply(answer)
                // A test cycle completed is TestTrack's value moment; only fresh answers count.
                ReviewMoment.noteFinished(context, crews.filter { it.phase == CrewPhase.RECORD && !it.out }.map { it.id }.toSet())
                offline = false
            } catch (e: ApiError) {
                message = e.message
                offline = true
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
                if (mine == loads) reloading = false
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
        appId: String? = null,
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
                            appId = appId,
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
                MyAppsChanged.tell()
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
     * Tonight's open: go to the app, stay a moment, come back.
     *
     * The whole obligation is opening somebody's app, so this launches it, through the round:
     * with the testing control floating over it when "display over other apps" is allowed.
     */
    fun openApp(
        context: Context,
        app: CrewApp,
    ) = round(context, listOf(app))

    /**
     * Walk the whole night, one app at a time: only what is on this phone, since the rest is
     * installed first from its own row.
     */
    fun openAll(
        context: Context,
        crewId: String? = null,
    ) = round(
        context,
        crews
            .filter { crewId == null || it.id == crewId }
            .flatMap { crew -> crew.apps.filter { !it.done && !it.reported } }
            .filter { isInstalled(context, it.packageName) },
    )

    /** A round asked for while the control may not float yet: started once the ask is answered. */
    var needsOverlayFor by mutableStateOf<List<RoundStore.Stop>?>(null)
        private set

    /** Asked once a session: somebody who said Not now is not asked at every app. */
    private var overlayAsked = false

    private fun round(
        context: Context,
        apps: List<CrewApp>,
    ) {
        val first = apps.firstOrNull() ?: return
        if (apps.any { it.packageName.isBlank() }) {
            message = "${first.label} can't be opened from here. Open it from your phone's app list instead."
            return
        }
        // Asked at the first app somebody opens: without it the phone reads no time in the app,
        // the server refuses the visit, and the evening silently does not count.
        if (!UsageRepo.hasAccess(context)) {
            needsUsageFor = first.label
            return
        }
        val stops =
            apps.map { app ->
                val notes = crews.firstNotNullOfOrNull { c -> c.seats.firstOrNull { it.id == app.id } }?.notes.orEmpty()
                RoundStore.Stop(app.id, app.packageName, app.label, notes)
            }
        if (!NightRound.canFloat(context) && !overlayAsked) {
            overlayAsked = true
            needsOverlayFor = stops
            return
        }
        NightRound.start(context, stops, float = true)
    }

    /** Both switches a round needs: usage access for the proof, drawing over apps for the timer. */
    fun permissionsReady(context: Context) = UsageRepo.hasAccess(context) && NightRound.canFloat(context)

    /** The guide's Allow: whichever of the two is still off, usage access first. */
    fun allowPermissions(activity: Activity) {
        if (!UsageRepo.hasAccess(activity)) UsageRepo.openSettings(activity) else allowOverlay(activity)
    }

    /** "Allow": to Android's switch; the round starts when TestTrack is back in front. */
    fun allowOverlay(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** "Not now": the round runs without the control, coming back to TestTrack between apps. */
    fun skipOverlay(context: Context) {
        val stops = needsOverlayFor ?: return
        needsOverlayFor = null
        NightRound.start(context, stops, float = false)
    }

    /**
     * Back in front. A round waiting on the overlay switch starts now, either way; otherwise the
     * app last visited is measured and recorded, and the round says how it went.
     */
    fun settle(context: Context) {
        needsOverlayFor?.let { stops ->
            needsOverlayFor = null
            NightRound.start(context, stops, float = NightRound.canFloat(context))
            return
        }
        NightRound.onTestTrackInFront(context) { said ->
            said?.let { message = it }
            load(context)
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
        onDone = {
            message = "Taken back out, and its ${CrewRules.SEAT_COST} tix are back. You can put an app in again."
            MyAppsChanged.tell()
        },
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
