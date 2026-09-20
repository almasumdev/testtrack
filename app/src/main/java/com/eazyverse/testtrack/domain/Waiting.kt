package com.eazyverse.testtrack.domain

/*
 * What the Waiting screen draws.
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
    /** The words, for an app still in closed testing where there is no Play review to point at. */
    val written: String = "",
    /** The screenshot, for one that is live. Empty when there is none. */
    val proofRef: String = "",
)

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
)
