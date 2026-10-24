package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * What the API sends and expects, as Kotlin.
 *
 * Hand written, and checked against the real API by ApiContractTest so the two sides cannot
 * drift apart unnoticed.
 */

@Serializable
data class SessionIn(
    @SerialName("id_token") val idToken: String,
    @SerialName("device_fp") val deviceFp: String = "",
    val timezone: String = "UTC",
)

@Serializable
data class RefreshIn(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class Tokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
)

@Serializable
data class LookupIn(
    val query: String,
    /** Only on a closed testing track, so only the signed-in browser can see it. */
    val closed: Boolean = false,
)

/**
 * What Play shows for one app, read by the server through a signed-in browser.
 *
 * Scraped rather than fetched from an API, because a closed testing listing is invisible to
 * anybody who is not on the tester group and Play publishes no API for it. So it is shown to the
 * submitter to confirm rather than taken as fact: the page it came from can be restyled whenever
 * Google likes.
 */
@Serializable
data class Listing(
    val `package`: String,
    val name: String,
    val icon: String = "",
    val developer: String = "",
)

@Serializable
data class GroupIn(
    val email: String,
)

/**
 * What the server knows about this account's place on the Google Group.
 *
 * `added` is always false and is kept because the server still sends it. Nobody can be added to
 * the group by us; joining is the tester's own tap. See docs/group-membership.md in the backend.
 */
@Serializable
data class GroupOut(
    val group: String,
    val email: String,
    @SerialName("is_member") val isMember: Boolean,
    val added: Boolean = false,
)

@Serializable
data class Me(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String,
    val tix: Int,
    val timezone: String,
    @SerialName("is_admin") val isAdmin: Boolean,
    // Whether this account is on the Google Group Play checks testers against. Answered from the
    // server's own copy of the member list, so it costs nothing and is here on every launch.
    @SerialName("in_group") val inGroup: Boolean = false,
    // Experience is the same number the board ranks by, which is the work this account has done.
    // Nothing spends it. Defaulted so a phone on this build still parses an older server's answer
    // rather than failing the whole call over four integers.
    val exp: Int = 0,
    val level: Int = 1,
    // How far into the level, and how wide the level is. The pair rather than totals, so the bar
    // is the two numbers it draws and the phone does no arithmetic the server could disagree with.
    @SerialName("exp_into_level") val expIntoLevel: Int = 0,
    @SerialName("exp_for_next") val expForNext: Int = 0,
)

@Serializable
data class PostIn(
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    val ask: String,
    val reward: Int,
    @SerialName("is_live") val isLive: Boolean,
    @SerialName("reviews_bought") val reviewsBought: Int = 3,
    @SerialName("request_id") val requestId: String? = null,
    @SerialName("app_id") val appId: String? = null,
)

@Serializable
data class ApiPost(
    val id: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    /** The app's icon as its owner's My apps has it; empty where it has none yet, or from an older server. */
    val icon: String = "",
    val ask: String,
    @SerialName("is_live") val isLive: Boolean,
    val reward: Int,
    @SerialName("reviews_bought") val reviewsBought: Int,
    @SerialName("reviews_done") val reviewsDone: Int,
    /** Still there to take; null from an older server, where bought less done stands in. */
    @SerialName("slots_open") val slotsOpen: Int? = null,
    @SerialName("owner_name") val ownerName: String,
    val mine: Boolean,
    @SerialName("my_review_id") val myReviewId: String? = null,
    @SerialName("my_state") val myState: String? = null,
    @SerialName("created_at") val createdAt: String,
)

/** One thing that happened in the community, as the Activity tab shows it. */
@Serializable
data class ApiActivityEvent(
    val id: String,
    val kind: String,
    val headline: String,
    val detail: String = "",
    @SerialName("happened_at") val happenedAt: String,
    val faces: List<String> = emptyList(),
    @SerialName("more_faces") val moreFaces: Int = 0,
    val tix: Int = 0,
)

@Serializable
data class ApiActivity(
    val events: List<ApiActivityEvent> = emptyList(),
)

/** A review of one of the reader's own requests, as its owner sees it afterwards. */
@Serializable
data class ApiOwnerReview(
    val id: String,
    val state: String,
    @SerialName("proof_ref") val proofRef: String = "",
    val written: String = "",
    val reason: String = "",
    @SerialName("reviewer_name") val reviewerName: String = "",
    val outcome: String? = null,
    @SerialName("decide_by") val decideBy: String? = null,
)

/** One of the reader's own requests, with every review it has had. */
@Serializable
data class ApiMyPost(
    val id: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String = "",
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
    @SerialName("is_live") val isLive: Boolean = true,
    val reward: Int = 0,
    @SerialName("reviews_bought") val reviewsBought: Int = 0,
    @SerialName("reviews_done") val reviewsDone: Int = 0,
    @SerialName("slots_open") val slotsOpen: Int = 0,
    val closed: Boolean = false,
    val refundable: Int = 0,
    val reviews: List<ApiOwnerReview> = emptyList(),
)

