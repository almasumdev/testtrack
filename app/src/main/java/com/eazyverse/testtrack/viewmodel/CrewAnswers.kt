package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ReportWithdrawal
import com.eazyverse.testtrack.net.withdrawReport
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.MyAppsChanged
import com.eazyverse.testtrack.ui.SnackTone

/*
 * Answering for somebody else's app: a vote on one the Play check could not look at, or a report
 * on one already in, and taking that report back; and giving your own seat up. Kept beside
 * CrewViewModel rather than in it, which is long enough already.
 */

/** Answer for an app the Play check could not look at: did it install for you? */
fun CrewViewModel.vote(
    context: Context,
    seat: CrewSeat,
    installed: Boolean,
    reason: String = "",
) = call(
    context = context,
    seat = seat.id,
    holdTillRead = true,
) { Api.vote(seat.id, installed, reason) }

/**
 * Tell an app's owner it will not install for you, and why.
 *
 * The owner hears about it, without your name. Before the run, a second report from somebody
 * else sends it back; during the run it only tells them, and excuses you from that app for the
 * night it is sent on. A later night it is owed again, and can be reported again. A report sent
 * before the run excuses nothing: if it still fails once the run starts, report it that night.
 */
fun CrewViewModel.report(
    context: Context,
    seatId: String,
    reason: String,
) {
    call(
        context = context,
        seat = seatId,
        holdTillRead = true,
    ) { Api.report(seatId, reason) }
}

/**
 * Take back your report of an app, on the night you sent it.
 *
 * The night it excused is owed again, and an open of the app earlier that night counts after all.
 * The reload after the call redraws the row. A refusal, or a server that has no withdraw yet, is
 * one short snack; the reload still runs, since a refusal usually means the row was out of date.
 */
fun CrewViewModel.withdrawReport(
    context: Context,
    seatId: String,
) = call(
    context = context,
    seat = seatId,
    holdTillRead = true,
    onDone = { answer: ReportWithdrawal ->
        when (answer) {
            is ReportWithdrawal.Done -> Unit
            is ReportWithdrawal.Refused -> say(answer.line)
            ReportWithdrawal.Unavailable -> say("Withdrawing a report isn't available yet.")
        }
    },
) { Api.withdrawReport(seatId) }

/**
 * Give your seat up before the crew starts.
 *
 * Only possible before it fills. The server refuses it afterwards, because by then fourteen
 * other people have committed a fortnight on the strength of it being there.
 */
fun CrewViewModel.leave(
    context: Context,
    seat: CrewSeat,
) = call(
    context = context,
    seat = seat.id,
    holdTillRead = true,
    onDone = {
        // The row goes by itself, so only what it cannot show is said: tix coming back.
        when {
            seat.charged == 0 -> Unit
            seat.charged > 0 -> say("${seat.appName} taken out. Your ${seat.charged} tix are back.", SnackTone.DONE)
            else -> say("${seat.appName} taken out. Your ${CrewRules.SEAT_COST} tix are back.", SnackTone.DONE)
        }
        MyAppsChanged.tell()
    },
) { Api.withdraw(seat.id) }
