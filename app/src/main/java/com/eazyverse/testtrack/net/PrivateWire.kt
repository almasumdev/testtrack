package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Private testers, as the server says them: somebody an app's owner asked by email to open that
 * app while its crew runs. Not a member: no tix, no rules, no picture. See the backend's
 * docs/private-testers.md. Every field has a default where the server may leave it out.
 */

/** Asking the account with exactly this email to test the app on this seat. */
@Serializable
data class PrivateInviteIn(
    @SerialName("seat_id") val seatId: String,
    val email: String,
)

/** Saying yes, with the calendar the tester's nights are counted in. */
@Serializable
data class PrivateAcceptIn(
    val timezone: String = "UTC",
)

/** One open, measured on the phone: time in front and who installed the copy. */
@Serializable
data class PrivateVisitIn(
    @SerialName("used_ms") val usedMs: Long,
    val installer: String,
)

/** One of the tester's own nights, opened or not. */
@Serializable
data class ApiPrivateNight(
    val day: Int = 0,
    val opened: Boolean = false,
)

/** One person asked, as the app's owner sees them. */
@Serializable
data class ApiPrivateTester(
    val id: String,
    @SerialName("seat_id") val seatId: String = "",
    @SerialName("tester_id") val testerId: String = "",
    val name: String = "",
    val photo: String = "",
    val email: String = "",
    /** invited, accepted, declined, removed, left or ended. */
    val state: String = "invited",
    @SerialName("end_reason") val endReason: String = "",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("answered_at") val answeredAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    /** The night they accepted on, and their own night now; both 0 until they accept. */
    @SerialName("first_night") val firstNight: Int = 0,
    val day: Int = 0,
    /** Each night from [firstNight] to [day], opened or not. Nights to come are not listed. */
    val nights: List<ApiPrivateNight> = emptyList(),
    @SerialName("opened_days") val openedDays: Int = 0,
)

/** Everyone the owner asked about one app, in any state, oldest first. */
@Serializable
data class ApiPrivateResults(
    @SerialName("seat_id") val seatId: String = "",
    @SerialName("app_name") val appName: String = "",
    @SerialName("run_days") val runDays: Int = 14,
    val testers: List<ApiPrivateTester> = emptyList(),
)

/** One ask, as the person asked sees it. The package and notes come only once they accept. */
@Serializable
data class ApiMyPrivateTest(
    val id: String,
    @SerialName("seat_id") val seatId: String = "",
    @SerialName("crew_id") val crewId: String = "",
    val state: String = "invited",
    @SerialName("app_name") val appName: String = "",
    @SerialName("package_name") val packageName: String = "",
    val icon: String = "",
    val notes: String = "",
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("crew_name") val crewName: String = "",
    val day: Int = 0,
    @SerialName("first_night") val firstNight: Int = 0,
    @SerialName("run_days") val runDays: Int = 14,
    @SerialName("opened_today") val openedToday: Boolean = false,
    @SerialName("opened_days") val openedDays: Int = 0,
)

/** The asks still open for the reader: waiting for an answer, or accepted. */
@Serializable
data class ApiMyPrivateTests(
    val tests: List<ApiMyPrivateTest> = emptyList(),
)
