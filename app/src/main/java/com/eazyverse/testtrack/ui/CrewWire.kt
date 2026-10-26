package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiCrew
import com.eazyverse.testtrack.net.ApiSeat

/*
 * The server's crews, in the shape the screens already speak.
 *
 * A translation layer rather than a rewrite. The crew screens were designed against the types in
 * CrewModels.kt, and those types say what the screen is about; the wire types say what the
 * database holds. Mapping between them here keeps one change from becoming two.
 *
 * Pure functions over values, so all of this is tested without a device, a network or an account.
 */

/**
 * Which of the four screens a crew belongs on.
 *
 * The server has three states and the app has four, because "full" is two different situations
 * for the person looking at it: a crew that has filled but whose own midnight has not arrived is
 * a lobby, and the same crew an hour later is a run. Which one it is depends on the reader, which
 * is why the day comes from the server per member rather than being worked out here. A crew the
 * reader is not in is the open one, whatever its state.
 */
fun crewPhase(
    crew: ApiCrew,
    member: Boolean = true,
): CrewPhase =
    when {
        !member -> CrewPhase.OPEN
        crew.state == "done" -> CrewPhase.RECORD
        crew.state == "filling" -> CrewPhase.LOBBY
        crew.day >= 1 -> CrewPhase.RUN
        else -> CrewPhase.LOBBY
    }

/** One seat, as the crew's own page lists it. */
fun ApiSeat.toSeat(crewName: String = "") =
    CrewSeat(
        id = id,
        appName = appName,
        owner = ownerName,
        packageName = packageName,
        icon = icon,
        notes = notes,
        crew = crewName,
        state = SeatState.of(state),
        mine = mine,
        reports = reports,
        reported = reported,
        reasons = reasons,
        outReason = outReason,
        yes = yes,
        no = no,
        myVote = myVote,
        charged = charged,
        refunded = refunded,
    )

/** One member, as the run screen lists them, in their own calendar. */
fun ApiSeat.toMember() =
    CrewMember(
        id = id,
        name = ownerName,
        appName = appName,
        day = day,
        doneToday = doneToday,
        owedToday = owedToday,
        nightDone = nightDone,
        missed = missed,
        missedYesterday = missedYesterday,
        mine = mine,
        out = state == "removed" || state == "rejected",
        openedMineToday = openedMineToday,
        openedMineAt = openedMineAt.orEmpty(),
        openedMineDays = openedMineDays,
    )

/**
 * One app owed tonight.
 *
 * Only apps still in the run, and never your own. An app sent back is not something to ask
 * anybody to open, and nobody tests their own.
 */
fun ApiSeat.toApp(crewName: String = "") =
    CrewApp(
        id = id,
        label = appName,
        packageName = packageName,
        owner = ownerName,
        crew = crewName,
        done = openedToday,
        // A report from this account excuses it from this app: something that will not install
        // is not something anybody can open.
        reported = reported,
    )

/**
 * A whole crew, ready for whichever screen it lands on.
 *
 * Fields that mean nothing at this crew's stage stay empty rather than absent, which is how
 * CrewModels.kt is built: one type across all four phases, because the phases are the same crew
 * getting older.
 */
fun ApiCrew.toCrew(member: Boolean = true): Crew {
    val mine = members.firstOrNull { it.mine }
    // An app sent back before its run stays in the Lobby for its owner, whatever the crew did
    // next: what they have to do about it is fix it and take a seat again, not watch a run.
    val phase = if (member && mine?.state == "rejected") CrewPhase.LOBBY else crewPhase(this, member)
    val stillIn = mine != null && mine.state == "confirmed"
    // An app the team put in part-way through is owed from its first night, not before.
    val owed = members.filter { !it.mine && it.state == "confirmed" && it.firstNight <= day }

    return Crew(
        id = id,
        name = name,
        phase = phase,
        filling = state == "filling",
        taken = taken,
        filled = filled,
        icons = icons,
        seats = members.map { it.toSeat(name) },
        // Rejected seats never ran, so they are not members; only the reader sees their own.
        members = members.filter { it.state != "rejected" }.map { it.toMember() },
        apps = if (phase == CrewPhase.RUN && stillIn) owed.map { it.toApp(name) } else emptyList(),
        day = day,
        startsIn = startsInMs,
        // What it came to. Only meaningful once it is over, and the server's own day count is
        // the honest figure: capped at the fortnight rather than however long ago it filled, and
        // counted from this account's first night where its app joined part-way through.
        finishedDays = if (phase == CrewPhase.RECORD) (minOf(day, runDays) - firstNight + 1).coerceAtLeast(0) else 0,
        firstNight = firstNight,
    )
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

/** Your apps sent back before a run, to fix and take a seat with again. */
fun sentBackIn(crews: List<Crew>): List<CrewSeat> = crews.mapNotNull { crew -> crew.mine?.takeIf { it.state == SeatState.REJECTED } }
