package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot

// ---- feedback --------------------------------------------------------------------------

private fun parseFeedback(doc: DocumentSnapshot) =
    Feedback(
        appId = doc.getString("appId").orEmpty(),
        groupId = doc.getString("groupId").orEmpty(),
        ownerUid = doc.getString("ownerUid").orEmpty(),
        testerUid = doc.getString("testerUid").orEmpty(),
        day = (doc.getLong("day") ?: 0L).toInt(),
        runStartedAt = doc.getLong("runStartedAt") ?: 0L,
        text = doc.getString("text").orEmpty(),
        createdAt = doc.getLong("createdAt") ?: 0L,
    )

/**
 * A tester's own note about an app they're testing, for a day that otherwise went fine — see
 * [TestApp.reportedAt] for the app-is-broken case this is not. Idempotent by construction,
 * the same reasoning as [recordProof]: same tester, same app, same day of the same run always
 * writes the same id, so refining a thought later the same day overwrites rather than piling up.
 *
 * Written alongside an event addressed to the owner, in the same batch, the same shape as
 * [broadcastUpdate]'s own pairing of an app write with the events it depends on — an owner
 * should not have to think to open their dashboard to find out testers left something there.
 */
suspend fun Repo.submitFeedback(
    app: TestApp,
    uid: String,
    groupId: String,
    day: Int,
    runStartedAt: Long,
    text: String,
) {
    val trimmed = text.take(Feedback.TEXT_MAX)
    val batch = db.batch()
    batch.set(
        db.collection("feedback").document(Feedback.id(app.id, uid, day, runStartedAt)),
        mapOf(
            "appId" to app.id,
            "groupId" to groupId,
            "ownerUid" to app.ownerUid,
            "testerUid" to uid,
            "day" to day,
            "runStartedAt" to runStartedAt,
            "text" to trimmed,
            "createdAt" to System.currentTimeMillis(),
        ),
    )
    batch.set(
        db.collection("events").document(),
        mapOf(
            "uid" to app.ownerUid,
            "type" to "feedback",
            "title" to "New feedback on ${app.label}",
            "body" to trimmed,
            "groupId" to groupId,
            "appId" to app.id,
            "actorUid" to uid,
            "createdAt" to System.currentTimeMillis(),
        ),
    )
    await(batch.commit())
}

/** Which apps this tester has already left today's note on, so the row can stop offering it. */
suspend fun Repo.myFeedbackToday(
    uid: String,
    groupId: String,
    day: Int,
    runStartedAt: Long,
): Set<String> =
    await(
        db
            .collection("feedback")
            .whereEqualTo("testerUid", uid)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("day", day)
            .whereEqualTo("runStartedAt", runStartedAt)
            .get(),
    ).documents.map(::parseFeedback).map { it.appId }.toSet()

/** Every note left on one app, newest first — the owner's own feed of what testers found. */
suspend fun Repo.feedbackForApp(
    appId: String,
    ownerUid: String,
): List<Feedback> =
    await(
        db
            .collection("feedback")
            .whereEqualTo("appId", appId)
            .whereEqualTo("ownerUid", ownerUid)
            .get(),
    ).documents.map(::parseFeedback).sortedByDescending { it.createdAt }
