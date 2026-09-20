package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiSeat

/*
 * The server's crews, in the shape the screens already speak.
 *
 * A translation layer rather than a rewrite. The crew screens were designed and agreed against
 * the types in CrewModels.kt, and those types say what the screen is about; the wire types say
 * what the database holds. Mapping between them here keeps one change from becoming two, and
 * means every screen stayed exactly as it was drawn.
 *
 * Pure functions over values, so all of this is tested without a device, a network or an account.
 */

/**
 * Which of the four screens a crew belongs on.
 *
 * The server has three states and the app has four, because "full" is two different situations
 * for the person looking at it: a crew that has filled but whose own midnight has not arrived is
 * a lobby, and the same crew an hour later is a run. Which one it is depends on the reader, which
 * is why the day comes from the server per member rather than being worked out here.
 */
fun crewPhase(crew: ApiCrew): CrewPhase =
    when {
        crew.state == "done" -> CrewPhase.RECORD
        crew.state == "filling" -> CrewPhase.OPEN
        crew.day >= 1 -> CrewPhase.RUN
        else -> CrewPhase.LOBBY
    }

/** One seat, as the crew's own page lists it. */
fun ApiSeat.toSeat(crewName: String = "") =
    CrewSeat(
        id = id,
        appName = appName,
        owner = ownerName,
        crew = crewName,
        greens = greens,
        reds = reds,
        mine = mine,
        // Answered already, either way, so it is off this member's list of things to do.
        judged = myVerdict != null,
    )

/**
 * One member, as the run screen lists them.
 *
 * [CrewMember.out] is a rejected seat and nothing else. Missing days does not put somebody out of
 * a crew on its own; the run keeps going with whoever is still opening things, and Google's floor
 * of twelve is what decides whether it was worth it.
 */
fun ApiSeat.toMember() =
    CrewMember(
        name = ownerName,
        appName = appName,
        days = daysDone,
        today = openedToday,
        missed = missed,
        mine = mine,
        out = state == "rejected",
    )

/**
 * One app owed tonight.
 *
 * Only confirmed seats, and never your own. An app two people could not install is not something
 * to ask a third to open, and nobody tests their own.
 */
fun ApiSeat.toApp(crewName: String = "") =
    CrewApp(
        id = id,
        label = appName,
        packageName = packageName,
        owner = ownerName,
        crew = crewName,
        done = openedToday,
        // A red from this account is a report, not a refusal to do the work, so the app is
        // excused rather than still owed.
        reported = myVerdict == false,
    )

/**
 * A whole crew, ready for whichever screen it lands on.
 *
 * Fields that mean nothing at this crew's stage stay empty rather than absent, which is how
 * CrewModels.kt is built: one type across all four phases, because the phases are the same crew
 * getting older.
 */
fun ApiCrew.toCrew(): Crew {
    val phase = crewPhase(this)
    val owed = members.filter { !it.mine && it.state == "confirmed" }

    return Crew(
        id = id,
        name = name,
        phase = phase,
        seats = members.map { it.toSeat(name) },
        members = members.map { it.toMember() },
        apps = if (phase == CrewPhase.RUN) owed.map { it.toApp(name) } else emptyList(),
        day = day,
        startsIn = startsInMs,
        // What it came to. Only meaningful once it is over, and the server's own day count is
        // the honest figure: capped at the fortnight rather than however long ago it filled.
        finishedDays = if (phase == CrewPhase.RECORD) minOf(day, runDays) else 0,
    )
}

/** Every seat in every crew this account is in that is still waiting on somebody to try it. */
fun checkable(crews: List<ApiCrew>): List<CrewSeat> =
    crews
        .filter { it.state == "filling" }
        .flatMap { crew ->
            crew.members
                .filter { !it.mine && it.state == "checking" && it.myVerdict == null }
                .map { it.toSeat(crew.name) }
        }

/*
 * What the tab is made of.
 *
 * Kept as functions over a list rather than as properties on the view model so the arithmetic can
 * be tested without a device or an account. The view model holds the list and calls these; there
 * is no second copy of any of it.
 */

/**
 * Everything owed tonight, across every running crew.
 *
 * One column rather than one per crew. Which crew an app belongs to is not the reason anybody
 * opens it, and a member with two runs going has one evening, not two.
 */
fun tonightIn(crews: List<Crew>): List<CrewApp> =
    crews
        .filter { it.phase == CrewPhase.RUN }
        .flatMap { crew -> crew.apps.filter { !it.done && !it.reported } }

/** Everything settled tonight, for the count under the list. */
fun doneTonightIn(crews: List<Crew>): Int =
    crews
        .filter { it.phase == CrewPhase.RUN }
        .sumOf { crew -> crew.apps.count { it.done || it.reported } }

/** Seats in any crew not yet running that this account has not answered on. */
fun waitingIn(crews: List<Crew>): List<CrewSeat> =
    crews
        .filter { it.phase == CrewPhase.LOBBY || it.phase == CrewPhase.OPEN }
        .flatMap { crew ->
            crew.seats.filter { !it.judged && !it.mine && it.state == SeatState.CHECKING }
        }

/** The crew holding your one seat that has not started, by name, or null where you are free. */
fun holdingIn(crews: List<Crew>): String? =
    crews
        .firstOrNull { crew ->
            crew.phase != CrewPhase.RUN &&
                crew.phase != CrewPhase.RECORD &&
                crew.seats.any { it.mine }
        }?.name

/**
 * Whether this account may reject anything in a given crew.
 *
 * A red only counts from somebody who has already got one app in that same crew working, because
 * a Play "not found" far more often means the reader is not set up than that the app is broken.
 * The server refuses it either way; this is so the button can say so before it is pressed.
 */
fun mayJudgeIn(
    crews: List<Crew>,
    crewId: String,
): Boolean =
    crews
        .firstOrNull { it.id == crewId }
        ?.seats
        ?.any { it.judged && !it.mine } == true
