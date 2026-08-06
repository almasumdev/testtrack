package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raised when a package name belongs to another member's app. */
class AppTakenException(packageName: String, ownerEmail: String) : Exception(
    if (ownerEmail.isBlank()) "$packageName is already registered by another member."
    else "$packageName is already registered by $ownerEmail."
)

/**
 * Firestore access.
 *
 * Four collections, deliberately flat:
 *
 * ```
 * users/{uid}                              email, displayName
 * groups/{groupId}                         memberUids, appIds, startDate, status
 * apps/{packageName}                       ownerUid, name, groupId, status
 * proofs/{appId}__{testerUid}__{day}       fileId, imageUrl, capturedAt, usageMs
 * ```
 *
 * The composite proof id is what keeps this simple: writing twice overwrites instead of
 * duplicating, and a whole 14-day grid is one `whereEqualTo("appId", …)` query rather than
 * 13 × 14 reads.
 *
 * Nothing here writes `groups`, or an app's `groupId` and `status`. Those belong to an admin, and
 * the security rules enforce it — see [firestore.rules].
 */
object Repo {

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    // ---- users ---------------------------------------------------------------------------

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

    /**
     * Just the members of one group, which is all any grid or dashboard ever needs.
     *
     * There is deliberately no "every tester" variant. One existed, went unused, and was a whole
     * user table one careless call away — the rules allow it, because a grid has to turn a uid
     * into a name, and that permission is only defensible while the queries stay this narrow.
     */
    suspend fun testers(uids: Collection<String>): List<Tester> {
        if (uids.isEmpty()) return emptyList()
        return uids.distinct().chunked(30).flatMap { batch ->
            await(db.collection("users").whereIn("uid", batch).get())
                .documents.mapNotNull(::parseTester)
        }.sortedBy { it.shortName }
    }

    private fun parseTester(doc: DocumentSnapshot) = Tester(
        uid = doc.getString("uid") ?: doc.id,
        email = doc.getString("email").orEmpty(),
        displayName = doc.getString("displayName").orEmpty()
    ).takeIf { it.email.isNotBlank() }

    // ---- groups --------------------------------------------------------------------------

    private fun parseGroup(doc: DocumentSnapshot) = TestGroup(
        id = doc.id,
        name = doc.getString("name").orEmpty(),
        memberUids = (doc.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        appIds = (doc.get("appIds") as? List<*>)?.filterIsInstance<String>().orEmpty(),
        startDate = doc.getLong("startDate") ?: 0L,
        status = doc.getString("status") ?: TestGroup.STATUS_FORMING
    )

    /** Every cohort this tester belongs to. Membership follows from having an app placed in one. */
    suspend fun myGroups(uid: String): List<TestGroup> =
        await(db.collection("groups").whereArrayContains("memberUids", uid).get())
            .documents.map(::parseGroup).sortedBy { it.name.lowercase() }

    suspend fun group(id: String): TestGroup? =
        await(db.collection("groups").document(id).get()).takeIf { it.exists() }?.let(::parseGroup)

    // ---- apps ----------------------------------------------------------------------------

    private fun parseApp(doc: DocumentSnapshot) = TestApp(
        id = doc.getString("id") ?: doc.id,
        ownerUid = doc.getString("ownerUid").orEmpty(),
        ownerEmail = doc.getString("ownerEmail").orEmpty(),
        name = doc.getString("name").orEmpty(),
        packageName = doc.getString("packageName") ?: doc.id,
        groupId = doc.getString("groupId")?.takeIf { it.isNotBlank() },
        submittedAt = doc.getLong("submittedAt") ?: 0L,
        status = doc.getString("status") ?: TestApp.STATUS_PENDING
    )

    /**
     * Registers an app for review, or corrects one already registered.
     *
     * Keyed by package name so re-submitting corrects the record instead of creating a second one.
     * Placement is deliberately absent: an owner cannot put their own app into a group, and the
     * rules reject the write if they try.
     *
     * @throws AppTakenException if the package is already registered by someone else. The rules
     *   would reject it anyway, but as a bare PERMISSION_DENIED that explains nothing.
     */
    suspend fun submitApp(uid: String, email: String, packageName: String, name: String) {
        val doc = db.collection("apps").document(packageName)

        // A refusal here is itself the answer: app documents are readable by their owner and by
        // the cohort testing them, so a package we cannot read is one already registered by
        // someone whose group we are not in. Their address stays hidden, which is the point.
        val existing = try {
            await(doc.get())
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                throw AppTakenException(packageName, "")
            }
            throw e
        }

        if (existing.exists() && existing.getString("ownerUid").orEmpty() != uid) {
            throw AppTakenException(packageName, existing.getString("ownerEmail").orEmpty())
        }

        val body = mutableMapOf<String, Any>(
            "id" to packageName,
            "ownerUid" to uid,
            "ownerEmail" to email,
            "name" to name,
            "packageName" to packageName,
            "submittedAt" to (existing.getLong("submittedAt")?.takeIf { it > 0 }
                ?: System.currentTimeMillis())
        )

        // A correction must not disturb placement — merging leaves groupId and status alone, so
        // fixing a typo on day nine does not eject the app from its group.
        if (!existing.exists()) {
            body["groupId"] = ""
            body["status"] = TestApp.STATUS_PENDING
        }

        await(doc.set(body, SetOptions.merge()))
    }

