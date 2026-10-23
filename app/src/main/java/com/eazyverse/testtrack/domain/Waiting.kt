package com.eazyverse.testtrack.domain

/*
 * What the To check tab draws.
 *
 * The first inhabitants of domain/, and the shape everything else is headed for: plain values
 * with no imports at all, which is what lets a screen be drawn without a network and a rule be
 * tested without a device. The wire types stay in net/ where the database's shape belongs, and
 * the mapping between them belongs to whoever holds the state.
 *
 * See docs/app-rules.md.
 */

/** One review of your own app, waiting for you to accept or refuse it. */
data class Waiting(
    val id: String,
    /** The app it is a review of, resolved for the reader. A post id means nothing on a card. */
    val appName: String,
    /** The reviewer's optional note, beside what they left on Google Play. */
    val written: String = "",
    /** The screenshot of what they left on Google Play. Empty when there is none. */
    val proofRef: String = "",
    /** Who sent it, by the name they review under. Blank from an older server. */
    val reviewerName: String = "",
    /** What accepting it pays them. */
    val reward: Int = 0,
    /** Live on Play, rather than in closed testing. */
    val isLive: Boolean = true,
    /** When it accepts itself if nobody answers, in epoch milliseconds; null when unknown. */
    val decideBy: Long? = null,
    /** The app's package, for its icon and its Play page. Blank from an older server. */
    val packageName: String = "",
    /**
     * For a live app: whether it was still on their phone a few days after they reviewed it. False
     * is worth the owner's look before it accepts itself; null when not asked yet.
     */
    val kept: Boolean? = null,
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
)

private const val HOUR = 3_600_000L

/**
 * How long is left before a review accepts itself, as a chip says it: "1d 20h", "6h", "25m".
 *
 * Whole units, rounded down, so it never promises more time than there is. Past the deadline it
 * says so rather than counting into negatives; the server settles it on its own sweep.
 */
fun timeLeft(
    until: Long,
    now: Long,
): String {
    val left = until - now
    if (left <= 0) return "Due now"
    val hours = left / HOUR
    return when {
        hours >= 24 -> if (hours % 24 == 0L) "${hours / 24}d" else "${hours / 24}d ${hours % 24}h"
        hours >= 1 -> "${hours}h"
        else -> "${maxOf(1, left / 60_000)}m"
    }
}

/** Under twelve hours left: worth drawing in a warning colour. */
fun closeTo(
    until: Long,
    now: Long,
): Boolean = until - now < 12 * HOUR

/**
 * One case between two other people that this account can help decide.
 *
 * Carries what the developer said, because three strangers decide it on that sentence and one of
 * the two people involved pays for the decision.
 */
data class Case(
    val id: String,
    val appName: String,
    val reason: String,
    val written: String = "",
    val proofRef: String = "",
    val stake: Int = 0,
    val votesCast: Int = 0,
    val votesNeeded: Int = 0,
    val packageName: String = "",
    /** What the review in dispute would pay. */
    val reward: Int = 0,
    val isLive: Boolean = true,
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
)
