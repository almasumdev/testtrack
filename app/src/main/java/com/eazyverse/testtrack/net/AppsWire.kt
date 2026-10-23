package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * My apps, as the server says it: each app added once, and where it is with crews and feedback.
 * See the backend's app/services/apps.py for the rules these flags carry.
 */

/**
 * Adding an app: what was pasted, and where people find it. `fromPhone` when it was picked from the
 * apps on this phone, with the name and icon (base64) it has here.
 */
@Serializable
data class AppIn(
    val query: String,
    val live: Boolean = false,
    val name: String = "",
    @SerialName("from_phone") val fromPhone: Boolean = false,
    @SerialName("icon_data") val iconData: String = "",
)

/** A new icon for an app, base64: the one on this phone, or a picture. */
@Serializable
data class IconIn(
    val data: String,
)

@Serializable
data class AppCrew(
    /** waiting, checking, starting, testing, finished, rejected or removed. */
    val state: String,
    @SerialName("crew_id") val crewId: String,
    @SerialName("crew_name") val crewName: String,
    @SerialName("seat_id") val seatId: String,
    val day: Int = 0,
    @SerialName("run_days") val runDays: Int = 0,
)

@Serializable
data class AppFeedback(
    /** collecting, done or closed. */
    val state: String,
    @SerialName("post_id") val postId: String,
    val done: Int = 0,
    val bought: Int = 0,
)

@Serializable
data class ApiApp(
    val id: String,
    @SerialName("package_name") val packageName: String,
    val name: String,
    val icon: String = "",
    val developer: String = "",
    /** closed or live. */
    val source: String = "closed",
    /** play, crew or review; null while unverified. */
    @SerialName("verified_by") val verifiedBy: String? = null,
    val crew: AppCrew? = null,
    @SerialName("crews_done") val crewsDone: Int = 0,
    val feedback: AppFeedback? = null,
    @SerialName("reviews_received") val reviewsReceived: Int = 0,
    @SerialName("can_crew") val canCrew: Boolean = false,
    @SerialName("can_feedback") val canFeedback: Boolean = false,
    @SerialName("can_delete") val canDelete: Boolean = false,
    @SerialName("why_not_delete") val whyNotDelete: String = "",
    @SerialName("created_at") val createdAt: String = "",
) {
    val live: Boolean get() = source == "live"
    val verified: Boolean get() = verifiedBy != null
}

@Serializable
data class AppsOut(
    val apps: List<ApiApp> = emptyList(),
)

@Serializable
data class AppAdded(
    val app: ApiApp,
    /** It was on My apps already: adding it again found the one there. */
    val existing: Boolean = false,
)
