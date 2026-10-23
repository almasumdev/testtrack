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
    /** A refusal's outcome: open, upheld or overturned; null if it was never refused. */
    val outcome: String? = null,
    /** When a submitted one accepts itself, in epoch milliseconds. */
    val decideBy: Long? = null,
)

/** Where one review of your app stands, in a few words. */
fun requestReviewStatus(review: RequestReview): String =
    when (review.state) {
        "claimed", "installed", "used" -> "In progress"
        "submitted" -> "Waiting on you"
        "approved" -> if (review.outcome == "overturned") "Paid after a dispute" else "Accepted"
        "disputed" -> "Refused · members deciding"
        "rejected" -> "Refused · upheld"
        "abandoned" -> "Given up"
        "expired" -> "Ran out"
        else -> review.state
    }
