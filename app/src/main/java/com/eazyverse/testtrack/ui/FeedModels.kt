package com.eazyverse.testtrack.ui

/*
 * The community half of TestTrack, in the shape the screen needs it.
 *
 * Nothing here touches Firestore yet, deliberately. The feed is a new product surface rather than
 * a new view of data the app already holds, and settling how it reads and what it costs is the
 * part worth getting wrong cheaply — collections, rules and indexes can only be designed once the
 * screen has stopped moving. Every type below is a plain value the FeedViewModel holds in memory;
 * swapping the seed in SampleFeed for a query later changes that one object and nothing on the
 * screen.
 */

/** The two halves of the feed: what people are asking for, and what the app noticed by itself. */
enum class FeedTab(
    val label: String,
) {
    /** Developers asking the community to try an app and write something useful back. */
    FEEDBACK("Feedback"),

    /** Nobody writes these. They are posted by things happening: a ship, a finished cohort. */
    ACTIVITY("Activity"),

    /**
     * Anything waiting on a decision from this account.
     *
     * Reviews of your own apps, and cases between two other people you are eligible to help
     * decide. One tab because from the reader's side it is one errand: somebody is waiting.
     */
    WAITING("Waiting"),
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
 * [REVIEWS_BOUGHT] reviews, so it is charged that many times what it is offering. At the floor
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

    /** Offered when nothing is chosen, and the figure the sample posts are seeded around. */
    const val REVIEW_REWARD = MIN_REWARD

    /** What going up costs, given what it is offering. */
    fun postCost(reward: Int) = reward * REVIEWS_BOUGHT

    /** Under this it is a reaction, not feedback, and the confirm button stays off. */
    const val REVIEW_MIN = 40

    /** Long enough for a paragraph about one screen. */
    const val REVIEW_MAX = 600

    /** Both fields on the post form. Names are short, asks are a sentence. */
    const val APP_NAME_MAX = 60
    const val ASK_MAX = 200
}

/** A developer asking the community to open their app and say something useful about it. */
data class FeedRequest(
    val id: String,
    val appName: String,
    /** Display name of whoever posted it. Blank is never shown, so seed it. */
    val author: String,
    /** What they want looked at. The reason this post is worth answering rather than scrolling. */
    val ask: String,
    val postedAt: Long,
    val reviews: Int = 0,
    val reward: Int = FeedRules.REVIEW_REWARD,
    /** This account has already answered it, so it is settled rather than actionable. */
    val reviewed: Boolean = false,
    /** This account posted it, so the card reports rather than asks. */
    val mine: Boolean = false,
)

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

    /** Somebody's app cleared its fortnight and went live. */
    SHIPPED("Shipped"),

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

    /** Somebody passed a total worth marking, counted across every cohort they have been in. */
    HELPED("Helped"),

    /**
     * The one thing here a person wrote: an admin posting to everybody from the console.
     *
     * Everything else on this tab is the app noticing something. This is the exception, and it
     * exists because there are things no rule can infer — an outage, a change to how groups are
     * filled, a week off. Without it those go out by Telegram and the half of the community that
     * is not in the channel never hears them.
     */
    ANNOUNCEMENT("Notice"),
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

/**
 * Stand-in content, until the feed has a database behind it.
 *
 * Seeded relative to now rather than with fixed dates, so the ages on screen stay believable
 * however long this build sits on a phone, and so a post made during a session is visibly newer
 * than everything under it.
 */
object SampleFeed {
    /** What the header chip starts at. Enough to post twice, so the economy can be tried out. */
    const val STARTING_TIX = 340

    private const val HOUR = 3_600_000L
    private const val DAY = 24L * HOUR

