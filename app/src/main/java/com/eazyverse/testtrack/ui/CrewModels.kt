package com.eazyverse.testtrack.ui

/*
 * Crews, in the shape the screen needs them.
 *
 * Plain values mapped from what the API sends (see CrewWire.kt). Every rule is the server's:
 * whether an app is in, what a report does, whose day it is. The screen only says so.
 *
 * The thing that shapes every type below is that a person is in several crews at once. Nothing
 * here is "the crew"; there is a list of them, each at its own point in its own fortnight, and the
 * work they produce lands on one person in one evening. See docs/crew.md.
 */

/** What a crew is doing, which is also which of the four screens its own page shows. */
enum class CrewPhase {
    /** Taking seats, and not yours. The one a new seat would go into. */
    OPEN,

    /** Yours, and not started: still filling, or full and waiting for your midnight. */
    LOBBY,

    /** The fourteen days. The part of the app that was already worth using. */
    RUN,

    /** Over. What it did, kept. */
    RECORD,
}

/** Where one seat has got to. */
enum class SeatState {
    /** Taken while the Play check could not run. The server tries again; it does not count yet. */
    CHECKING,

    /** Seen on Play through the testers group. Counts. */
    CONFIRMED,

    /** Sent back before the run: two reports, or Play would not show it. The owner can fix it. */
    REJECTED,

    /** Out of a running crew: two missed days in a row, or the team took it out. */
    REMOVED,
    ;

    companion object {
        fun of(wire: String) =
            when (wire) {
                "confirmed" -> CONFIRMED
                "rejected", "withdrawn" -> REJECTED
                "removed" -> REMOVED
                else -> CHECKING
            }
    }
}

/**
 * The numbers, in one place. Mirrored from the server's app/core/rules.py, which decides.
 *
 * Fifteen rather than fourteen, which costs every member one more app a night: nobody tests their
 * own, so a crew of fifteen is fourteen apps to open. What it buys is slack. Google wants twelve
 * testers for the fortnight, so three members can drop and the run still counts.
 *
 * There is no limit on how many crews one person may be in, which is a decision with a number
 * attached: two crews is twenty-eight apps a night and three is forty-two. Since nothing stops
 * somebody taking on more than they can finish, the app's whole defence is saying the figure out
 * loud before they do. See [nightly].
 */
object CrewRules {
    /** The whole crew. It starts at this number and at no other. */
    const val SEATS = 15

    /** What Google actually asks for, and so how far a crew can bleed before the run is wasted. */
    const val FLOOR = 12

    /** Apps one crew costs you a night. Everybody in it but yourself. */
    const val NIGHTLY = SEATS - 1

    const val RUN_DAYS = 14

    /**
     * What a seat costs, as the server charges it (rules.SEAT_COST). Back in full if the app
     * leaves before its run; kept once the run has it. The server's answer is the one that counts.
     */
    const val SEAT_COST = 100

    /** Reports from different members that send an app back before the run starts. */
    const val REPORTS_TO_RETURN = 2

    /** Members' answers, either way, that decide an app the Play check could not look at. */
    const val VOTES_TO_DECIDE = 2

    /** The shortest reason a report may give. The server refuses anything shorter. */
    const val REPORT_MIN_CHARS = 10

    /**
     * Real foreground seconds before a night's open counts.
     *
     * Ten, not the thirty a Feedback review needs: a crew visit is "you opened it today", which is
     * what Google asks of a tester, while a review is somebody forming an opinion. Mirrored from
     * rules.VISIT_SECONDS on the server, which refuses anything short regardless of what is sent.
     */
    const val VISIT_SECONDS = 10

    /** What being in this many crews costs every evening, which is the only honest warning. */
    fun nightly(crews: Int) = crews * NIGHTLY
}

/**
 * One app's place in a crew.
 *
 * [reasons] and [outReason] are only ever filled in on your own seat: what reports said, and why
 * it left, are for its owner and nobody else.
 */
data class CrewSeat(
    val id: String,
    val appName: String,
    val owner: String,
    val packageName: String = "",
    /** How a tester gets past the front door: a test login or a code. Read by the whole crew. */
    val notes: String = "",
    /** Which crew it is in. Carried on the seat because some lists span crews. */
    val crew: String = "",
    val state: SeatState = SeatState.CONFIRMED,
    /** This account's app. */
    val mine: Boolean = false,
    val reports: Int = 0,
    /** This account reported it. */
    val reported: Boolean = false,
    val reasons: List<String> = emptyList(),
    val outReason: String = "",
    /** An app the Play check could not look at is decided by members: answers so far. */
    val yes: Int = 0,
    val no: Int = 0,
    /** This account's answer on it, or null if it has not answered. */
    val myVote: Boolean? = null,
)

/**
 * One member's fortnight, from the outside, in their own calendar.
 *
 * [day] is theirs, not the reader's: members keep their own midnights, so two people in one crew
 * can be a day apart and both be on time. See docs/crew.md.
 */
