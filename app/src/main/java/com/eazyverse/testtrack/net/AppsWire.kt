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
    /** What the app is, in Play's two parts. Optional; Play's own for an app found there. */
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("full_description") val fullDescription: String = "",
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
    /** Reviews of this request waiting for the owner. Absent from an older server's answer. */
    @SerialName("to_check") val toCheck: Int = 0,
    /** Taken off Circle after testers could not install the app. Absent from an older server's answer. */
    val unlisted: Boolean = false,
)

@Serializable
data class ApiApp(
    val id: String,
    @SerialName("package_name") val packageName: String,
    val name: String,
    val icon: String = "",
    val developer: String = "",
    /** What the app is, as Play has it or its owner wrote it. Absent from an older server's answer. */
    @SerialName("short_description") val shortDescription: String = "",
    @SerialName("full_description") val fullDescription: String = "",
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
    /*
     * What a seat needs besides Play showing the app. All absent from an older server's answer,
     * which is why the two boxes are nullable: null means "not kept by the server", and the phone
     * keeps them instead.
     */
    @SerialName("all_countries") val allCountries: Boolean? = null,
    @SerialName("all_devices") val allDevices: Boolean? = null,
    /** When Play was last asked whether it shows the app to the group, and what it said. */
    @SerialName("last_check_at") val lastCheckAt: String? = null,
    @SerialName("last_check_ok") val lastCheckOk: Boolean? = null,
    /** live, waiting or appeal: why the app can't take a seat now. Null when it can. */
    @SerialName("crew_block_reason") val crewBlockReason: String? = null,
    /** The crew's name, for waiting and appeal. */
    @SerialName("crew_block_crew") val crewBlockCrew: String? = null,
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

/**
 * A change to an app, sent to PATCH /apps/{id}. Every field is optional and one left null is not
 * sent, so it stays as it was: the two boxes an owner confirms before a seat, or the name and both
 * descriptions from the app's page. An empty name is refused; long text is refused as too long.
 */
@Serializable
data class AppEditIn(
    val name: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("full_description") val fullDescription: String? = null,
    @SerialName("all_countries") val allCountries: Boolean? = null,
    @SerialName("all_devices") val allDevices: Boolean? = null,
)
