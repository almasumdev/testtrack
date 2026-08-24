package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot

private fun parseHistory(doc: DocumentSnapshot) =
    History(
        id = doc.id,
        groupId = doc.getString("groupId").orEmpty(),
        groupName = doc.getString("groupName").orEmpty(),
        uid = doc.getString("uid").orEmpty(),
        type = doc.getString("type").orEmpty(),
        appId = doc.getString("appId").orEmpty(),
        appLabel = doc.getString("appLabel").orEmpty(),
        actorUid = doc.getString("actorUid").orEmpty(),
        createdAt = doc.getLong("createdAt") ?: 0L,
    )

/**
 * This group's own record of what happened to me — see AdminRepo.logHistory in the admin app.
 *
 * Scoped to the caller's own uid, not the whole cohort: see the `history` block in
 * firestore.rules for why a membership-shaped check doesn't apply here the way it does for
 * `groups`/`apps` — and why that is what lets this still resolve after leaving the group.
 */
suspend fun Repo.myHistory(
    groupId: String,
    uid: String,
): List<History> =
    await(
        db
            .collection("history")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("uid", uid)
            .get(),
    ).documents.map(::parseHistory).sortedByDescending { it.createdAt }
