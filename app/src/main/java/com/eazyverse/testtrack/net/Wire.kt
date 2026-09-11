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

/**
 * A body with nothing in it.
 *
 * Several endpoints take no arguments but are still POSTs, because they change something. Sending
 * `{}` rather than nothing keeps one code path through [Api.post] instead of a branch on whether
 * there is a body.
 */
@Serializable
class Empty
