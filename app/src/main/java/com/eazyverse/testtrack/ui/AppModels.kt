package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.Request

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
    /** What the app is, as Play has it or its owner wrote it; blank when there is nothing. */
    val shortDescription: String = "",
    val fullDescription: String = "",
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

/** Where one request stands, as its card says it at the top right. */
enum class RequestState(
    val label: String,
) {
    ON_CIRCLE("On Circle"),
    OFF_CIRCLE("Off Circle"),
    DONE("Done"),
    CLOSED("Closed"),
}

/** One bought review, as a face on a request's card. */
enum class ReviewFace { DONE, TO_CHECK, IN_PROGRESS, OPEN }

/** A request's reviews, one count for each face. */
data class ReviewCounts(
    val done: Int = 0,
    val toCheck: Int = 0,
    val inProgress: Int = 0,
    val open: Int = 0,
) {
    val total: Int get() = done + toCheck + inProgress + open

    /** In: accepted, or waiting for the owner to accept or refuse. */
    val inCount: Int get() = done + toCheck
}

private val IN_PROGRESS_STATES = setOf("claimed", "installed", "used", "checking")

/** Accepted is what the server counts as done; open is what was bought and nobody has started. */
fun Request.reviewCounts(): ReviewCounts {
    val done = this.done.coerceAtLeast(0)
    val toCheck = reviews.count { it.state == "submitted" }
    val inProgress = reviews.count { it.state in IN_PROGRESS_STATES }
    return ReviewCounts(done, toCheck, inProgress, (bought - done - toCheck - inProgress).coerceAtLeast(0))
}

/** Filled first, then closed, then off Circle while testers can't install it. */
fun Request.cardState(): RequestState =
    when {
        bought > 0 && done >= bought -> RequestState.DONE
        closed -> RequestState.CLOSED
        unlisted -> RequestState.OFF_CIRCLE
        else -> RequestState.ON_CIRCLE
    }

val RequestState.finished: Boolean get() = this == RequestState.DONE || this == RequestState.CLOSED

/** The card's title: how many reviews are in. */
fun requestTitle(
    state: RequestState,
    counts: ReviewCounts,
): String {
    val n = counts.total
    return when {
        state == RequestState.DONE -> if (n == 1) "The review is in" else "All $n reviews in"
        counts.inCount == 0 -> "No reviews in yet"
        else -> "${counts.inCount} of $n ${if (n == 1) "review" else "reviews"} in"
    }
}

/** One face per review, in a fixed order: done, to check, under way, open. */
fun ReviewCounts.faces(): List<ReviewFace> =
    List(done) { ReviewFace.DONE } +
        List(toCheck) { ReviewFace.TO_CHECK } +
        List(inProgress) { ReviewFace.IN_PROGRESS } +
        List(open) { ReviewFace.OPEN }

/** Most faces a card draws one by one; above it the card shows [summary] instead. */
const val MAX_REVIEW_FACES = 10

val ReviewCounts.summarised: Boolean get() = total > MAX_REVIEW_FACES

/** One stack of the summary: a face kind, how many, and the word under the count. */
data class FaceGroup(
    val face: ReviewFace,
    val count: Int,
    val word: String,
)

/** The summary's stacks, in the faces' order, leaving out the kinds with none. */
fun ReviewCounts.summary(): List<FaceGroup> =
    listOf(
        FaceGroup(ReviewFace.DONE, done, "done"),
        FaceGroup(ReviewFace.TO_CHECK, toCheck, "to check"),
        FaceGroup(ReviewFace.IN_PROGRESS, inProgress, "under way"),
        FaceGroup(ReviewFace.OPEN, open, "open"),
    ).filter { it.count > 0 }

/** The faces in words, for a screen reader: "2 done, 1 to check, 2 open". */
fun ReviewCounts.spoken(): String = summary().joinToString(", ") { "${it.count} ${it.word}" }.ifEmpty { "No reviews" }

/** How a row of faces fills a width: faces per row, each face's outer size, and the gap between them. */
data class FaceFit(
    val perRow: Int,
    val face: Float,
    val gap: Float,
)

/**
 * As many faces of at least [minFace] as fit with [minGap] between them; the faces then grow to
 * fill the row, up to [maxFace], and what is left goes to the gaps. A face's size is its outer
 * edge, ring included, so two neighbours always keep the whole gap between them.
 */
fun fitFaces(
    width: Float,
    minFace: Float,
    maxFace: Float,
    minGap: Float,
): FaceFit {
    val perRow = maxOf(1, ((width + minGap) / (minFace + minGap)).toInt())
    if (perRow == 1) return FaceFit(1, minOf(maxFace, width), 0f)
    val face = minOf(maxFace, (width - minGap * (perRow - 1)) / perRow)
    return FaceFit(perRow, face, (width - perRow * face) / (perRow - 1))
}

/** How far a face's mark reaches past its bottom right: an eighth of the face, kept 2dp clear of the next face. */
fun markOverhang(
    face: Float,
    gap: Float,
): Float = minOf(face * 0.125f, (gap - 2f).coerceAtLeast(0f))

/** The request shown in full (the open one, else the newest), and every other one, newest first. */
fun List<Request>.splitForPage(): Pair<Request?, List<Request>> {
    val newest = sortedByDescending { it.createdAt ?: Long.MIN_VALUE }
    val lead = newest.firstOrNull { !it.cardState().finished } ?: newest.firstOrNull()
    return lead to newest.filter { it !== lead }
}

/** What a request's card says under its faces. */
enum class RequestFoot { OFF_CIRCLE, TO_CHECK, ASK_AGAIN, TIX_BACK, ON_IT, NONE }

/** The first that applies wins; [newestFinished] is the newest request once it is over and none is open. */
fun requestFoot(
    request: Request,
    compact: Boolean,
    newestFinished: Boolean,
): RequestFoot {
    val state = request.cardState()
    return when {
        state == RequestState.OFF_CIRCLE -> RequestFoot.OFF_CIRCLE
        request.reviewCounts().toCheck > 0 -> RequestFoot.TO_CHECK
        newestFinished && state.finished -> RequestFoot.ASK_AGAIN
        state == RequestState.CLOSED && (request.refunded ?: 0) > 0 -> RequestFoot.TIX_BACK
        state == RequestState.ON_CIRCLE && !compact -> RequestFoot.ON_IT
        else -> RequestFoot.NONE
    }
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
fun AppProblem.sentence(): String =
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
