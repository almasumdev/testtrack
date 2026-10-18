package com.eazyverse.testtrack.ui

/*
 * My apps as the screens draw it: each app added once, and the badges that say where it is.
 *
 * Mapped from the server's answer in the view model, so no screen here reaches the network to
 * be drawn, and every word a badge says is worked out by plain functions a test can call.
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
)

/** One app on My apps. */
data class AppEntry(
    val id: String,
    val pkg: String,
    val name: String,
    /** Play's icon, or blank when added by hand. */
    val icon: String = "",
    val developer: String = "",
    val live: Boolean = false,
    /** Typed by its owner while Play could not be read for us. */
    val byHand: Boolean = false,
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

    /** In a crew right now, filling or running. */
    val inCrew: Boolean get() = crew?.state in CREW_ACTIVE

    /** A feedback request still collecting. */
    val collecting: Boolean get() = feedback?.state == "collecting"
}

private val CREW_ACTIVE = setOf("waiting", "checking", "starting", "testing")

/** How a badge is coloured: the word decides what it says, the tone how loudly. */
enum class BadgeTone { RUNNING, DONE, ATTENTION, PROBLEM, QUIET }

/** What a badge is about, which decides where tapping it goes. */
enum class BadgeKind { CREW, FEEDBACK, VERIFY, IDLE }

data class AppBadge(
    val text: String,
    val tone: BadgeTone,
    val kind: BadgeKind,
)

/** The badges a tile shows, in the order they are read: crew, then feedback, then unverified. */
fun AppEntry.badges(): List<AppBadge> =
    listOfNotNull(crew?.let(::crewBadge), feedback?.let(::feedbackBadge)) +
        listOfNotNull(
            if (!verified) AppBadge("Not verified", BadgeTone.ATTENTION, BadgeKind.VERIFY) else null,
        )

/** A tile's badges: the same, or one quiet one when nothing is happening, so the row is never empty. */
fun AppEntry.tileBadges(): List<AppBadge> = badges().ifEmpty { listOf(AppBadge("Nothing running", BadgeTone.QUIET, BadgeKind.IDLE)) }

fun crewBadge(crew: AppCrewStatus): AppBadge =
    when (crew.state) {
        "waiting" -> AppBadge("${crew.crewName} · filling", BadgeTone.RUNNING, BadgeKind.CREW)
        "checking" -> AppBadge("${crew.crewName} · members checking", BadgeTone.ATTENTION, BadgeKind.CREW)
        "starting" -> AppBadge("${crew.crewName} · starts at midnight", BadgeTone.RUNNING, BadgeKind.CREW)
        "testing" -> AppBadge("Day ${crew.day} of ${crew.runDays} · ${crew.crewName}", BadgeTone.RUNNING, BadgeKind.CREW)
        "finished" -> AppBadge("Crew test done", BadgeTone.DONE, BadgeKind.CREW)
        "rejected" -> AppBadge("Sent back · fix it", BadgeTone.PROBLEM, BadgeKind.CREW)
        "removed" -> AppBadge("Out of ${crew.crewName}", BadgeTone.PROBLEM, BadgeKind.CREW)
        else -> AppBadge(crew.crewName, BadgeTone.QUIET, BadgeKind.CREW)
    }

fun feedbackBadge(feedback: AppFeedbackStatus): AppBadge =
    when (feedback.state) {
        "collecting" -> AppBadge("Feedback ${feedback.done} of ${feedback.bought}", BadgeTone.RUNNING, BadgeKind.FEEDBACK)
        "done" -> AppBadge("Feedback done", BadgeTone.DONE, BadgeKind.FEEDBACK)
        else -> AppBadge("Feedback closed · ${feedback.done} of ${feedback.bought}", BadgeTone.QUIET, BadgeKind.FEEDBACK)
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
        crew.state == "starting" -> "${crew.crewName} starts at midnight"
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
