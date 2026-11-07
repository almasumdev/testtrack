package com.eazyverse.testtrack.domain

/*
 * The owner's side of a feedback request, after the asking: what came in and how each ended.
 * Plain values, like Waiting.kt beside it.
 */

/** One of this account's own requests for feedback. */
data class Request(
    val id: String,
    val appName: String,
    val packageName: String = "",
    val isLive: Boolean = true,
    val reward: Int = 0,
    val bought: Int = 0,
    val done: Int = 0,
    /** Slots nobody has taken yet. */
    val open: Int = 0,
    val closed: Boolean = false,
    /** What closing it now would give back. */
    val refundable: Int = 0,
    val reviews: List<RequestReview> = emptyList(),
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
    /** Off the feed because two testers could not install it, until it is relisted. */
    val unlisted: Boolean = false,
    /** Why testers could not install it, newest first, without who said so. */
    val reports: List<InstallReport> = emptyList(),
    /** What it asks of each reviewer (stars, written or detailed), and the characters that takes. */
    val feedback: String = "stars",
    val needsChars: Int = 0,
    /** Its ready-written reviews, in the order they are handed out. */
    val readyTexts: List<String> = emptyList(),
) {
    /** Reviews still on their way, or waiting on the owner or the members. */
    val underWay: Int get() = reviews.count { it.state in setOf("claimed", "installed", "used", "submitted", "disputed") }
}

/** One review a request has had. */
data class RequestReview(
    val id: String,
    val state: String,
    val reviewerName: String = "",
    val proofRef: String = "",
    val written: String = "",
    val reason: String = "",
    /** A refusal's outcome: open, upheld, overturned or accepted; null if it was never refused. */
    val outcome: String? = null,
    /** When a submitted one accepts itself, in epoch milliseconds. */
    val decideBy: Long? = null,
    /** Whether they used the ready-written review they were given: used, changed or own. */
    val textUse: String? = null,
) {
    /** Refused, with the members still deciding: the owner can still accept it. */
    val canAcceptLater: Boolean get() = state == "disputed" && outcome == "open"
}

/** How a sent text compares with the ready-written review its reviewer was given, in words. */
fun textUseLabel(use: String?): String? =
    when (use) {
        "used" -> "Used your ready review"
        "changed" -> "Changed your ready review"
        "own" -> "In their own words"
        else -> null
    }

/** Where one review of your app stands, in a few words. */
fun requestReviewStatus(review: RequestReview): String =
    when (review.state) {
        "claimed", "installed", "used" -> "In progress"
        "submitted" -> "Waiting on you"
        "approved" ->
            when (review.outcome) {
                "overturned" -> "Paid after a dispute"
                "accepted" -> "Accepted after all"
                else -> "Accepted"
            }
        "disputed" -> "Refused, members deciding"
        "rejected" -> "Refused and upheld"
        "abandoned" -> "Given up"
        "expired" -> "Ran out"
        else -> review.state
    }

/** One tester who could not install the app, in the fixed reason's words and their own note. */
data class InstallReport(
    val label: String,
    val note: String = "",
)
