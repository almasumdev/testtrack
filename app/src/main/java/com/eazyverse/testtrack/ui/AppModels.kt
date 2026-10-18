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
enum class BadgeKind { CREW, FEEDBACK, VERIFY }

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

/** How the app is known to be real, said on its page. */
fun verifiedLine(app: AppEntry): String =
    when (app.verifiedBy) {
        "play" -> "Verified on Google Play."
        "crew" -> "Verified by crew members who installed it."
        "review" -> "Verified by a reviewer who installed it."
        else ->
            "Not verified yet: Play couldn't be checked when you added it. It's verified once Play " +
                "shows it, two crew members install it, or you accept a review of it."
    }

/** What a crew is doing for this app, in a sentence for its page. */
fun crewLine(app: AppEntry): String {
    val crew = app.crew
    val done = if (app.crewsDone > 1) " ${app.crewsDone} crew tests done so far." else ""
    return when {
        app.live && crew == null -> "Live apps take feedback, not crews."
        crew == null -> "Not in a crew yet. A crew is 14 testers opening it every day for 14 days."
        crew.state == "waiting" -> "In ${crew.crewName}, waiting for it to fill.$done"
        crew.state == "checking" -> "In ${crew.crewName}. Members are trying to install it.$done"
        crew.state == "starting" -> "${crew.crewName} is full. Day 1 starts at midnight.$done"
        crew.state == "testing" -> "Day ${crew.day} of ${crew.runDays} in ${crew.crewName}.$done"
        crew.state == "finished" -> "Its crew test is done.$done"
        crew.state == "rejected" -> "${crew.crewName} sent it back. Fix it and put it in again."
        crew.state == "removed" -> "It left ${crew.crewName} after missed days."
        else -> crew.crewName
    }
}

/** What feedback is doing for this app, in a sentence for its page. */
fun feedbackLine(app: AppEntry): String {
    val feedback = app.feedback ?: return "No feedback asked for yet."
    return when (feedback.state) {
        "collecting" -> "${feedback.done} of ${feedback.bought} reviews in."
        "done" -> "All ${feedback.bought} reviews in. You can ask again."
        else -> "Closed with ${feedback.done} of ${feedback.bought} reviews. You can ask again."
    }
}
