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
    /** Sign-in only: given, phone_used (this phone gave the starter tix to another account) or none. */
    val starter: String = "none",
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
    /** Set while it is off the feed because two testers could not install it. */
    @SerialName("unlisted_at") val unlistedAt: String? = null,
    /** Why testers could not install it, newest first, without who said so. */
    val reports: List<ApiPostReport> = emptyList(),
)

@Serializable
data class ApiPostReport(
    val reason: String,
    val label: String,
    val note: String = "",
    val at: String = "",
)

/** A tester who could not install an app: one of the fixed reasons, and a note for "other". */
@Serializable
data class PostReportIn(
    val reason: String,
    val note: String,
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
    /** The shuffled feed's own cursor, sent back as `after`; null from a server that does not shuffle. */
    val next: String? = null,
)

/** What waits on this account, one number a tab: each the sum of things to do now. */
@Serializable
data class ApiActions(
    val crew: Int = 0,
    val circle: Int = 0,
    val apps: Int = 0,
    val parts: ApiActionParts = ApiActionParts(),
)

/** The pieces each tab's number is made of. */
@Serializable
data class ApiActionParts(
    @SerialName("crew_tonight") val crewTonight: Int = 0,
    @SerialName("crew_verdicts") val crewVerdicts: Int = 0,
    @SerialName("reviews_going") val reviewsGoing: Int = 0,
    @SerialName("to_approve") val toApprove: Int = 0,
    @SerialName("cases_to_vote") val casesToVote: Int = 0,
    @SerialName("apps_to_verify") val appsToVerify: Int = 0,
    @SerialName("requests_unlisted") val requestsUnlisted: Int = 0,
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
    /** Why it ended when its reviewer did not give it up: reported, request_deleted, member_deleted, team. */
    @SerialName("ended_reason") val endedReason: String? = null,
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
    /** When the app was first used for it, as the server heard; null before then, or from an older server. */
    @SerialName("first_used_at") val firstUsedAt: String? = null,
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

@Serializable
data class MeIn(
    @SerialName("display_name") val displayName: String,
    /** The photo to keep, by its URL. Empty clears it back to the initial. */
    @SerialName("photo_ref") val photoRef: String = "",
    @SerialName("drive_token") val driveToken: String = "",
)

@Serializable
data class PushTokenIn(
    val token: String,
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
