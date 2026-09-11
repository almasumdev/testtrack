package com.eazyverse.testtrack.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/**
 * The crew tab's state, held in memory and nowhere else.
 *
 * Every action below is the local half of something a backend will do: taking a seat, giving a
 * verdict, opening an app for the night. They are written as if the answer came back instantly and
 * always succeeded, which is exactly what an MVP wants and exactly what a real one must not
 * assume. When there is a backend each becomes a call and a result, and the screens do not change.
 *
 * The shape that matters is [crews] being a list. A person is in several at once, so nothing here
 * is about "the crew": the tab's own screen is built out of [tonight] and [waiting], which are
 * every crew's work in one column, and a single crew is something you open rather than something
 * you are in.
 */
class CrewViewModel : ViewModel() {
    /** Every crew this account is in, at whatever point each has reached. */
    var crews by mutableStateOf(SampleCrew.mine())
        private set

    /**
     * The crews taking seats, nearest to starting first.
     *
     * A list rather than one, and ordered rather than chosen from at random: the crew with the
     * fewest free seats starts soonest, so putting it at the top sends people to the one that most
     * needs finishing. That ordering is what stops several open crews all filling slowly, which is
     * the reason there used to be only one.
     */
    val opens: List<Crew>
        get() = openCrews.sortedBy { CrewRules.SEATS - it.seats.size }

    private var openCrews by mutableStateOf(SampleCrew.opens())

    /**
     * Which tab is showing.
     *
     * Opened on whichever has something owed rather than always on the same one: a run in progress
     * beats a lobby, a lobby beats a record, and somebody in no crews at all is shown the way in.
     */
    var tab by mutableStateOf(CrewPhase.RUN)
        private set

    /** Which crew's own page is showing, or null for the tab itself. */
    var opened by mutableStateOf<String?>(null)
        private set

    /** A one line result of the last action, shown once and cleared. */
    var message by mutableStateOf<String?>(null)
        private set

    init {
        tab =
            when {
                crews.any { it.phase == CrewPhase.RUN } -> CrewPhase.RUN
                crews.any { it.phase == CrewPhase.LOBBY } -> CrewPhase.LOBBY
                crews.any { it.phase == CrewPhase.RECORD } -> CrewPhase.RECORD
                else -> CrewPhase.OPEN
            }
    }

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

    /** What the pill for a tab counts, or zero where the tab has nothing outstanding. */
    fun badge(phase: CrewPhase) =
        when (phase) {
            CrewPhase.RUN -> tonight.size
            CrewPhase.LOBBY -> waiting.size
            CrewPhase.OPEN -> 0
            CrewPhase.RECORD -> 0
        }

    /** Which crew a seat belongs to, since a merged list only carries the crew's name. */
    fun crewIdOf(seat: CrewSeat) = crews.first { crew -> crew.seats.any { it.id == seat.id } }.id

    /** The crew being looked at, which may be the open one this account has not joined. */
    val current: Crew?
        get() = opened?.let { id -> crews.firstOrNull { it.id == id } }

    /**
     * Everything owed tonight, across every running crew, newest crew first.
     *
     * One column rather than one per crew. Which crew an app belongs to is not the reason anybody
     * opens it, and a member with two runs going has one evening, not two.
     */
    val tonight: List<CrewApp>
        get() =
            crews
                .filter { it.phase == CrewPhase.RUN }
                .flatMap { crew -> crew.apps.filter { !it.done && !it.reported } }

    /** Everything settled tonight, for the count under the list. */
    val doneTonight: Int
        get() =
            crews
                .filter { it.phase == CrewPhase.RUN }
                .sumOf { crew -> crew.apps.count { it.done || it.reported } }

    /** Seats in any filling crew this account has not given a verdict on. */
    val waiting: List<CrewSeat>
        get() =
            crews
                .filter { it.phase == CrewPhase.LOBBY }
                .flatMap { crew ->
                    crew.seats.filter { !it.judged && !it.mine && it.state == SeatState.CHECKING }
                }

    /**
     * Whether this account holds a seat anywhere at all.
     *
     * One seat across every open crew, not one per crew. Somebody who has just joined has nothing
     * to do in this tab but wait, and offering them a second seat in the crew next to it is
     * offering them fourteen more apps a night before the first fourteen have started.
     */
    val holdsSeat: Boolean
        get() = holding != null

    /** The crew holding your one seat, by name, or null where you are free to take one. */
    val holding: String?
        get() =
            crews
                .firstOrNull { crew ->
                    crew.phase == CrewPhase.LOBBY && crew.seats.any { it.mine }
                }?.name

    /** How many runs are going, which is what the nightly figure is made of. */
    val running: Int
        get() = crews.count { it.phase == CrewPhase.RUN }