    /**
     * Withdraws an app.
     *
     * Its proofs are left behind: they are keyed by app id and nothing queries them once the app
     * is gone, and deleting them would mean handing clients write access to other testers' rows.
     */
    suspend fun deleteApp(packageName: String) {
        await(db.collection("apps").document(packageName).delete())
    }

    suspend fun app(id: String): TestApp? =
        await(db.collection("apps").document(id).get()).takeIf { it.exists() }?.let(::parseApp)

    /** Everything in one cohort — the tester's worklist, and the owner's peers. */
    suspend fun appsInGroup(groupId: String): List<TestApp> =
        await(db.collection("apps").whereEqualTo("groupId", groupId).get())
            .documents.map(::parseApp).sortedBy { it.label.lowercase() }

    /** Submitted, waiting for an admin to place it. */
    suspend fun pendingApps(uid: String): List<TestApp> =
        await(
            db.collection("apps")
                .whereEqualTo("ownerUid", uid)
                .whereEqualTo("status", TestApp.STATUS_PENDING)
                .get()
        ).documents.map(::parseApp).sortedBy { it.submittedAt }

    // ---- proofs --------------------------------------------------------------------------

    private fun parseProof(doc: DocumentSnapshot) = Proof(
        appId = doc.getString("appId").orEmpty(),
        groupId = doc.getString("groupId").orEmpty(),
        ownerUid = doc.getString("ownerUid").orEmpty(),
        testerUid = doc.getString("testerUid").orEmpty(),
        testerEmail = doc.getString("testerEmail").orEmpty(),
        day = (doc.getLong("day") ?: 0L).toInt(),
        fileId = doc.getString("fileId").orEmpty(),
        imageUrl = doc.getString("imageUrl").orEmpty(),
        capturedAt = doc.getLong("capturedAt") ?: 0L,
        usageMs = doc.getLong("usageMs") ?: 0L
    )

    /** Idempotent by construction: same tester, same app, same day always writes the same id. */
    suspend fun recordProof(proof: Proof) {
        await(
            db.collection("proofs")
                .document(Proof.id(proof.appId, proof.testerUid, proof.day))
                .set(
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
                        "usageMs" to proof.usageMs
                    )
                )
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
    suspend fun proofsForApp(appId: String, ownerUid: String): List<Proof> =
        await(
            db.collection("proofs")
                .whereEqualTo("appId", appId)
                .whereEqualTo("ownerUid", ownerUid)
                .get()
        ).documents.map(::parseProof)

    /**
     * What this tester has already posted in one group today.
     *
     * Keyed on the group's day rather than a timestamp window, so a report at 23:59 and another at
     * 00:01 are the same day if the group says they are.
     */
    suspend fun myProofsForDay(uid: String, groupId: String, day: Int): Set<String> =
        await(
            db.collection("proofs")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("day", day)
                .get()
        ).documents.map(::parseProof).filter { it.meetsBar }.map { it.appId }.toSet()
}
