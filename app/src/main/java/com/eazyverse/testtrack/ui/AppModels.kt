package com.eazyverse.testtrack.ui

/*
 * My apps as the screens draw it: each app added once, one status line and the problems it has.
 *
 * Mapped from the server's answer in the view model, so no screen here reaches the network to
 * be drawn, and every word a tile says is worked out by plain functions a test can call.
 */

/** Where an app is with crews: the newest seat that was not simply given up. */
data class AppCrewStatus(
    /** waiting, checking, starting, testing, finished, rejected or removed. */
    val state: String,
    val crewId: String,
    val crewName: String,
    val day: Int = 0,
    val runDays: Int = CrewRules.RUN_DAYS,
)

/** The newest feedback request for an app. */
data class AppFeedbackStatus(
    /** collecting, done or closed. */
    val state: String,
    val postId: String,
    val done: Int = 0,
    val bought: Int = 0,
    /** Reviews of this request waiting for the owner to accept or refuse. */
    val toCheck: Int = 0,
    /** Taken off Circle because testers could not install the app. */
    val unlisted: Boolean = false,
)

/** One app on My apps. */
data class AppEntry(
    val id: String,
    val pkg: String,
    val name: String,
    /** Play's icon, or ours for one picked from a phone; blank only for an app typed on an old build. */
    val icon: String = "",
    val developer: String = "",
    val live: Boolean = false,
    /** play, crew or review; null while unverified. */
    val verifiedBy: String? = null,
    val crew: AppCrewStatus? = null,
    val crewsDone: Int = 0,
    val feedback: AppFeedbackStatus? = null,
    val reviewsReceived: Int = 0,
    val canCrew: Boolean = false,
    val canFeedback: Boolean = false,
    val canDelete: Boolean = false,
    val whyNotDelete: String = "",
) {
    val verified: Boolean get() = verifiedBy != null

    /** A feedback request still collecting. */
    val collecting: Boolean get() = feedback?.state == "collecting"
}

/** What the status line under an app's name is coloured and weighted as. */
enum class StatusTone { ACCENT, BODY, QUIET, FAINT }

/** The small icon that may lead the status line. */
enum class StatusMark { NONE, DONE, WAITING }

/**
 * The one line under an app's name. [text] is never cut; [crew] is the crew's name that follows it,
 * the only part that may end in an ellipsis when the line is short of room.
 */
data class AppStatusLine(
    val text: String,
    val crew: String = "",
    val tone: StatusTone = StatusTone.BODY,
    val mark: StatusMark = StatusMark.NONE,
)

/** What is wrong with an app, shown by a mark on the right of its tile rather than in its status. */
enum class AppProblem { UNLISTED, NOT_VERIFIED, SENT_BACK, TAKEN_OUT }

/** A count as a tile says it: the number, or "99+" above the cap. */
fun cappedCount(
    count: Int,
    cap: Int = 99,
): String = if (count > cap) "$cap+" else "$count"

/** Reviews of this app's request that wait for its owner to accept or refuse. */
val AppEntry.toCheck: Int get() = feedback?.toCheck?.coerceAtLeast(0) ?: 0

/** One bought review, as a face on the feedback card. */
enum class ReviewFace { ACCEPTED, TO_CHECK, OPEN }

/** The faces a card draws, and how many more reviews there are than room for faces. */
data class ReviewFaces(
    val faces: List<ReviewFace>,
    val more: Int = 0,
)

/** Most faces one card draws before the rest become a "+N". */
const val MAX_REVIEW_FACES = 10

/**
 * One face per review bought, in a fixed order: accepted, then waiting to be checked, then the rest.
 * The server counts accepted and waiting reviews but not who has started one, so a review being
 * written and a slot nobody took are both open. When there are more than [cap], the open ones go
 * first, then the oldest accepted, and the ones waiting for the owner always stay.
 */