    /**
     * Whether this account may reject anything in a given crew.
     *
     * A red only counts from somebody who has already got one app working in that same crew,
     * because a Play "not found" far more often means the reader is not set up than that the app
     * is broken. One green is the proof that their own side works. See docs/crew.md.
     */
    fun mayJudge(crewId: String) = crews.firstOrNull { it.id == crewId }?.seats?.any { it.judged && !it.mine } == true

    private fun replace(
        crewId: String,
        change: (Crew) -> Crew,
    ) {
        crews = crews.map { if (it.id == crewId) change(it) else it }
        openCrews = openCrews.map { if (it.id == crewId) change(it) else it }
    }

    /** Say whether an app installed. */
    fun judge(
        crewId: String,
        seat: CrewSeat,
        installed: Boolean,
    ) {
        if (!installed && !mayJudge(crewId)) {
            message = "Get one app in ${seat.crew} installed first, then your reports count."
            return
        }
        replace(crewId) { crew ->
            crew.copy(
                seats =
                    crew.seats.map {
                        if (it.id != seat.id) {
                            it
                        } else {
                            it.copy(
                                greens = it.greens + if (installed) 1 else 0,
                                reds = it.reds + if (installed) 0 else 1,
                                judged = true,
                            )
                        }
                    },
            )
        }
        // Standing in for the rest of the crew, who in a real one would be judging your app while
        // you judge theirs. Once you have nothing left to look at there, their verdicts land on
        // yours, which is the only way to reach the countdown by hand.
        if (waiting.none { it.crew == seat.crew }) {
            replace(crewId) { crew ->
                crew.copy(
                    seats =
                        crew.seats.map {
                            if (it.mine && it.state == SeatState.CHECKING) {
                                it.copy(greens = CrewRules.VERDICTS)
                            } else {
                                it
                            }
                        },
                )
            }
        }

        val now = crews.firstOrNull { it.id == crewId }?.seats?.firstOrNull { it.id == seat.id }
        message =
            when (now?.state) {
                SeatState.CONFIRMED -> "${seat.appName} is in. Seat confirmed."
                SeatState.REJECTED -> "${seat.appName} is out. ${seat.owner} has been told why."
                else ->
                    if (installed) {
                        "Green sent. One more and ${seat.appName} is in."
                    } else {
                        "Reported. Somebody else will try it before anything happens."
                    }
            }
    }

    /**
     * Take a seat in the open crew with an app of your own.
     *
     * Nothing refuses this on the grounds of being in too many already, which is the decision the
     * product made rather than an omission. What the screen owes in exchange is the arithmetic,
     * said before the seat is taken rather than discovered a fortnight later.
     */
    fun takeSeat(
        crewId: String,
        appName: String,
    ) {
        val crew = openCrews.firstOrNull { it.id == crewId } ?: return
        val seat =
            CrewSeat(
                id = "seat-mine-$crewId",
                appName = appName,
                owner = "You",
                crew = crew.name,
                takenAt = System.currentTimeMillis(),
                mine = true,
            )
        // Still the crew taking seats. It closes when it is full, not when you join it.
        val joined = crew.copy(seats = listOf(seat) + crew.seats)
        openCrews = openCrews.map { if (it.id == crewId) joined else it }
        crews = listOf(joined.copy(phase = CrewPhase.LOBBY)) + crews
        opened = null
        tab = CrewPhase.LOBBY
        message = "Seat taken in ${crew.name}. Two members have to install it before it counts."
    }

    /**
     * Open everything still owed, in one press.
     *
     * The night without the fourteen taps. TestTrack already knows how to launch an app, time the
     * visit and come back, which is what [CaptureService] does, so asking somebody to do that by
     * hand fourteen times a night is a choice rather than a limit. Here it simply marks them,
     * because there is no service behind this screen yet; when there is, this becomes the call
     * that starts it and nothing above changes.
     */
    fun openAll(crewId: String? = null) {
        val target = crews.filter { crewId == null || it.id == crewId }
        if (target.none { it.owed > 0 }) return
        crews =
            crews.map { crew ->
                if (crewId != null && crew.id != crewId) {
                    crew
                } else {
                    crew.copy(apps = crew.apps.map { if (it.reported) it else it.copy(done = true) })
                }
            }
        message = "Opened everything owed tonight."
    }

    /** Tonight's tap: the app was opened and the evening is one app shorter. */
    fun openApp(app: CrewApp) {
        crews =
            crews.map { crew ->
                crew.copy(apps = crew.apps.map { if (it.id == app.id) it.copy(done = true) else it })
            }
    }

    /** Excused rather than owed, for an app nobody in the crew can get into. */
    fun report(app: CrewApp) {
        crews =
            crews.map { crew ->
                crew.copy(
                    apps = crew.apps.map { if (it.id == app.id) it.copy(reported = true) else it },
                )
            }
        message = "Reported. It stops counting against you from today."
    }
}
