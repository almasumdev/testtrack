package com.eazyverse.testtrack.data

/** {appId}__{uid}, exactly as firestore.rules' skips block expects to derive it. */
private fun skipId(
    appId: String,
    uid: String,
) = "${appId}__$uid"

/** Whether [uid] has personally opted out of testing [appId] — see the `skips` collection. */
suspend fun Repo.hasSkipped(
    appId: String,
    uid: String,
): Boolean = await(db.collection("skips").document(skipId(appId, uid)).get()).exists()

/** Every app in [groupId] the caller has personally skipped, for [TestRow]'s own state. */
suspend fun Repo.mySkips(
    uid: String,
    groupId: String,
): Set<String> =
    await(
        db
            .collection("skips")
            .whereEqualTo("uid", uid)
            .whereEqualTo("groupId", groupId)
            .get(),
    ).documents.mapNotNull { it.getString("appId") }.toSet()

/** Opts the caller out of testing [app] — only admitted when [TestApp.skippable] is set. */
suspend fun Repo.setSkip(
    app: TestApp,
    uid: String,
    groupId: String,
) {
    await(
        db.collection("skips").document(skipId(app.id, uid)).set(
            mapOf(
                "appId" to app.id,
                "uid" to uid,
                "groupId" to groupId,
                "skippedAt" to System.currentTimeMillis(),
            ),
        ),
    )
}

/** Reverses [setSkip] — the app owes its ordinary day again for this member from now on. */
suspend fun Repo.clearSkip(
    appId: String,
    uid: String,
) {
    await(db.collection("skips").document(skipId(appId, uid)).delete())
}
