package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Volunteers, as the server says them: somebody with no app in a running crew who opens its apps
 * every night for tix. See the backend's docs/volunteers.md. Every field has a default, so a
 * server from before volunteers and one after both read.
 */

/** Asking for a volunteer spot in a running crew, from this phone and in its time zone. */
@Serializable
data class VolunteerIn(
    @SerialName("crew_id") val crewId: String,
    @SerialName("device_fp") val deviceFp: String,
    val timezone: String = "UTC",
)

/** The reader's own volunteer spot in a crew: sent on a crew only to the volunteer it is. */
@Serializable
data class ApiVolunteerMe(
    val id: String = "",
    /** active, left, out, released, removed or done. */
    val state: String = "active",
    /** Their own night of the run, and the first one that is theirs. */
    val day: Int = 0,
    @SerialName("first_night") val firstNight: Int = 1,
    @SerialName("starts_in_ms") val startsInMs: Long = 0,
    @SerialName("done_today") val doneToday: Int = 0,
    @SerialName("owed_today") val owedToday: Int = 0,
    @SerialName("night_done") val nightDone: Boolean = false,
    /** Whole nights paid so far, and the tix they came to. */
    @SerialName("nights_paid") val nightsPaid: Int = 0,
    @SerialName("tix_earned") val tixEarned: Int = 0,
    /** Why the spot ended, in words, such as "Missed night 5."; blank while it is active. */
    @SerialName("end_reason") val endReason: String = "",
    /** The last whole night that counted for them; null while active. */
    @SerialName("last_night") val lastNight: Int? = null,
)

/** One volunteer in a crew, as its members see them: the same shape as a member's seat, with no app. */
@Serializable
data class ApiVolunteerRow(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val photo: String = "",
    val email: String = "",
    val state: String = "active",
    val day: Int = 0,
    @SerialName("first_night") val firstNight: Int = 1,
    @SerialName("done_today") val doneToday: Int = 0,
    @SerialName("owed_today") val owedToday: Int = 0,
    @SerialName("night_done") val nightDone: Boolean = false,
    @SerialName("out_at") val outAt: String? = null,
    @SerialName("opened_mine_today") val openedMineToday: Boolean = false,
    @SerialName("opened_mine_at") val openedMineAt: String? = null,
    @SerialName("opened_mine_days") val openedMineDays: Int = 0,
    @SerialName("opened_mine_picture") val openedMinePicture: String = "",
    @SerialName("opened_mine_by_hand") val openedMineByHand: Boolean = false,
)

/** A running crew on the Open tab's volunteer list: icons only, never names or packages. */
@Serializable
data class ApiVolunteerSpot(
    @SerialName("crew_id") val crewId: String,
    val number: Int = 0,
    val name: String = "",
    /** The crew's night in the reader's calendar, and how long it runs. */
    val day: Int = 0,
    @SerialName("run_days") val runDays: Int = 14,
    /** Volunteer spots free now. */
    val free: Int = 0,
    val icons: List<String> = emptyList(),
    /** The reader's first night if they joined now; 0 from a server that does not say. */
    @SerialName("first_night") val firstNight: Int = 0,
    /** The most tix the reader could earn here; 0 from a server that does not say. */
    val tix: Int = 0,
    /** The reader and this crew: open, full, volunteering, member, or how their spot ended (left, out, released, removed). */
    val state: String = "open",
)

/**
 * One volunteer spot of the reader's that is over, however it ended, as GET /me/crew-records lists
 * it under `volunteering`. A join and leave with no nights is one too. Absent from an older server.
 */
@Serializable
data class ApiVolunteerStint(
    val id: String = "",
    @SerialName("crew_id") val crewId: String = "",
    @SerialName("crew_number") val crewNumber: Int = 0,
    @SerialName("crew_name") val crewName: String = "",
    /** left, out, released, removed or done. */
    val state: String = "left",
    @SerialName("joined_at") val joinedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("run_days") val runDays: Int = 14,
    @SerialName("first_night") val firstNight: Int = 1,
    /** The last whole night that counted for them; 0 when they left before their first night. */
    @SerialName("last_night") val lastNight: Int? = null,
    @SerialName("nights_paid") val nightsPaid: Int = 0,
    @SerialName("tix_earned") val tixEarned: Int = 0,
    @SerialName("end_reason") val endReason: String = "",
)

@Serializable
data class ApiVolunteerSpots(
    val spots: List<ApiVolunteerSpot> = emptyList(),
    /** Whether this account may take a spot at all, and why not in words. */
    @SerialName("can_join") val canJoin: Boolean = true,
    val blocked: String? = null,
)
