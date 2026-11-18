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
    @SerialName("app_name") val appName: String = "",
    @SerialName("package_name") val packageName: String = "",
    @SerialName("app_icon") val appIcon: String = "",
    /** finished, left, removed, moved or cancelled. */
    val outcome: String = "finished",
    @SerialName("left_day") val leftDay: Int? = null,
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
    val final: Boolean = true,
    val pruned: Boolean = false,
)

@Serializable
data class CrewRecordList(
    val items: List<ApiCrewRecord> = emptyList(),
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

/** "Did your app get production access?", answered once for the tix it pays. 403, 404 or 409 refuse it. */
suspend fun Api.answerProdAccess(
    crewId: String,
    granted: Boolean,
): ApiCrewRecord = post("/crews/$crewId/record/prod-access", ProdAccessIn(granted))

/** A perfect run's seat price back, claimed once. 403, 404 or 409 refuse it. */
suspend fun Api.claimPerfect(crewId: String): ApiCrewRecord = post("/crews/$crewId/record/claim-perfect", Empty())