    fun requests(now: Long = System.currentTimeMillis()) =
        listOf(
            FeedRequest(
                id = "req-bazar",
                appName = "Local Bazar",
                author = "Tanvir",
                ask = "The cart empties itself if you background the app. Does it for you too?",
                postedAt = now - 2L * HOUR,
                reviews = 3,
                reward = 40,
            ),
            FeedRequest(
                id = "req-weather",
                appName = "Barisal Weather",
                author = "Habib",
                ask = "Looking for a read on the home screen before I put it in front of a crew.",
                postedAt = now - 5L * HOUR,
                reviews = 6,
                reward = 20,
            ),
            FeedRequest(
                id = "req-rickshaw",
                appName = "Rickshaw Fare Calc",
                author = "You",
                ask = "Is the fare breakdown clear without reading the help text?",
                postedAt = now - 3L * DAY,
                reviews = 6,
                reward = 60,
                mine = true,
            ),
            FeedRequest(
                id = "req-nikosh",
                appName = "Nikosh Budget",
                author = "Rafiul",
                ask = "Second opinion on the Bangla numerals in the monthly total.",
                postedAt = now - 4L * DAY,
                reviews = 11,
                reward = 25,
                reviewed = true,
            ),
        )

    fun events(now: Long = System.currentTimeMillis()) =
        listOf(
            FeedEvent(
                id = "ev-shipped",
                kind = FeedEventKind.SHIPPED,
                headline = "Doctorbari is live on Play Store",
                detail = "Fourteen days, thirteen testers, and it cleared closed testing today.",
                happenedAt = now - 2L * HOUR,
                faces = listOf("J", "R", "S"),
                moreFaces = 10,
            ),
            FeedEvent(
                id = "ev-notice",
                kind = FeedEventKind.ANNOUNCEMENT,
                headline = "Crews now fill on Sunday nights",
                detail = "Submit by Saturday and you are in the next one. Posted by the team.",
                happenedAt = now - 4L * HOUR,
            ),
            FeedEvent(
                id = "ev-milestone",
                kind = FeedEventKind.MILESTONE,
                headline = "Crew 05 finished its 14 days together",
                detail = "Every app in the crew cleared the full fortnight. Nobody dropped.",
                happenedAt = now - 1L * DAY,
                faces = listOf("J", "R", "S"),
                moreFaces = 11,
            ),
            FeedEvent(
                id = "ev-halfway",
                kind = FeedEventKind.HALFWAY,
                headline = "Crew 06 is halfway with all fourteen still in",
                detail = "Day 7 of 14, and not one missed day between them.",
                happenedAt = now - 1L * DAY - 6L * HOUR,
                faces = listOf("T", "H", "N"),
                moreFaces = 11,
            ),
            FeedEvent(
                id = "ev-streak-end",
                kind = FeedEventKind.STREAK,
                headline = "Sumaiya is Crew 07's streak of the run",
                detail = "One per crew at the end, and this one missed nothing at all.",
                happenedAt = now - 2L * DAY,
                faces = listOf("S"),
                tix = 15,
            ),
            FeedEvent(
                id = "ev-streak-seven",
                kind = FeedEventKind.STREAK,
                headline = "Nadia has seven days in a row in Crew 08",
                detail = "Half the run, no gaps. Seven more and the crew's pick is hers.",
                happenedAt = now - 2L * DAY - 9L * HOUR,
                faces = listOf("N"),
                tix = 15,
            ),
            FeedEvent(
                id = "ev-started",
                kind = FeedEventKind.STARTED,
                headline = "Crew 08 filled up and started today",
                detail = "Fifteen apps, fourteen days. First reports are due tonight.",
                happenedAt = now - 3L * DAY,
                faces = listOf("R", "M", "A"),
                moreFaces = 11,
            ),
            FeedEvent(
                id = "ev-joined",
                kind = FeedEventKind.JOINED,
                headline = "Rafiul placed his first app",
                detail = "Nikosh Budget went into Crew 08. Say hello by opening it tonight.",
                happenedAt = now - 3L * DAY - 4L * HOUR,
                faces = listOf("R"),
            ),
            FeedEvent(
                id = "ev-helped",
                kind = FeedEventKind.HELPED,
                headline = "Habib has helped ten apps reach production",
                detail = "Ten fortnights, one evening at a time.",
                happenedAt = now - 4L * DAY,
                faces = listOf("H"),
                tix = 50,
            ),
        )
}