fun AppFeedbackStatus.faces(cap: Int = MAX_REVIEW_FACES): ReviewFaces {
    val total = bought.coerceAtLeast(0)
    val accepted = done.coerceIn(0, total)
    val waiting = toCheck.coerceIn(0, total - accepted)
    val open = total - accepted - waiting
    val waitingShown = minOf(waiting, cap)
    val room = cap - waitingShown
    val acceptedShown = minOf(accepted, room)
    val openShown = minOf(open, room - acceptedShown)
    val faces =
        List(acceptedShown) { ReviewFace.ACCEPTED } +
            List(waitingShown) { ReviewFace.TO_CHECK } +
            List(openShown) { ReviewFace.OPEN }
    return ReviewFaces(faces, total - faces.size)
}

/** What the feedback card says under its faces. */
enum class FeedbackCardKind { TO_CHECK, WAITING, OFF_CIRCLE, ALL_IN, CLOSED }

fun AppFeedbackStatus.cardKind(): FeedbackCardKind =
    when {
        state == "done" -> FeedbackCardKind.ALL_IN
        state != "collecting" -> FeedbackCardKind.CLOSED
        toCheck > 0 -> FeedbackCardKind.TO_CHECK
        unlisted -> FeedbackCardKind.OFF_CIRCLE
        else -> FeedbackCardKind.WAITING
    }

/** The faces in words, for a screen reader: "2 accepted, 1 to check, 2 open". */
fun AppFeedbackStatus.facesSpoken(): String {
    val total = bought.coerceAtLeast(0)
    val accepted = done.coerceIn(0, total)
    val waiting = toCheck.coerceIn(0, total - accepted)
    return listOfNotNull(
        "$accepted accepted",
        "$waiting to check".takeIf { waiting > 0 },
        "${total - accepted - waiting} open".takeIf { total - accepted - waiting > 0 },
    ).joinToString(", ")
}

/** The tag after a block's heading: when it is for. */
const val CREW_WHEN = "Before production"
const val FEEDBACK_WHEN = "Any time"

/** What an empty start tile says it is for, so nobody has to guess which one to pick. */
const val CREW_START_LINE = "Testing days Google asks for"
const val FEEDBACK_START_LINE = "Real people try it, then review"

/** The problems an app has, in the order a tile lists them. */
fun AppEntry.problems(): List<AppProblem> =
    listOfNotNull(
        AppProblem.UNLISTED.takeIf { feedback?.unlisted == true },
        AppProblem.NOT_VERIFIED.takeIf { !live && !verified },
        AppProblem.SENT_BACK.takeIf { crew?.state == "rejected" },
        AppProblem.TAKEN_OUT.takeIf { crew?.state == "removed" },
    )

private fun AppEntry.crewLabel(): String = crew?.crewName?.ifBlank { null } ?: "its crew"

/** The short line a problem opens with, in the callout and as the mark's spoken label. */
fun AppProblem.title(app: AppEntry): String =
    when (this) {
        AppProblem.UNLISTED -> "Can't be installed"
        AppProblem.NOT_VERIFIED -> "Not verified yet"
        AppProblem.SENT_BACK -> "Sent back by ${app.crewLabel()}"
        AppProblem.TAKEN_OUT -> "Taken out of ${app.crewLabel()}"
    }

/** One plain sentence for a problem, said under its title in the callout. */
fun AppProblem.sentence(app: AppEntry): String =
    when (this) {
        AppProblem.UNLISTED -> "Testers couldn't install it, so it's off Circle for now."
        AppProblem.NOT_VERIFIED -> "Add the testers group to your closed test, then tap Verify."
        AppProblem.SENT_BACK -> "Fix what the crew found, then put it in again."
        AppProblem.TAKEN_OUT -> "It's no longer in this run."
    }

/**
 * What an app is doing, in one line. The first that applies wins; problems are never here, they
 * have their own mark. The crew's length comes from the server, never from a number written here.
 */
