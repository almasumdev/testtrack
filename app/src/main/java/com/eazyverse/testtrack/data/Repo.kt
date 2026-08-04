package com.eazyverse.testtrack.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firestore access.
 *
 * Three collections, deliberately flat:
 *
 * ```
 * users/{uid}                              email, displayName
 * apps/{packageName}                       ownerUid, name, startDate, status
 * proofs/{appId}__{testerUid}__{day}       fileId, imageUrl, capturedAt
 * ```
 *
 * The composite proof id is what keeps this simple: writing twice overwrites instead of
 * duplicating, and an owner's whole 14-day grid is one `whereEqualTo("appId", …)` query rather
 * than 12 × 14 reads.
 */
object Repo {

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    /** Records the signed-in tester so other members' grids can show a name, not a raw uid. */
    suspend fun upsertUser(uid: String, email: String, displayName: String) {
        await(
            db.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        )
    }

    suspend fun testers(): List<Tester> =
        await(db.collection("users").get()).documents.mapNotNull { doc ->
            Tester(
                uid = doc.getString("uid") ?: doc.id,
                email = doc.getString("email").orEmpty(),
                displayName = doc.getString("displayName").orEmpty()
            ).takeIf { it.email.isNotBlank() }
        }.sortedBy { it.shortName }

    /**
     * Registers the owner's app, pending admin approval.
     *
     * Keyed by package name so re-submitting corrects the record instead of creating a second
     * one. `status` is not merged in on purpose — a resubmission goes back to pending review.
     */
    suspend fun submitApp(uid: String, email: String, packageName: String, name: String) {
        await(
            db.collection("apps").document(packageName).set(
                mapOf(
                    "id" to packageName,
                    "ownerUid" to uid,
                    "ownerEmail" to email,
                    "name" to name,
                    "packageName" to packageName,
                    "startDate" to System.currentTimeMillis(),
                    "status" to TestApp.STATUS_PENDING
                )
            )
        )
    }

    private fun parseApp(doc: com.google.firebase.firestore.DocumentSnapshot) = TestApp(
        id = doc.getString("id") ?: doc.id,
        ownerUid = doc.getString("ownerUid").orEmpty(),
        ownerEmail = doc.getString("ownerEmail").orEmpty(),
        name = doc.getString("name").orEmpty(),
        packageName = doc.getString("packageName") ?: doc.id,
        startDate = doc.getLong("startDate") ?: 0L,
        status = doc.getString("status") ?: TestApp.STATUS_PENDING
    )

    /** Every approved app in the group — the tester's daily worklist. */
    suspend fun approvedApps(): List<TestApp> =
        await(
            db.collection("apps")
                .whereEqualTo("status", TestApp.STATUS_APPROVED)
                .get()
        ).documents.map(::parseApp).sortedBy { it.name.lowercase() }

    suspend fun myApp(uid: String): TestApp? =
        await(db.collection("apps").whereEqualTo("ownerUid", uid).get())
            .documents.firstOrNull()?.let(::parseApp)

    /** Idempotent by construction: same tester, same app, same day always writes the same id. */
    suspend fun recordProof(proof: Proof) {
        await(
            db.collection("proofs")
                .document(Proof.id(proof.appId, proof.testerUid, proof.day))
                .set(
                    mapOf(
                        "appId" to proof.appId,
                        "testerUid" to proof.testerUid,
                        "testerEmail" to proof.testerEmail,
                        "day" to proof.day,
                        "fileId" to proof.fileId,
                        "imageUrl" to proof.imageUrl,
                        "capturedAt" to proof.capturedAt
                    )
                )
        )
    }

    private fun parseProof(doc: com.google.firebase.firestore.DocumentSnapshot) = Proof(
        appId = doc.getString("appId").orEmpty(),
        testerUid = doc.getString("testerUid").orEmpty(),
        testerEmail = doc.getString("testerEmail").orEmpty(),
        day = (doc.getLong("day") ?: 0L).toInt(),
        fileId = doc.getString("fileId").orEmpty(),
        imageUrl = doc.getString("imageUrl").orEmpty(),
        capturedAt = doc.getLong("capturedAt") ?: 0L
    )

    /** Every proof for one app — the owner's whole grid, in one read. */
    suspend fun proofsForApp(appId: String): List<Proof> =
        await(db.collection("proofs").whereEqualTo("appId", appId).get())
            .documents.map(::parseProof)

    /** What this tester has already posted today, so the list can show what is left. */
    suspend fun myProofsToday(uid: String): Set<String> =
        await(db.collection("proofs").whereEqualTo("testerUid", uid).get())
            .documents.map(::parseProof)
            .filter { proof ->
                // "Today" is per-app, because each app's run started on its own date.
                proof.capturedAt > System.currentTimeMillis() - 86_400_000L
            }
            .map { it.appId }
            .toSet()
}
