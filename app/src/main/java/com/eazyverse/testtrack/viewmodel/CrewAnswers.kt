package com.eazyverse.testtrack.viewmodel

import android.content.Context
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.ui.CrewSeat
import com.eazyverse.testtrack.ui.CrewViewModel

/*
 * Answering for somebody else's app: a vote on one the Play check could not look at, or a report
 * on one already in. Kept beside CrewViewModel rather than in it, which is long enough already.
 */

/** Answer for an app the Play check could not look at: did it install for you? */
fun CrewViewModel.vote(
    context: Context,
    seat: CrewSeat,
    installed: Boolean,
    reason: String = "",
) = call(
    context = context,
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
 * else sends it back; during the run it only tells them, and excuses you from that app.
 */
fun CrewViewModel.report(
    context: Context,
    seatId: String,
    appName: String,
    reason: String,
) = call(
    context = context,
    onDone = { seat ->
        say(
            if (seat.state == "rejected") {
                "$appName went back to its owner to fix."
            } else {
                "Reported. ${seat.ownerName} has been told, and it no longer counts against you."
            },
        )
    },
) { Api.report(seatId, reason) }