fun AppEntry.statusLine(): AppStatusLine {
    val crew = crew
    val feedback = feedback
    val state = crew?.state
    val name = crew?.crewName.orEmpty()
    return when {
        toCheck > 0 ->
            AppStatusLine(if (toCheck == 1) "1 review to check" else "${cappedCount(toCheck)} reviews to check", tone = StatusTone.ACCENT)
        crew != null && state == "testing" && crew.day <= crew.runDays ->
            AppStatusLine("Day ${crew.day} of ${crew.runDays} in ", name)
        crew != null && state == "testing" -> AppStatusLine("All ${crew.runDays} days done")
        state == "starting" -> AppStatusLine("Starts tonight in ", name)
        state == "checking" -> AppStatusLine("Being checked in ", name)
        state == "waiting" -> AppStatusLine("Waiting in ", name)
        feedback?.state == "collecting" ->
            AppStatusLine(if (feedback.done > 0) "${feedback.done} of ${feedback.bought} reviews in" else "Collecting feedback")
        state == "finished" ->
            AppStatusLine(
                if (crewsDone >
                    1
                ) {
                    "$crewsDone crew tests done"
                } else {
                    "Crew test done"
                },
                tone = StatusTone.QUIET,
                mark = StatusMark.DONE,
            )
        feedback?.state == "done" ->
            AppStatusLine("All ${feedback.bought} reviews in", tone = StatusTone.QUIET, mark = StatusMark.DONE)
        feedback?.state == "closed" -> AppStatusLine("Feedback closed", tone = StatusTone.FAINT)
        live && !verified -> AppStatusLine("Waiting for Play", tone = StatusTone.QUIET, mark = StatusMark.WAITING)
        state == "rejected" || state == "removed" -> AppStatusLine("Not in a crew", tone = StatusTone.QUIET)
        live -> AppStatusLine("Ready for feedback", tone = StatusTone.QUIET)
        // A closed app Play has not shown to the group yet is not ready for anything; its mark says why.
        !verified -> AppStatusLine("Add the group to its track", tone = StatusTone.QUIET)
        else -> AppStatusLine("Ready for a crew", tone = StatusTone.QUIET)
    }
}

/** How the app is known to be real, in the chip under its name. */
fun verifiedChip(app: AppEntry): String =
    when (app.verifiedBy) {
        "play" -> "Verified on Play"
        "crew" -> "Verified by its crew"
        "review" -> "Verified by a review"
        else -> "Not verified"
    }

/** Whether the crew row opens a crew rather than starting one. */
val AppEntry.crewOpen: Boolean get() = crew?.state.let { it != null && it != "rejected" && it != "removed" }

/** Where the app is with crews, short enough for one line of its row. */
fun crewStatus(app: AppEntry): String {
    val crew = app.crew
    return when {
        crew == null && app.live -> "Live apps take feedback, not crews"
        crew == null -> "Not in a crew yet"
        crew.state == "waiting" -> "In ${crew.crewName}, filling up"
        crew.state == "checking" -> "In ${crew.crewName}, members checking"
        crew.state == "starting" -> "${crew.crewName} starts at midnight, your time"
        crew.state == "testing" && crew.day > crew.runDays -> "Your nights in ${crew.crewName} are done"
        crew.state == "testing" -> "Day ${crew.day} of ${crew.runDays} in ${crew.crewName}"
        crew.state == "finished" -> if (app.crewsDone > 1) "${app.crewsDone} crew tests done" else "Crew test done"
        crew.state == "rejected" -> "${crew.crewName} sent it back"
        crew.state == "removed" -> "Left ${crew.crewName} after missed days"
        else -> crew.crewName
    }
}

/** Where the app is with feedback, short enough for one line of its row. */
fun feedbackStatus(app: AppEntry): String {
    val feedback = app.feedback ?: return "No feedback asked for yet"
    return when (feedback.state) {
        "collecting" -> "${feedback.done} of ${feedback.bought} reviews in"
        "done" -> "All ${feedback.bought} reviews in"
        else -> "Closed with ${feedback.done} of ${feedback.bought} reviews"
    }
}
