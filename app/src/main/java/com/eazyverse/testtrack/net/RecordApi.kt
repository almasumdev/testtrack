package com.eazyverse.testtrack.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * A member's kept record of each crew they ran in, and the two things they can do with it.
 * See the backend's app/schemas/crew_record.py. Fields the Record tab does not draw are left out;
 * the decoder skips them.
 */

/** How one member's run in one crew came to. Times are ISO strings, null where the server has none. */
@Serializable
data class ApiCrewRecord(
    @SerialName("crew_id") val crewId: String,
    @SerialName("crew_number") val crewNumber: Int = 0,
    @SerialName("crew_name") val crewName: String = "",
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("ended_at") val endedAt: String? = null,
    @SerialName("run_days") val runDays: Int = 14,
    /** Nights of the run they owed something and the night was over. */
    @SerialName("member_nights") val memberNights: Int = 0,
    @SerialName("app_name") val appName: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_icon") val appIcon: String = "",
    /** finished, left, removed, moved or cancelled. */
    val outcome: String = "finished",
    @SerialName("left_day") val leftDay: Int? = null,
    @SerialName("nights_kept") val nightsKept: Int = 0,
    @SerialName("nights_missed") val nightsMissed: Int = 0,
    @SerialName("nights_short") val nightsShort: Int = 0,
    @SerialName("nights_let_off") val nightsLetOff: Int = 0,
    @SerialName("nights_reported") val nightsReported: Int = 0,
    @SerialName("testers_stayed") val testersStayed: Int = 0,
    @SerialName("testers_every_day") val testersEveryDay: Int = 0,
    val counted: Boolean = false,
    val floor: Int = 12,
    @SerialName("tix_spent") val tixSpent: Int = 0,
    @SerialName("tix_refunded") val tixRefunded: Int = 0,
    val charged: Boolean = false,
    val perfect: Boolean = false,
    @SerialName("moved_to_crew_number") val movedToCrewNumber: Int? = null,
    @SerialName("moved_to_crew_id") val movedToCrewId: String? = null,
    @SerialName("out_reason") val outReason: String? = null,
    @SerialName("done_at") val doneAt: String? = null,
    @SerialName("prod_access") val prodAccess: Boolean? = null,
    @SerialName("prod_access_answered_at") val prodAccessAnsweredAt: String? = null,
    @SerialName("prod_access_until") val prodAccessUntil: String? = null,
    @SerialName("prod_access_reward") val prodAccessReward: Int = 0,
    @SerialName("perfect_claim_amount") val perfectClaimAmount: Int = 0,
    @SerialName("perfect_claimed_at") val perfectClaimedAt: String? = null,
    @SerialName("perfect_claimable") val perfectClaimable: Boolean = false,
    /** The member's nights in order, 1 to the end of the run; null for a record kept before them. */
    val nights: List<ApiRecordNight>? = null,
    /** The other apps in the crew, never the reader's own; null for a record kept before them. */
    @SerialName("crew_apps") val crewApps: List<ApiRecordApp>? = null,
    /** The reader's app's testers: only from [crewRecord], and only while the crew is not pruned. */
    val testers: List<ApiRecordTester>? = null,
    val final: Boolean = true,
    val pruned: Boolean = false,
)

/** One night of the member's run. */
@Serializable
data class ApiRecordNight(
    val night: Int = 0,
    /** kept, short, missed, let_off, reported, after_left or not_in. */
    val state: String = "",
)

/** Another member's app in the crew, for the icon strip. */
@Serializable
data class ApiRecordApp(
    @SerialName("app_name") val appName: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_icon") val appIcon: String = "",
    /** finished, left, removed, moved or cancelled. */
    val outcome: String = "finished",
)

/** One other member's part in testing the reader's app. */
@Serializable
data class ApiRecordTester(
    val name: String = "",
    val avatar: String = "",
    @SerialName("nights_opened") val nightsOpened: Int = 0,
    @SerialName("of_nights") val ofNights: Int = 0,
    /** every_night, some, left, out or reported. */
    val status: String = "",
    @SerialName("left_day") val leftDay: Int? = null,
)

@Serializable
data class CrewRecordList(
    val items: List<ApiCrewRecord> = emptyList(),
    /** Volunteer spots that are over, newest first; empty from an older server. */
    val volunteering: List<ApiVolunteerStint> = emptyList(),
)

@Serializable
data class ProdAccessIn(
    val granted: Boolean,
)

private const val CREW_RECORDS = "/me/crew-records"

/** Every finished crew this account ran in, newest first. Kept on the phone for offline. */
suspend fun Api.crewRecords(): CrewRecordList = kept(CREW_RECORDS)

/** What [crewRecords] last kept, or null; see [Api.last]. */
fun Api.lastCrewRecords(afterFailure: Boolean = false): CrewRecordList? = last(CREW_RECORDS, afterFailure)

/** One crew's record on its own, with its testers while they are kept. 404 for a crew never run in. */
suspend fun Api.crewRecord(crewId: String): ApiCrewRecord = get("/crews/$crewId/record")

/** "Did your app get production access?", answered once for the tix it pays. 403, 404 or 409 refuse it. */
suspend fun Api.answerProdAccess(
    crewId: String,
    granted: Boolean,
): ApiCrewRecord = post("/crews/$crewId/record/prod-access", ProdAccessIn(granted))

/** A perfect run's seat price back, claimed once. 403, 404 or 409 refuse it. */
suspend fun Api.claimPerfect(crewId: String): ApiCrewRecord = post("/crews/$crewId/record/claim-perfect", Empty())
