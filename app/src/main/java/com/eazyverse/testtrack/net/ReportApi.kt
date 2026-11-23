package com.eazyverse.testtrack.net

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/*
 * Taking a report of somebody's app back, on the night it was sent. The backend's
 * app/services/report_withdraw.py holds the rules; this only asks and says what came back.
 */

/** How a withdraw went: done with the seat as the reader now sees it, refused, or not on this server yet. */
sealed interface ReportWithdrawal {
    data class Done(
        val seat: ApiSeat,
    ) : ReportWithdrawal

    /** The server said no, for a reason the member can read in [line]. */
    data class Refused(
        val line: String,
    ) : ReportWithdrawal

    /** A server from before withdraw existed: the route is not there. */
    data object Unavailable : ReportWithdrawal
}

/**
 * Take back this member's report of [seatId] for their current night.
 *
 * A refusal the member can act on comes back as [ReportWithdrawal.Refused], with a short line
 * picked by its code. Anything else (no connection, a session gone, a seat that is gone) is thrown
 * as before, so the caller's usual handling applies.
 */
suspend fun Api.withdrawReport(seatId: String): ReportWithdrawal =
    try {
        ReportWithdrawal.Done(
            send<ApiSeat>(Request.Builder().url(url("/crews/seats/$seatId/report/withdraw")).post("{}".toRequestBody(JSON))),
        )
    } catch (e: ApiError) {
        withdrawRefusal(e) ?: throw e
    }

/**
 * What a refused withdraw means for the member, or null when it is not a refusal of withdraw.
 *
 * An older server has no such route and answers 404 or 405 with no error code of ours, which reads
 * as "unknown". A 404 that carries a code is a seat that is gone, and is left to the caller.
 */
internal fun withdrawRefusal(e: ApiError): ReportWithdrawal? =
    when {
        e.code == "unknown" && e.status in setOf(404, 405) -> ReportWithdrawal.Unavailable
        e.code == "no_report" -> ReportWithdrawal.Refused("There is no report to withdraw.")
        e.code == "report_night_over" -> ReportWithdrawal.Refused("That night is over, so the report stays.")
        e.code == "report_whole_run" -> ReportWithdrawal.Refused("That report covers the whole run, so it stays.")
        e.code == "report_app_out" -> ReportWithdrawal.Refused("That app is out of the crew, so the report stays.")
        else -> null
    }