@Serializable
data class ClosedPost(
    val post: ApiMyPost,
    val refunded: Int,
)

@Serializable
data class PostPage(
    val posts: List<ApiPost>,
    @SerialName("next_cursor") val nextCursor: String? = null,
)

@Serializable
data class ClaimIn(
    @SerialName("device_fp") val deviceFp: String,
)

@Serializable
data class UsedIn(
    @SerialName("used_ms") val usedMs: Long,
    /** Calendar days the app was used on since the review was taken. */
    val days: Int = 0,
)

@Serializable
data class SubmitIn(
    @SerialName("proof_ref") val proofRef: String = "",
    val written: String = "",
    @SerialName("drive_token") val driveToken: String = "",
)

@Serializable
data class KeptIn(
    val installed: Boolean,
)

@Serializable
data class RejectIn(
    val reason: String,
)

@Serializable
data class ApiReview(
    val id: String,
    @SerialName("post_id") val postId: String,
    val state: String,
    @SerialName("used_ms") val usedMs: Long,
    @SerialName("needed_ms") val neededMs: Long,
    @SerialName("proof_ref") val proofRef: String,
    val written: String,
    val reason: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("decided_at") val decidedAt: String? = null,
    /** A live review waiting on its second day of use: when the app may be used again for it. */
    @SerialName("returns_at") val returnsAt: String? = null,
    /** A live review sent: from when the phone is asked whether the app is still installed. */
    @SerialName("keep_from") val keepFrom: String? = null,
    /** What the phone answered, once it has. */
    val kept: Boolean? = null,
    /** Calendar days the app has been used on for it, as this phone counted them. */
    @SerialName("days_used") val daysUsed: Int = 0,
    // Only on /reviews/mine: the app the review is for, so the list needs no second call.
    @SerialName("app_name") val appName: String = "",
    @SerialName("package_name") val packageName: String = "",
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
    @SerialName("is_live") val isLive: Boolean = false,
    val reward: Int = 0,
    // Only on /reviews/waiting: who sent it, and when it accepts itself.
    @SerialName("reviewer_name") val reviewerName: String = "",
    @SerialName("decide_by") val decideBy: String? = null,
)

@Serializable
data class ApiDispute(
    val id: String,
    @SerialName("review_id") val reviewId: String,
    @SerialName("app_name") val appName: String,
    val ask: String,
    @SerialName("is_live") val isLive: Boolean,
    @SerialName("proof_ref") val proofRef: String,
    val written: String,
    val reason: String,
    val stake: Int,
    @SerialName("votes_cast") val votesCast: Int,
    @SerialName("votes_needed") val votesNeeded: Int,
    val outcome: String,
    @SerialName("package_name") val packageName: String = "",
    val reward: Int = 0,
    /** The app's icon as its owner's My apps has it; empty where it has none, or from an older server. */
    val icon: String = "",
)

@Serializable
data class VoteIn(
    @SerialName("for_reviewer") val forReviewer: Boolean,
)

/**
 * The one error shape the API uses, everywhere.
 *
 * [code] is stable and is what the app branches on. [message] is written for a person and may be
 * reworded at any time, so nothing here ever parses it.
 */
@Serializable
data class ApiErrorBody(
    val code: String,
    val message: String,
)

@Serializable
data class ApiErrorOut(
    val error: ApiErrorBody,
)

// ---------------------------------------------------------------------------------- crews

@Serializable
data class SeatIn(
    /** Optional. Empty takes the name Play shows for the app. */
    @SerialName("app_name") val appName: String = "",
    /** A package name or a Play link carrying one. The server reads either. */
    @SerialName("package_name") val packageName: String,
    val notes: String = "",
    @SerialName("device_fp") val deviceFp: String,
    val timezone: String = "UTC",
    @SerialName("app_id") val appId: String? = null,
)

@Serializable
data class MeIn(
    @SerialName("display_name") val displayName: String,
    /** The photo to keep, by its URL. Empty clears it back to the initial. */
    @SerialName("photo_ref") val photoRef: String = "",
    @SerialName("drive_token") val driveToken: String = "",
)

@Serializable
data class VisitIn(
    /** Real foreground milliseconds, measured on this phone. The server holds it to its floor. */
    @SerialName("used_ms") val usedMs: Long,
)

@Serializable
data class SeatVoteIn(
    val installed: Boolean,
    val reason: String = "",
)

@Serializable
data class ReportIn(
    /** What went wrong, for the app's owner. Required. */
    val reason: String,
)

@Serializable
data class PushTokenIn(
    val token: String,
)

