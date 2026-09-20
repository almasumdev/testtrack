package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * What the API sends and expects, as Kotlin.
 *
 * Hand written for now. The agreed plan is to generate these from the API's own OpenAPI document
 * so the two sides cannot drift, and that is still the plan; what stopped it today is that the
 * generator is a build plugin and a pile of generated source, and none of that is worth landing
 * before the endpoints have settled. Until then these are checked against the real API by the
 * tests in ApiWireTest, which is the same promise made a cheaper way.
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
data class Me(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("photo_url") val photoUrl: String,
    val tix: Int,
    val timezone: String,
    @SerialName("is_admin") val isAdmin: Boolean,
)

@Serializable
data class PostIn(
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    val ask: String,
    val reward: Int,
    @SerialName("is_live") val isLive: Boolean,
)

@Serializable
data class ApiPost(
    val id: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    val ask: String,
    @SerialName("is_live") val isLive: Boolean,
    val reward: Int,
    @SerialName("reviews_bought") val reviewsBought: Int,
    @SerialName("reviews_done") val reviewsDone: Int,
    @SerialName("owner_name") val ownerName: String,
    val mine: Boolean,
    @SerialName("my_review_id") val myReviewId: String? = null,
    @SerialName("my_state") val myState: String? = null,
    @SerialName("created_at") val createdAt: String,
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
)

@Serializable
data class SubmitIn(
    @SerialName("proof_ref") val proofRef: String = "",
    val written: String = "",
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
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    val notes: String = "",
    @SerialName("device_fp") val deviceFp: String,
    val timezone: String = "UTC",
)

@Serializable
data class VerdictIn(
    val ok: Boolean,
    val reason: String = "",
)

@Serializable
data class ApiSeat(
    val id: String,
    @SerialName("crew_id") val crewId: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    val notes: String = "",
    @SerialName("owner_name") val ownerName: String,
    val state: String,
    val greens: Int,
    val reds: Int,
    val mine: Boolean,
    /** This account's own answer, or null while it is still theirs to give. */
    @SerialName("my_verdict") val myVerdict: Boolean? = null,
    /** Why it was turned down. Only ever sent to the seat's owner. */
    val reasons: List<String> = emptyList(),
    /**
     * Opened by this account on its current day.
     *
     * On somebody else's seat it means "I have done this one tonight". On your own it means your
     * whole night is finished, which is the same question asked of everything you owe.
     */
    @SerialName("opened_today") val openedToday: Boolean = false,
    /** Days already let go by unfinished. Only ever filled in on your own seat. */
    val missed: Int = 0,
    /** Which day of the fortnight this account is on. */
    @SerialName("days_done") val daysDone: Int = 0,
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
    @SerialName("crews_finished") val crewsFinished: Int,
    @SerialName("joined_at") val joinedAt: String,
)

@Serializable
data class ApiMyApp(
    val id: String,
    /** "crew" or "post". One list, both halves of the product. */
    val kind: String,
    @SerialName("app_name") val appName: String,
    @SerialName("package_name") val packageName: String,
    /** waiting, checking, testing, finished, rejected, withdrawn, collecting, done. */
    val state: String,
    /** The crew's name, or Circle. */
    val where: String,
    @SerialName("crew_id") val crewId: String? = null,
    val day: Int = 0,
    @SerialName("run_days") val runDays: Int = 0,
    /** People testing it, or reviews collected so far. */
    val testers: Int = 0,
    val greens: Int = 0,
    val reds: Int = 0,
    @SerialName("reviews_bought") val reviewsBought: Int = 0,
    val reward: Int = 0,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class MyApps(
    val apps: List<ApiMyApp> = emptyList(),
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