data class CrewMember(
    val id: String,
    val name: String,
    val appName: String,
    val day: Int = 0,
    val doneToday: Int = 0,
    val owedToday: Int = 0,
    /** Everything they owe today is opened. */
    val nightDone: Boolean = false,
    val missed: Int = 0,
    /** Yesterday was short: one more and their app leaves. */
    val missedYesterday: Boolean = false,
    val mine: Boolean = false,
    /** Out of the run: two missed days in a row, or the team took the app out. */
    val out: Boolean = false,
    /** Opened the reader's own app on their current day, and when (an ISO time, or blank). */
    val openedMineToday: Boolean = false,
    val openedMineAt: String = "",
    /** On how many of their days they have opened the reader's app. */
    val openedMineDays: Int = 0,
)

/**
 * One app owed a visit tonight.
 *
 * [crew] is on the app rather than looked up, because tonight's list is every crew's apps in one
 * column. Which crew an app came from is not why you are opening it, so it is a tag rather than a
 * heading, but it has to be there: without it two crews' lists are one undifferentiated wall.
 */
data class CrewApp(
    val id: String,
    val label: String,
    /** What actually gets launched and timed. A row without it cannot be opened, only claimed. */
    val packageName: String = "",
    val owner: String,
    val crew: String = "",
    val done: Boolean = false,
    /** Reported by this account as not installing, so it is excused rather than owed. */
    val reported: Boolean = false,
)

/**
 * Everything one crew is, at whatever point it has reached.
 *
 * One type across all four phases rather than four, because the phases are the same crew getting
 * older and the screen wants to say so. Fields that mean nothing yet are empty rather than absent.
 */
data class Crew(
    val id: String,
    val name: String,
    val phase: CrewPhase,
    /** Still taking seats, on the server. A lobby can also be full and waiting for midnight. */
    val filling: Boolean = false,
    /** Seats holding a place, and those of them seen on Play. From the server, not the list. */
    val taken: Int = 0,
    val filled: Int = 0,
    val seats: List<CrewSeat> = emptyList(),
    val members: List<CrewMember> = emptyList(),
    val apps: List<CrewApp> = emptyList(),
    /** Which day of the run, in this account's own calendar. Zero before it starts. */
    val day: Int = 0,
    /** Milliseconds until this account's midnight, once it is full. */
    val startsIn: Long = 0L,
    /** How the fortnight went, once there is a fortnight to talk about. */
    val finishedDays: Int = 0,
) {
    /** This account's seat in it, in whatever state. */
    val mine: CrewSeat? get() = seats.firstOrNull { it.mine }

    /**
     * This account may answer for apps waiting on members: its own app is in, which is what shows
     * its setup works, and the crew is still filling.
     */
    val mayVote: Boolean get() = filling && mine?.state == SeatState.CONFIRMED

    /** This account's app is no longer part of it. */
    val out: Boolean get() = mine?.state == SeatState.REJECTED || mine?.state == SeatState.REMOVED

    /** Owed tonight, in this crew alone. */
    val owed: Int get() = apps.count { !it.done && !it.reported }

    /** Days this account has already missed here. */
    val myMisses: Int get() = members.firstOrNull { it.mine }?.missed ?: 0

    /**
     * One more missed day and this account's app is out of this crew.
     *
     * The reason the run screen sorts by this rather than by crew. Somebody in three crews who
     * gets through twenty of forty-two apps has chosen, without being told, which crews to lose;
     * the list can make that choice a good one by putting the costly rows first.
     */
    val atRisk: Boolean get() = phase == CrewPhase.RUN && members.firstOrNull { it.mine }?.missedYesterday == true

    /** Members still in the run. */
    val stayed: Int get() = members.count { !it.out }

    /** The others still in the run: the people testing this account's app. */
    val testers: List<CrewMember> get() = members.filter { !it.mine && !it.out }

    /** Of [testers], those who have opened this account's app on their current day. */
    val openedMineTonight: Int get() = testers.count { it.openedMineToday }

    /** Of [testers], those who opened this account's app on every day of a finished run. */
    val testedEveryDay: Int get() = testers.count { it.openedMineDays >= finishedDays && finishedDays > 0 }

    /** What the crew row says about itself, in one line. */
    val standing: String
        get() =
            when {
                out && phase != CrewPhase.RECORD -> "Your app is out"
                phase == CrewPhase.OPEN -> "${CrewRules.SEATS - taken} seats left"
                phase == CrewPhase.LOBBY && filling -> "Filling · $filled of ${CrewRules.SEATS}"
                phase == CrewPhase.LOBBY -> "Starts in ${untilStart(startsIn)}"
                phase == CrewPhase.RUN -> "Day $day of ${CrewRules.RUN_DAYS}"
                else -> "Finished · $finishedDays days"
            }
}

/** "9h 14m", for the wait between filling and midnight. Never seconds: nobody is counting. */
fun untilStart(ms: Long): String {
    val minutes = (ms / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60
    return when {
        minutes < 1L -> "any moment"
        hours < 1L -> "${minutes}m"
        else -> "${hours}h ${minutes % 60}m"
    }
}
