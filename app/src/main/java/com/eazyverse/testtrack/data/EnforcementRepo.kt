package com.eazyverse.testtrack.data

// ---- enforcement ---------------------------------------------------------------------

/**
 * Says a cohort-mate's app owner has missed two days running on this one — a flag, not a
 * removal. Nothing about the target changes: not their slot, not whether the app is tested.
 * It exists so an admin sees it and decides, in the console, with the person's history in
 * front of them, rather than having a device three time zones away make the call.
 *
 * One document and two fields, so there is no transaction and nothing to keep in step. Safe
 * to run twice: every device in the cohort reaches the same conclusion from the same proofs,
 * and the rules refuse a second write once the first has already landed — which is also what
 * keeps a dozen devices from raising a dozen pushes about the same two days.
 */
suspend fun Repo.flagAtRisk(
    targetAppId: String,
    byAppId: String,
) {
    await(
        db.collection("apps").document(targetAppId).update(
            mapOf(
                "flaggedAt" to System.currentTimeMillis(),
                // The audit trail, and the rule's argument: it names the app that went
                // unopened, which is what lets the server go and check the proofs itself.
                "flaggedByAppId" to byAppId,
            ),
        ),
    )
}

/**
 * Says an app in the caller's own cohort would not open, or broke, before any admin heard
 * about it another way.
 *
 * Set-once by the rules: a report already on the app refuses a second write, so this can be
 * called freely from the "Can't install this?" flow without first checking whether somebody
 * else already said the same thing.
 */
suspend fun Repo.reportProblem(
    appId: String,
    uid: String,
    reason: String,
) {
    await(
        db.collection("apps").document(appId).update(
            mapOf(
                "reportedAt" to System.currentTimeMillis(),
                "reportedBy" to uid,
                "reportedReason" to reason.take(NOTES_MAX),
            ),
        ),
    )
}

/**
 * Takes a report back — the tester who filed it changing their mind, without waiting on an
 * admin to notice and clear it from the console.
 *
 * The rules only accept this from `reportedBy` itself, so the write below either lands
 * exactly as sent or is refused outright; there is no partial-success case to handle.
 */
suspend fun Repo.clearMyReport(appId: String) {
    await(
        db.collection("apps").document(appId).update(
            mapOf(
                "reportedAt" to 0L,
                "reportedBy" to "",
                "reportedReason" to "",
            ),
        ),
    )
}

/**
 * Tells every other member of this app's cohort that an update is out, and advances the
 * cooldown so the next one waits its turn.
 *
 * One events document per member plus the app's own timestamp, all in a single batch. The
 * rules gate every write in a batch against the same pre-batch snapshot — see the
 * `owner_broadcast` rule and the dedicated `lastBroadcastAt` update rule in firestore.rules —
 * so there is no ordering dependency between the two halves and no way for one to land
 * without the other passing the same cooldown check.
 */
suspend fun Repo.broadcastUpdate(
    app: TestApp,
    group: TestGroup,
) {
    val now = System.currentTimeMillis()
    val batch = db.batch()
    group.memberUids.filter { it != app.ownerUid }.forEach { uid ->
        batch.set(
            db.collection("events").document(),
            mapOf(
                "uid" to uid,
                "type" to EVENT_OWNER_BROADCAST,
                "title" to "${app.label} has an update",
                "body" to "An update is out for ${app.label}. Install it from the Play " +
                    "Store, then keep testing as usual.",
                "groupId" to group.id,
                "appId" to app.id,
                "actorUid" to app.ownerUid,
                "createdAt" to now,
            ),
        )
    }
    batch.update(db.collection("apps").document(app.id), mapOf("lastBroadcastAt" to now))
    await(batch.commit())
}

/**
 * Asks every other member of this app's cohort to leave feedback, and advances the
 * cooldown so the next ask waits its turn.
 *
 * Same shape as [broadcastUpdate], on its own field and its own rule — see
 * `feedback_ask` and the dedicated `lastFeedbackAskAt` update rule in firestore.rules.
 */
suspend fun Repo.askForFeedback(
    app: TestApp,
    group: TestGroup,
) {
    val now = System.currentTimeMillis()
    val batch = db.batch()
    group.memberUids.filter { it != app.ownerUid }.forEach { uid ->
        batch.set(
            db.collection("events").document(),
            mapOf(
                "uid" to uid,
                "type" to EVENT_FEEDBACK_ASK,
                "title" to "${app.label} would like your feedback",
                "body" to "Leave them a note about how it's going, whenever you have a moment.",
                "groupId" to group.id,
                "appId" to app.id,
                "actorUid" to app.ownerUid,
                "createdAt" to now,
            ),
        )
    }
    batch.update(db.collection("apps").document(app.id), mapOf("lastFeedbackAskAt" to now))
    await(batch.commit())
}