@Serializable
data class ApiSeat(
    val id: String,
    @SerialName("crew_id") val crewId: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    /** Play's icon for the app, when it was added through My apps; empty otherwise. */
    val icon: String = "",
    val notes: String = "",
    @SerialName("owner_name") val ownerName: String,
    /** checking, confirmed, rejected, withdrawn or removed. */
    val state: String,
    val mine: Boolean,
    /** Reports filed against it so far. */
    val reports: Int = 0,
    /** This account reported it, which excuses this account from it. */
    val reported: Boolean = false,
    /** For an app the Play check could not look at: members' answers so far, and this account's. */
    val yes: Int = 0,
    val no: Int = 0,
    @SerialName("my_vote") val myVote: Boolean? = null,
    /** What the reports said. Only ever sent to the seat's owner, without who said it. */
    val reasons: List<String> = emptyList(),
    /** Why it left the crew. Only ever sent to the seat's owner. */
    @SerialName("out_reason") val outReason: String = "",
    /** Opened by this account on its own current day. */
    @SerialName("opened_today") val openedToday: Boolean = false,
    /** The owner's own day of the fortnight, in their calendar. Zero before their midnight. */
    val day: Int = 0,
    /** What the owner has opened today, out of what they owe today. */
    @SerialName("done_today") val doneToday: Int = 0,
    @SerialName("owed_today") val owedToday: Int = 0,
    /** The owner has opened everything they owe today. */
    @SerialName("night_done") val nightDone: Boolean = false,
    /** Finished days the owner left short. */
    val missed: Int = 0,
    /** The owner's yesterday was one of them: one more and their app is out. */
    @SerialName("missed_yesterday") val missedYesterday: Boolean = false,
    /** This member opened the reader's own app on their current day, and when (ISO time). */
    @SerialName("opened_mine_today") val openedMineToday: Boolean = false,
    @SerialName("opened_mine_at") val openedMineAt: String? = null,
    /** On how many of their days this member has opened the reader's app. */
    @SerialName("opened_mine_days") val openedMineDays: Int = 0,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class ApiCrew(
    val id: String,
    val number: Int,
    val name: String,
    val state: String,
    /** Confirmed seats. A seat still being checked is somebody's intention, not a place filled. */
    val filled: Int,
    /** Seats holding a place either way, which is what decides whether there is room. */
    val taken: Int,
    /** Those seats' app icons, confirmed first; empty where an app has none. Never names. */
    val icons: List<String> = emptyList(),
    val seats: Int,
    @SerialName("run_days") val runDays: Int,
    /** This account's own day of the fortnight. Zero before their own midnight. */
    val day: Int = 0,
    @SerialName("starts_in_ms") val startsInMs: Long = 0,
    @SerialName("my_seat_id") val mySeatId: String? = null,
    @SerialName("filled_at") val filledAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    /** Only sent for a crew this account is in. */
    val members: List<ApiSeat> = emptyList(),
)

@Serializable
data class CrewList(
    val mine: List<ApiCrew> = emptyList(),
    /** The crew a new seat would go into, or null if there is nowhere to go. */
    val open: ApiCrew? = null,
    /** Why a seat cannot be taken right now, in words the screen can show as they arrive. */
    val blocked: String? = null,
)

// ------------------------------------------------------- standing, apps and the profile

@Serializable
data class BoardRow(
    val id: String,
    val name: String,
    @SerialName("photo_url") val photoUrl: String = "",
    /** Where they sit, counted from one. */
    val place: Int,
    /** Apps opened on a testing night. The work. */
    val opens: Int,
    /** Reviews of theirs a developer accepted. */
    val reviews: Int,
    /** What those two come to, weighted. */
    val points: Int,
)

@Serializable
data class ApiBoard(
    val rows: List<BoardRow> = emptyList(),
    /** This account's own row, whether or not it is in [rows]. */
    val mine: BoardRow? = null,
    /**
     * What each kind of work is worth.
     *
     * Sent rather than held here, so changing what a review is worth is one change on the server
     * and not a release of the app.
     */
    @SerialName("open_points") val openPoints: Int = 1,
    @SerialName("review_points") val reviewPoints: Int = 5,
)

@Serializable
data class ApiProfile(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String = "",
    val timezone: String,
    val tix: Int,
    val opens: Int,
    val reviews: Int,
    val points: Int,
    /** Zero before this account has done anything, since nobody is ranked before they start. */
    val place: Int,
    @SerialName("crews_running") val crewsRunning: Int,
    /** Of those, the ones that have filled, which is what the nightly work is counted from. */
    @SerialName("crews_filled") val crewsFilled: Int = 0,
    @SerialName("crews_finished") val crewsFinished: Int,
    @SerialName("joined_at") val joinedAt: String,
)

/**
 * A body with nothing in it.
 *
 * Several endpoints take no arguments but are still POSTs, because they change something. Sending
 * `{}` rather than nothing keeps one code path through [Api.post] instead of a branch on whether
 * there is a body.
 */
@Serializable
class Empty

@Serializable
data class FeedbackOptions(
    val version: Int,
    @SerialName("min_reviews") val minReviews: Int,
    @SerialName("max_reviews") val maxReviews: Int,
)
