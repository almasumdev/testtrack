package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot

// ---- groups --------------------------------------------------------------------------

private fun parseGroup(doc: DocumentSnapshot) =
    TestGroup(
        id = doc.id,
        name = doc.getString("name").orEmpty(),
        memberUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        appIds = (doc.get("appIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        startDate = doc.getLong("startDate") ?: 0L,
        runDays = (doc.getLong("runDays") ?: RUN_DAYS.toLong()).toInt(),
        graceDays = (doc.getLong("graceDays") ?: 0L).toInt(),
        status = doc.getString("status") ?: TestGroup.STATUS_FORMING,
    )

/** Every cohort this tester belongs to. Membership follows from having an app placed in one. */
suspend fun Repo.myGroups(uid: String): List<TestGroup> =
    await(db.collection("groups").whereArrayContains("memberUids", uid).get())
        .documents
        .map(::parseGroup)
        .sortedBy { it.name.lowercase() }

suspend fun Repo.group(id: String): TestGroup? = await(db.collection("groups").document(id).get()).takeIf { it.exists() }?.let(::parseGroup)
