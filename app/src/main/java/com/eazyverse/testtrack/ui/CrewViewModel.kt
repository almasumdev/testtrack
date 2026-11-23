package com.eazyverse.testtrack.ui

import android.app.Activity
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.domain.AppNight
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.viewmodel.CrewRecords
import com.eazyverse.testtrack.viewmodel.LiveReload
import com.eazyverse.testtrack.viewmodel.NightPictures
import com.eazyverse.testtrack.viewmodel.NightRound
import com.eazyverse.testtrack.viewmodel.ReviewMoment
import com.eazyverse.testtrack.viewmodel.UninstallRun
import kotlinx.coroutines.launch

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

    /** What the server last said, before any picture that has landed since is marked on it. */
    private var served: List<Crew> = emptyList()

    /** The nights whose picture has reached the server, as last seen: marked opened on [crews]. */
    private var landed: Set<AppNight> = emptySet()

    private fun showCrews(list: List<Crew>) {
        served = list
        crews = withLandedPictures(list, landed)
    }

    /**
     * The crew a new seat would go into, or null when none is filling and the next seat starts
     * one. Which crew it is stays the server's answer: it sends back whichever is nearest to
     * starting, so the crew that most needs finishing is the one that fills.
     */
    var open by mutableStateOf<Crew?>(null)
        private set

    /** A seat is being taken: the server is looking the app up on Play, which takes a while. */
    var seating by mutableStateOf(false)
        internal set

    /** Why the last seat was not taken, said inside the dialog that asked. */
    var seatError by mutableStateOf<String?>(null)
        internal set

    fun clearSeatError() {
        seatError = null
    }

    /** Which tab is showing, whether it is known yet, and whether it moved like a tap: see [CrewTabState]. */
    private val tabs = CrewTabState(viewModelScope)
    val tab: CrewPhase get() = tabs.tab
    val tabKnown: Boolean get() = tabs.known
    val tabByHand: Boolean get() = tabs.byHand

    /** A visit to the Crews tab: a new one opens on Open, and may slide to Run once. */
    fun arrive(visit: String) = tabs.arrive(visit, fixed = previewing)

    fun leave() = tabs.leave()

    /** A touch on the tab: a slide to Run still waiting does not come. */
    fun holdTab() = tabs.hold()

    /** Which crew's own page is showing, or null for the tab itself. */
    var opened by mutableStateOf<String?>(null)
        internal set

    /** The Record tab's kept records, read with every load, and the two answers they ask. */
    val records = CrewRecords(viewModelScope)

    /** Uninstall all on a finished crew's page; its run outlives the page being left and opened again. */
    val uninstall = UninstallRun(viewModelScope)

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

    /** The server's reason a seat cannot be taken, shown as it arrives, and the crew the waiting app is in (null from an older server). */
    var blocked by mutableStateOf<String?>(null)
        private set
    var blockedCrew by mutableStateOf<String?>(null)
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
     * Open the usage switch here so the screen does not have to reach into data for it. This
     * file already talks to UsageRepo to take the measurement, so the pair of it lives together.
     */
    fun openUsageSettings(activity: Activity) {
        needsUsageFor = null
        UsageRepo.openSettings(activity)
    }

    /** Say something once, in a snack, from an action defined beside this class. */
    internal fun say(
        text: String,
        tone: SnackTone = SnackTone.INFO,
    ) = Snacks.show(Snack(text, tone))

    fun show(id: String?) {
        opened = id
    }

    /**
     * Take a crew out of the list at once, ahead of the reload that confirms it: a crew's page open
     * on it closes straight away rather than showing what was just left until the server answers.
     * A load already on its way was asked before the crew was left and would bring it back, so it
     * no longer counts; the reload after the call is the one that does.
     */
    internal fun forget(crewId: String) {
        loads++
        reloading = false
        showCrews(served.filterNot { it.id == crewId })
    }

    fun showTab(phase: CrewPhase) {
        tabs.show(phase)
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

    /** Install, source and version: cached device facts unless a display preview overrides them. */
    fun copyOf(
        context: Context,
        pkg: String,
    ): HereCopy {
        previewInstalled?.let { return HereCopy(pkg in it) }
        DebugLooks.crewDemoCopy(pkg)?.let { return it }
        if (pkg.isBlank()) return HereCopy(false)
        val info = InstalledApps.cachedInfo(context, pkg)
        return HereCopy(info.installed, info.installer == "com.android.vending", info.versionCode.takeIf { info.installed })
    }

    /** Re-reads the installed apps, on the way back from Play. */
    fun refreshInstalled(context: Context) {
        if (previewInstalled == null) InstalledApps.refresh(context)
    }

    /** Crews put in directly, for the screen gallery in androidTest. Nothing in the app calls this. */
    @VisibleForTesting
    internal fun preview(
        mine: List<Crew>,
        open: Crew? = null,
        blocked: String? = null,
        blockedCrew: String? = null,
        signedOut: Boolean = false,
        tab: CrewPhase = CrewPhase.OPEN,
        installed: Set<String> = emptySet(),
        records: List<CrewRecord> = emptyList(),
    ) {
        this.records.preview(records)
        previewing = true
        previewInstalled = installed
        showCrews(mine)
        this.open = open
        this.blocked = blocked
        this.blockedCrew = blockedCrew
        this.signedOut = signedOut
        tabs.preview(tab)
        opened = null
        loading = false
        offline = false
    }

    /**
     * Everything the tab shows, in one call. Safe to run on every resume. [then] runs once a fresh
     * answer is in; [settled] runs once the load is over, whatever came of it.
     */
    fun load(
        context: Context,
        then: () -> Unit = {},
        settled: () -> Unit = {},
    ) {
        if (previewing) {
            settled()
            return
        }
        val mine = ++loads
        reloading = true
        viewModelScope.launch {
            try {
                // No guard. The crew list reads without an account and answers a stranger with
                // the one that is filling and its seat count, which is the only evidence anywhere
                // in the app that there are real people waiting to test each other's work.
                signedOut = !ApiAuth.ensure(context)
                records.load(signedOut)
                val answer = Api.crews()
                // Asked before a seat was taken and answered after: it would take the new crew
                // away again, and a crew's page reads that as the crew being gone.
                if (mine != loads) return@launch
                apply(answer)
                then()
                // A test cycle completed is TestTrack's value moment; only fresh answers count.
                ReviewMoment.noteFinished(context, crews.filter { it.phase == CrewPhase.RECORD && !it.out }.map { it.id }.toSet())
                offline = false
            } catch (e: ApiError) {
                // An older load's failure says nothing about the newer one's answer.
                if (mine != loads) return@launch
                Snacks.error(e.message)
                offline = true
            } catch (e: Exception) {
                if (mine != loads) return@launch
                offline = true
                // No connection and nothing shown yet: the kept crews stand in. Not for a server fault.
                if (e is java.io.IOException && crews.isEmpty() && open == null) seedFromPhone(afterFailure = true)
            } finally {
                if (mine == loads) {
                    // Answered or not, the first load after arriving has had its say on the tab.
                    tabs.loaded()
                    loading = false
                    reloading = false
                }
                settled()
            }
        }
    }

    private fun apply(answer: com.eazyverse.testtrack.net.CrewList) {
        // A volunteer spot the reader left is over by their own choice: it lives in the Record tab's
        // volunteering list, not here. A newer server leaves it out already; an older one sends it.
        showCrews(crewsOf(answer))
        open = answer.open?.toCrew(member = false)
        blocked = answer.blocked
        blockedCrew = answer.blockedCrew
        live.channels(crews.map { "crew:${it.id}" }.toSet())

        // Only the first answer after arriving may move the tab, and only from Open to Run.
        tabs.answered(crews)
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
     * Walk the whole night, one app at a time: only what is on this phone and will count, since
     * the rest is installed, updated or installed from Play first, from its own row.
     */
    fun openAll(
        context: Context,
        crewId: String? = null,
    ) = round(
        context,
        openAllOf(
            crews.filter { crewId == null || it.id == crewId }.flatMap { it.apps },
            copy = { pkg -> copyOf(context, pkg) },
            sendingTonight = { pictures.sending(it.id, it.day) },
        ),
    )

    /** The seat a call is running for, so its row shows it busy and a second tap sends nothing. */
    var acting by mutableStateOf<String?>(null)
        private set

    /** A round asked for while the control may not float yet: started once the ask is answered. */
    var needsOverlayFor by mutableStateOf<List<RoundStore.Stop>?>(null)
        private set

    /** Asked once a session: somebody who said Not now is not asked at every app. */
    private var overlayAsked = false

    /** What a round needs before it starts: tonight's screen-share yes. */
    val pictures = NightPictures(viewModelScope, reload = { load(it) })

    private fun round(
        context: Context,
        apps: List<CrewApp>,
    ) {
        val first = apps.firstOrNull() ?: return
        DebugLooks.crewDemoRoundMessage(apps)?.let {
            say(it)
            return
        }
        if (apps.any { it.packageName.isBlank() }) {
            say("${first.label} can't open from here. Use your phone's app list.")
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
                val notes = crews.firstNotNullOfOrNull { c -> c.seats.firstOrNull { it.id == app.id } }?.notes ?: app.notes
                RoundStore.Stop(app.id, app.packageName, app.label, notes, day = app.day, privateTestId = app.privateTestId)
            }
        if (!NightRound.canFloat(context) && !overlayAsked) {
            overlayAsked = true
            needsOverlayFor = stops
            return
        }
        pictures.ask(stops, float = true, context)
    }

    /** "Not now": the round runs without the control, coming back to TestTrack between apps. */
    fun skipOverlay(context: Context) {
        val stops = needsOverlayFor ?: return
        needsOverlayFor = null
        pictures.ask(stops, float = false, context)
    }

    /**
     * Back in front. A round waiting on the overlay switch starts now, either way; otherwise the
     * app last visited is measured and recorded, and the round says how it went.
     */
    fun settle(context: Context) {
        needsOverlayFor?.let { stops ->
            needsOverlayFor = null
            pictures.ask(stops, float = NightRound.canFloat(context), context)
            return
        }
        NightRound.onTestTrackInFront(context) { said ->
            said?.let(Snacks::show)
            load(context)
        }
        (context as? Activity)?.let(pictures::send) // What could not go yet.
    }

    /**
     * One call, one reload, one place failure is handled.
     *
     * Reloaded rather than patched in memory: the server's answer changes more than the one row
     * that was touched, since a report can send an app back and reopen a crew.
     */
    internal fun <T> call(
        context: Context? = null,
        /** The seat this is for. One call at a time: another tap while it runs is dropped. */
        seat: String? = null,
        onDone: (T) -> Unit = {},
        /** Say the result only once the fresh answer is in, so the snack and the row agree. The seat stays busy till then. */
        holdTillRead: Boolean = false,
        block: suspend () -> T,
    ) {
        if (acting != null) {
            Snacks.info("Still sending. Try again in a moment.")
            return
        }
        acting = seat
        viewModelScope.launch {
            var handed = false
            try {
                val result = block()
                if (holdTillRead && context != null) {
                    handed = true
                    load(context, settled = {
                        acting = null
                        onDone(result)
                    })
                } else {
                    onDone(result)
                    context?.let { load(it) } ?: reload()
                }
            } catch (e: ApiError) {
                Snacks.error(e.message)
            } catch (_: Exception) {
                Snacks.error("Couldn't reach TestTrack. Check your connection.") { call(context, seat, onDone, holdTillRead, block) }
            } finally {
                if (!handed) acting = null
            }
        }
    }

    // Your crews' own channels, and the two events about your seats that come on yours.
    private val live =
        LiveReload("crew", viewModelScope) {
            it.channel.startsWith("crew:") || it.type in setOf("seat.mine", "visit.recorded", "volunteer.changed", "private.changed")
        }

    // The kept crews, only without a connection; online the tabs shimmer until the server answers.
    // With none kept the tabs wait for the server rather than say "nothing running" from memory.
    private fun seedFromPhone(afterFailure: Boolean = false) {
        Api.lastCrews(afterFailure)?.takeIf { it.mine.isNotEmpty() }?.let {
            apply(it)
            loading = false
        }
    }

    init {
        seedFromPhone()
        // A picture reaching the server settles its row at once, and a quiet read follows to confirm it.
        viewModelScope.launch {
            pictures.landed.collect { now ->
                val grew = !landed.containsAll(now)
                landed = now
                crews = withLandedPictures(served, now)
                if (grew && !previewing && served.isNotEmpty()) viewModelScope.launch { reload() }
            }
        }
    }

    /** Redraw when anything in your crews changes while showing; a burst of events is one fetch. */
    fun startLive(context: Context) {
        if (!signedOut) live.start { load(context) }
    }

    fun stopLive() = live.stop()

    /**
     * A quiet [load] after a call made without a context: the same newest-wins count, but no
     * [reloading], so a crew's page that was just left closes at once rather than waiting.
     */
    private suspend fun reload() {
        val mine = ++loads
        try {
            runCatching { Api.crews() }.onSuccess { if (mine == loads) apply(it) }
        } finally {
            if (mine == loads) {
                tabs.loaded()
                loading = false
                reloading = false
            }
        }
    }
}

/** Your crews out of an answer, without any volunteer spot the reader left. */
internal fun crewsOf(answer: com.eazyverse.testtrack.net.CrewList): List<Crew> =
    answer.mine.map { it.toCrew() } +
        answer.volunteering.map { it.toVolunteerCrew() }.filter { it.volunteer?.state != "left" }
