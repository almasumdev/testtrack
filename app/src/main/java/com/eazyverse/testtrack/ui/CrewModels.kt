package com.eazyverse.testtrack.ui

/*
 * Crews, in the shape the screen needs them.
 *
 * Nothing here touches Firestore, deliberately and for the same reason Circle does not: the rules
 * below are new, and settling how they read is the part worth getting wrong cheaply. A crew that
 * forms itself and is gatekept by its own members is a different product from one an admin fills,
 * and no collection should be designed until the screen has stopped moving.
 *
 * The thing that shapes every type below is that a person is in several crews at once. Nothing
 * here is "the crew"; there is a list of them, each at its own point in its own fortnight, and the
 * work they produce lands on one person in one evening. See docs/crew.md.
 */

/** What a crew is doing, which is also which of the four screens its own page shows. */
enum class CrewPhase {
    /** Taking seats. Nobody has committed a fortnight yet and anybody may join. */
    OPEN,

    /** Full, or nearly, and waiting for midnight. The window where setup gets finished. */
    LOBBY,

    /** The fourteen days. The part of the app that was already worth using. */
    RUN,

    /** Over. What it did, kept. */
    RECORD,
}

/** Where one seat has got to, from taken to counted. */
enum class SeatState {
    /** Taken, waiting on members to try installing it. Does not count toward the fifteen. */
    CHECKING,

    /** Two greens. Counts. */
    CONFIRMED,

    /** Two reds. The owner has something to fix, and the seat is gone. */
    REJECTED,
}

/**
 * The numbers, in one place.
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

    /** Agreeing verdicts needed either way. Two, so no single member decides anything. */
    const val VERDICTS = 2

    /**
     * Real foreground seconds before a night's open counts.
     *
     * Ten, not the thirty a Feedback review needs: a crew visit is "you opened it today", which is
     * what Google asks of a tester, while a review is somebody forming an opinion. Mirrored from
     * rules.VISIT_SECONDS on the server, which refuses anything short regardless of what is sent.
     */
    const val VISIT_SECONDS = 10

    /**
     * Seats filled, out of [SEATS].
     *
     * Only confirmed ones. A seat still being checked is somebody's intention, and a crew that
     * started on intentions would start with apps nobody can install.
     */
    fun filled(seats: List<CrewSeat>) = seats.count { it.state == SeatState.CONFIRMED }

    /** What being in this many crews costs every evening, which is the only honest warning. */
    fun nightly(crews: Int) = crews * NIGHTLY
}

/**
 * One app's place in a crew, and how far along its check is.
 *
 * The two verdict counts are kept rather than a list of who said what, because the screen never
 * shows who said what. Publishing that a named member failed to install your app turns a check
 * into an accusation, and the fix is the same either way.
 */
data class CrewSeat(
    val id: String,
    val appName: String,
    val owner: String,
    /** Which crew it is in. Carried on the seat because verification is listed across crews. */
    val crew: String = "",
    val takenAt: Long = 0L,
    val greens: Int = 0,
    val reds: Int = 0,
    /** This account's app. The one seat on the screen that is reported rather than judged. */
    val mine: Boolean = false,
    /**
     * This account has already given a verdict on it, so it is off the list of things to do.
     *
     * Held per seat rather than as one flag, since a member works through the crew one app at a
     * time and the screen has to know which ones are left.
     */
    val judged: Boolean = false,
) {
    val state: SeatState
        get() =
            when {
                reds >= CrewRules.VERDICTS -> SeatState.REJECTED
                greens >= CrewRules.VERDICTS -> SeatState.CONFIRMED
                else -> SeatState.CHECKING
            }
}

/**
 * One member's fourteen days, from the outside.
 *
 * [today] is whether they have opened everything owed today, which is the only fact the run screen
 * is really about. [days] is how many they have completed, and it can differ between members of
 * one crew by a day, because a day is each member's own local one. See docs/crew.md.
 */
data class CrewMember(
    val name: String,
    val appName: String,
    val days: Int = 0,
    val today: Boolean = false,
    val missed: Int = 0,
    val mine: Boolean = false,
    /** Gone. Two missed days in a row, or an app pulled. Kept on the list, greyed. */
    val out: Boolean = false,
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
    /** Reported as broken by this account, so it is excused rather than owed. */
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
    val seats: List<CrewSeat> = emptyList(),
    val members: List<CrewMember> = emptyList(),
    val apps: List<CrewApp> = emptyList(),
    /** Which day of the run, in this account's own calendar. Zero before it starts. */
    val day: Int = 0,
    /** Milliseconds until 12:01, once it is full. Zero when it is not waiting on anything. */
    val startsIn: Long = 0L,
    /** How the fortnight went, once there is a fortnight to talk about. */
    val finishedDays: Int = 0,
    val tixEarned: Int = 0,
) {
    /** Owed tonight, in this crew alone. */
    val owed: Int get() = apps.count { !it.done && !it.reported }

    /** Days this account has already missed here. Two in a row and the app leaves. */
    val myMisses: Int get() = members.firstOrNull { it.mine }?.missed ?: 0

    /**
     * One more missed day and this account's app is out of this crew.
     *
     * The reason the run screen sorts by this rather than by crew. Somebody in three crews who
     * gets through twenty of forty-two apps has chosen, without being told, which crews to lose;
     * the list can make that choice a good one by putting the costly rows first.
     */
    val atRisk: Boolean get() = phase == CrewPhase.RUN && myMisses > 0

    /** What the crew row says about itself, in one line. */
    val standing: String
        get() =
            when (phase) {
                CrewPhase.OPEN -> "${CrewRules.SEATS - CrewRules.filled(seats)} seats left"
                CrewPhase.LOBBY ->
                    "Filling · ${CrewRules.filled(seats)} of ${CrewRules.SEATS}"
                CrewPhase.RUN -> "Day $day of ${CrewRules.RUN_DAYS}"
                CrewPhase.RECORD -> "Finished · $finishedDays days"
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
