package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.ui.CrewPhase
import com.eazyverse.testtrack.ui.CrewRules
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel
import com.eazyverse.testtrack.ui.MyAppsChanged

/*
 * Answering for somebody else's app: a vote on one the Play check could not look at, or a report
 * on one already in; and giving your own seat up. Kept beside CrewViewModel rather than in it,
 * which is long enough already.
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
    onDone = { answer ->
        say(
            when (answer.state) {
                "confirmed" -> "${seat.appName} is in."
                "rejected" -> "${seat.appName} went back to its owner to fix."
                else -> "Thanks. One more answer decides it."
            },
        )
    },
) { Api.vote(seat.id, installed, reason) }

/**
 * Tell an app's owner it will not install for you, and why.
 *
 * The owner hears about it, without your name. Before the run, a second report from somebody
 * else sends it back; during the run it only tells them, and excuses you from that app for the
 * night it is sent on. A later night it is owed again, and can be reported again.
 */
fun CrewViewModel.report(
    context: Context,
    seatId: String,
    appName: String,
    reason: String,
) {
    val running = crews.any { crew -> crew.phase == CrewPhase.RUN && crew.seats.any { it.id == seatId } }
    call(
        context = context,
        seat = seatId,
        onDone = { seat ->
            say(
                when {
                    seat.state == "rejected" -> "$appName went back to its owner to fix."
                    running -> "Reported. ${seat.ownerName} has been told, and tonight doesn't count against you."
                    else -> "Reported. ${seat.ownerName} has been told, and it no longer counts against you."
                },
            )
        },
    ) { Api.report(seatId, reason) }
}

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
    onDone = {
        say(
            when {
                seat.charged == 0 -> "Taken back out. It was placed free, so there was nothing to refund. You can put an app in again."
                seat.charged > 0 -> "Taken back out, and its ${seat.charged} tix are back. You can put an app in again."
                else -> "Taken back out, and its ${CrewRules.SEAT_COST} tix are back. You can put an app in again."
            },
        )
        MyAppsChanged.tell()
    },
) { Api.withdraw(seat.id) }
