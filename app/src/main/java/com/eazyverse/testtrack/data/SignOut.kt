package com.eazyverse.testtrack.data

import android.content.Context

/**
 * Unwinds everything belonging to the signed-in account, in the order it has to happen.
 *
 * Sequenced rather than fired alongside: clearing the push token is a Firestore write that needs
 * the very credentials it is being asked to abandon, so it has to finish before the caller drops
 * the session. The two local teardowns go first and cost nothing — a reminder raised after
 * sign-out would be about a group the phone can no longer read.
 *
 * Shared because two screens can sign out. The setup screen can be reached from home, so the
 * account is abandonable from either, and an ordering constraint duplicated across two view models
 * is one that will eventually only be right in one of them.
 */
suspend fun releaseSession(context: Context) {
    val uid = AuthRepo.uid
    ReminderWorker.cancel(context)
    AdminEvents.clear(context)
    // Otherwise the next account's first sweep reads this one's cohort as thirteen apps that have
    // all just vanished, and says so.
    Enforcement.clear(context)
    PushRepo.clear(context, uid)
}
