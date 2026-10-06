package com.eazyverse.testtrack.ui

/*
 * The community half of TestTrack, in the shape the screen needs it.
 *
 * Plain values the FeedViewModel holds, mapped from what the API sends. The Activity tab's events
 * are worked out by the server from what happened (see /feed/activity); nothing here is seeded.
 */

/** The two halves of the feed: what people are asking for, and what the app noticed by itself. */
enum class FeedTab(
    val label: String,
) {
    /** Developers asking the community to try an app and write something useful back. */
    FEEDBACK("Feedback"),

    /** Nobody writes these. They are posted by things happening: a ship, a finished cohort. */
    ACTIVITY("Activity"),

    /** Every review this account has taken on: in progress, waiting on the developer, decided. */
    MINE("My reviews"),

    /**
     * Anything waiting on a decision from this account.
     *
     * Reviews of your own apps, and cases between two other people you are eligible to help
     * decide. One tab because from the reader's side it is one errand: somebody is waiting.
     */
    WAITING("To check"),
}

/**
 * What a post costs and what a review pays.
 *
 * A number on both sides is the whole point. Feedback that is free to ask for arrives as fourteen
 * requests nobody answers, and feedback that pays nothing gets written as "nice app".
 *
 * The reward is set by whoever posts, which is what makes it worth printing on a card: a figure
 * that is the same everywhere is decoration, and a figure somebody chose is a bid. Somebody in a
 * hurry, or asking about something tedious, can pay their way up the list.
 *
 * What stops everybody simply offering the maximum is that the cost follows the offer. A post buys
 * the selected number of reviews, charged at the chosen reward per review. By default
 * that comes to sixty, which is what a post has always cost here; the old flat price turns out to
 * be this rule with the minimum plugged into it.
 */
object FeedRules {
    /** The least a post may offer. Below this nobody writes forty characters for it. */
    const val MIN_REWARD = 20

    /** The most. Without a ceiling one large balance can outbid every other post on the screen. */
    const val MAX_REWARD = 200

    /** How many reviews a post is buying, and so the multiple its price is charged at. */
    const val REVIEWS_BOUGHT = 3
    const val MIN_REVIEWS = 1
    const val MAX_REVIEWS = 50

    /** What going up costs, given what it is offering. */
    fun postCost(
        reward: Int,
        reviews: Int = REVIEWS_BOUGHT,
    ) = reward * reviews

    /** The focus question on the post form: a sentence. */
    const val ASK_MAX = 200
}

/**
 * Which thing happened.
 *
 * Every one of these is something TestTrack already knows without being told: a cohort filling, a
 * day being reported, a version going live, a post collecting the reviews it paid for. Nothing
 * here is written by a person, which is the point. A feed people have to remember to post to goes
 * quiet inside a week.
 *
 * Ordered the way a cohort lives rather than alphabetically: it fills, it passes the halfway mark,
 * it finishes, an app ships. Then the two that are about people rather than cohorts, and last the
 * one exception to all of it, which an admin writes by hand.
 */
enum class FeedEventKind(
    val label: String,
) {
    /** Fifteen apps found each other and the clock started. */
    STARTED("Under way"),

    /** Day seven, with nobody dropped yet. The point where a cohort is going to make it. */
    HALFWAY("Halfway"),

    /** A whole cohort finished together, which is the thing no single person can do alone. */
    MILESTONE("Milestone"),

    /**
     * Seven days without missing one, and again at the end where one member per group is picked.
     *
     * Seven rather than the full fourteen because a streak is worth saying while it can still be
     * kept up. Named at the halfway point it is encouragement; named only at the end it is a
     * result, and the person it belonged to already knew.
     */
    STREAK("Streak"),

    /** Somebody new got their first app placed into a cohort. */
    JOINED("New here"),

    /** Somebody passed a mark in accepted reviews, counted across every request they reviewed. */
    HELPED("Helped"),

    /** Somebody's first review of somebody else's app was accepted. Once a person. */
    FIRST_REVIEW("First review"),

    /**
     * The one thing here a person wrote: an admin posting to everybody from the console.
     *
     * Everything else on this tab is the app noticing something. This is the exception, and it
     * exists because there are things no rule can infer — an outage, a change to how groups are
     * filled, a week off. Without it those go out by Telegram and the half of the community that
     * is not in the channel never hears them.
     */
    ANNOUNCEMENT("Notice"),
    ;

    companion object {
        /** The server's word for a kind; null for one this build does not know, which is skipped. */
        fun of(wire: String): FeedEventKind? =
            when (wire) {
                "started" -> STARTED
                "halfway" -> HALFWAY
                "milestone" -> MILESTONE
                "streak" -> STREAK
                "joined" -> JOINED
                "helped" -> HELPED
                "first_review" -> FIRST_REVIEW
                "announcement" -> ANNOUNCEMENT
                else -> null
            }
    }
}

/**
 * Something the app posted about itself.
 *
 * Auto-posted on purpose, with one exception. A feed people have to remember to write goes quiet
 * in a week, and the events worth celebrating here are ones TestTrack already knows about the
 * moment they happen. [FeedEventKind.ANNOUNCEMENT] is the exception and the reason the rest can
 * stay automatic: anything the rules cannot infer has somewhere to go.
 */
data class FeedEvent(
    val id: String,
    val kind: FeedEventKind,
    /** The line that is read. Names the thing and whose it was, in one sentence. */
    val headline: String,
    /** The line under it. What the headline leaves out: the numbers, and what happens next. */
    val detail: String,
    val happenedAt: Long,
    /**
     * Initials of whoever it belongs to, as a stack of faces along the bottom.
     *
     * One for an event that is one person's, three for a cohort's, empty where it is an app's
     * doing rather than anybody's. A community feed that never puts a face to anything is a
     * changelog.
     */
    val faces: List<String> = emptyList(),
    /** How many more there were than the faces shown, written as "and 11 more". Zero draws none. */
    val moreFaces: Int = 0,
    /** Awarded by the event itself. Zero where nothing was, which is most of them. */
    val tix: Int = 0,
)

/**
 * Rough on purpose.
 *
 * Nobody reading a feed needs the minute, and an exact stamp invites the reader to work out how
 * long ago that was, which is the one job this string exists to do for them.
 */
fun feedAge(
    at: Long,
    now: Long = System.currentTimeMillis(),
): String {
    val minutes = ((now - at) / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 2L -> "just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days < 2L -> "yesterday"
        else -> "${days}d ago"
    }
}
