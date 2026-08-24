package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot

// ---- proofs --------------------------------------------------------------------------

private fun parseProof(doc: DocumentSnapshot) =
    Proof(
        appId = doc.getString("appId").orEmpty(),
        groupId = doc.getString("groupId").orEmpty(),
        ownerUid = doc.getString("ownerUid").orEmpty(),
        testerUid = doc.getString("testerUid").orEmpty(),
        testerEmail = doc.getString("testerEmail").orEmpty(),
        day = (doc.getLong("day") ?: 0L).toInt(),
        fileId = doc.getString("fileId").orEmpty(),
        imageUrl = doc.getString("imageUrl").orEmpty(),
        capturedAt = doc.getLong("capturedAt") ?: 0L,
        usageMs = doc.getLong("usageMs") ?: 0L,
        runStartedAt = doc.getLong("runStartedAt") ?: 0L,
        // Absent on everything posted before manual capture existed, which reads as automated —
        // the only kind there was.
        manual = doc.getBoolean("manual") ?: false,
    )

/** Idempotent by construction: same tester, same app, same day always writes the same id. */
suspend fun Repo.recordProof(proof: Proof) {
    await(
        db
            .collection("proofs")
            .document(
                Proof.id(proof.appId, proof.testerUid, proof.day, proof.runStartedAt),
            ).set(
                mapOf(
                    "appId" to proof.appId,
                    "groupId" to proof.groupId,
                    "ownerUid" to proof.ownerUid,
                    "testerUid" to proof.testerUid,
                    "testerEmail" to proof.testerEmail,
                    "day" to proof.day,
                    "fileId" to proof.fileId,
                    "imageUrl" to proof.imageUrl,
                    "capturedAt" to proof.capturedAt,
                    "usageMs" to proof.usageMs,
                    "runStartedAt" to proof.runStartedAt,
                    "manual" to proof.manual,
                ),
            ),
    )
}

/**
 * Every proof for one app — the owner's whole grid, in one read.
 *
 * Filtered by owner as well as app, and that second clause is not redundant: **security rules
 * are not filters.** For a query, Firestore will not evaluate `resource.data` document by
 * document — it requires the query to constrain the fields the rule tests, so the whole result
 * set is provably allowed before anything is read. The rule permits a proof to its app's
 * owner, so the query has to say `ownerUid == me` or it is refused outright, however
 * impeccable the documents are.
 */
suspend fun Repo.proofsForApp(
    appId: String,
    ownerUid: String,
): List<Proof> =
    await(
        db
            .collection("proofs")
            .whereEqualTo("appId", appId)
            .whereEqualTo("ownerUid", ownerUid)
            .get(),
    ).documents.map(::parseProof)

/**
 * What this tester has already posted in one group today.
 *
 * Keyed on the group's day rather than a timestamp window, so a report at 23:59 and another at
 * 00:01 are the same day if the group says they are.
 *
 * @param requireBar counts only visits that cleared [Proof.MIN_USAGE_MS]. True for the day's
 *   progress, which is what the tester is asked for. False for [Enforcement], which must
 *   agree with the security rules — and a rule can only ask whether a proof document exists,
 *   not how long it recorded.
 */
suspend fun Repo.myProofsForDay(
    uid: String,
    groupId: String,
    day: Int,
    runStartedAt: Long,
    requireBar: Boolean = true,
): Set<String> =
    await(
        db
            .collection("proofs")
            .whereEqualTo("testerUid", uid)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("day", day)
            // The run, as well as the day. A restarted cohort numbers its days from zero
            // again, so without this it opens showing the previous run's attendance.
            .whereEqualTo("runStartedAt", runStartedAt)
            .get(),
    ).documents
        .map(::parseProof)
        .filter { !requireBar || it.meetsBar }
        .map { it.appId }
        .toSet()

/**
 * The same day [myProofsForDay] reads, but appId -> whether that proof was supplied by hand —
 * for [GroupViewModel] to mark a manually-completed day yellow rather than green. One query
 * rather than two: [myProofsForDay]'s own callers in [Enforcement] don't need this, so it
 * stays a separate function instead of changing that one's return shape for everybody.
 */
suspend fun Repo.myProofDetailsForDay(
    uid: String,
    groupId: String,
    day: Int,
    runStartedAt: Long,
): Map<String, Boolean> =
    await(
        db
            .collection("proofs")
            .whereEqualTo("testerUid", uid)
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("day", day)
            .whereEqualTo("runStartedAt", runStartedAt)
            .get(),
    ).documents
        .map(::parseProof)
        .filter { it.meetsBar }
        .associate { it.appId to it.manual }

/** Every day this tester has posted anything in one group, whatever app it was about. */
suspend fun Repo.myReportedDays(
    uid: String,
    groupId: String,
): Set<Int> =
    await(
        db
            .collection("proofs")
            .whereEqualTo("testerUid", uid)
            .whereEqualTo("groupId", groupId)
            .get(),
    ).documents.map(::parseProof).map { it.day }.toSet()
