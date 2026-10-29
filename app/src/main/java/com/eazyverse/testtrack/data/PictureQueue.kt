package com.eazyverse.testtrack.data

/**
 * The rules of the pictures waiting to send, apart from the phone so they are tested on a desk.
 *
 * A picture is one app's on one night: a newer picture of the same app on the same night replaces
 * the older one, and a picture from another night never does. A pass sends them in order and
 * carries on past a picture the server refuses for good, stopping only when the network, Drive or
 * the server itself is down, which a later pass can get past.
 */
object PictureQueue {
    /** What one picture's try came to. */
    sealed interface Step {
        /** Sent, or dropped for good: on to the next. */
        data object Gone : Step

        /** The network, Drive or the server is down: stop here and say why. */
        data class Stop(
            val reason: String,
        ) : Step
    }

    /** The queue with [item] in it, and the pictures it replaces: the same app on the same night. */
    fun adding(
        queue: List<VisitPictures.Waiting>,
        item: VisitPictures.Waiting,
    ): Pair<List<VisitPictures.Waiting>, List<VisitPictures.Waiting>> {
        val replaced = queue.filter { sameNight(it, item) }
        return queue.filterNot { sameNight(it, item) } + item to replaced
    }

    private fun sameNight(
        a: VisitPictures.Waiting,
        b: VisitPictures.Waiting,
    ) = a.seatId == b.seatId && a.day == b.day

    /**
     * A refusal that will be the same on every try: not allowed (the app left the run, or the
     * member did), a picture the server will not take, or a visit it no longer has. Anything else
     * (a timeout, the server down, a session to refresh) is worth another try later.
     */
    fun refusedForGood(status: Int) = status in setOf(400, 403, 404, 409, 422)

    /** Each picture in turn through [send], until one says stop: null when every one went. */
    suspend fun pass(
        items: List<VisitPictures.Waiting>,
        send: suspend (VisitPictures.Waiting) -> Step,
    ): String? {
        for (item in items) {
            val step = send(item)
            if (step is Step.Stop) return step.reason
        }
        return null
    }

    /** Whether tonight's picture of an app is the one waiting, so it needs no second visit. */
    fun waitingTonight(
        queue: List<VisitPictures.Waiting>,
        seatId: String,
        day: Int,
    ) = queue.any { it.seatId == seatId && it.day == day }
}
