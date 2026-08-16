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

/** Raised when an admin has barred this account from submitting. */
class BlockedException(reason: String) : Exception(
    if (reason.isBlank())
        "An admin has paused your account, so you can't submit an app right now."
    else
        "An admin has paused your account, so you can't submit an app right now. Reason given: " +
            reason
)

/**
 * The one event kind a tester's device writes rather than an admin.
 *
 * Mirrored in firestore.rules, which admits this string and no other from a non-admin.
 */
const val EVENT_REMOVED_MISSED = "removed_missed"

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

    /**
     * The reason an admin gave for pausing this account, or null if it is in good standing.
     *
     * An empty string is a block with no reason recorded, which is still a block — hence a nullable
     * return rather than a blank one, so the two cannot be confused at the call site.
     */
    suspend fun blockedReason(uid: String): String? {
        val doc = await(db.collection("users").document(uid).get())
        if (doc.getBoolean("blocked") != true) return null
        return doc.getString("blockedReason").orEmpty()
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
        runDays = (doc.getLong("runDays") ?: RUN_DAYS.toLong()).toInt(),
        graceDays = (doc.getLong("graceDays") ?: 0L).toInt(),
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
        placedAt = doc.getLong("placedAt") ?: 0L,
        status = doc.getString("status") ?: TestApp.STATUS_PENDING,
        // Absent from every document written before this existed, which reads as not removed.
        removed = doc.getBoolean("removed") ?: false,
        removedAt = doc.getLong("removedAt") ?: 0L,
        removedReason = doc.getString("removedReason").orEmpty()
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
        // Asked before the write so a blocked account is told why, in words. The rules refuse this
        // anyway, but as a bare PERMISSION_DENIED that names nothing and reads like a fault.
        blockedReason(uid)?.let { throw BlockedException(it) }

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
        usageMs = doc.getLong("usageMs") ?: 0L,
        runStartedAt = doc.getLong("runStartedAt") ?: 0L
    )

    /** Idempotent by construction: same tester, same app, same day always writes the same id. */
    suspend fun recordProof(proof: Proof) {
        await(
            db.collection("proofs")
                .document(
                    Proof.id(proof.appId, proof.testerUid, proof.day, proof.runStartedAt)
                )
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
                        "usageMs" to proof.usageMs,
                        "runStartedAt" to proof.runStartedAt
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
    /**
     * @param requireBar counts only visits that cleared [Proof.MIN_USAGE_MS]. True for the day's
     *   progress, which is what the tester is asked for. False for [Enforcement], which must
     *   agree with the security rules — and a rule can only ask whether a proof document exists,
     *   not how long it recorded.
     */
    suspend fun myProofsForDay(
        uid: String,
        groupId: String,
        day: Int,
        runStartedAt: Long,
        requireBar: Boolean = true
    ): Set<String> =
        await(
            db.collection("proofs")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("day", day)
                // The run, as well as the day. A restarted cohort numbers its days from zero
                // again, so without this it opens showing the previous run's attendance.
                .whereEqualTo("runStartedAt", runStartedAt)
                .get()
        ).documents.map(::parseProof)
            .filter { !requireBar || it.meetsBar }
            .map { it.appId }
            .toSet()

    /** Every day this tester has posted anything in one group, whatever app it was about. */
    suspend fun myReportedDays(uid: String, groupId: String): Set<Int> =
        await(
            db.collection("proofs")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .get()
        ).documents.map(::parseProof).map { it.day }.toSet()

    // ---- enforcement ---------------------------------------------------------------------

    /**
     * Drops a member and their app out of a cohort.
     *
     * The only write in this file that touches `groups`, and it is allowed for one narrow case:
     * the owner of an app removing someone who has not opened it for [Compliance.MISSES_TO_REMOVE]
     * completed days running. The rules re-derive that from the proof documents themselves before
     * accepting either half, so what makes this safe is not the checks in [Enforcement] — it is
     * that a client which skipped them would be refused.
     *
     * Both halves go in one transaction. Half an eviction is worse than none: a group that still
     * lists a member whose app has left has a slot it cannot fill, and an app still pointing at a
     * group that has forgotten it turns up in every member's worklist for ever.
     *
     * The evidence is written down as well as acted on. `lastEviction` is the audit trail, and it
     * is also what the rule reads to know which app was missed.
     */
    /**
     * Takes somebody's app out of testing for missing their days, without taking their slot.
     *
     * What [evict] used to be called for and no longer is. Evicting dropped the member, which
     * dropped the group to twelve and punished the twelve as much as the one; this leaves the
     * cohort exactly as it was and stops the app being tested. Emptying a slot is an admin's
     * decision now, taken in the console, with the person in front of them.
     *
     * One document and three fields, so there is no transaction and nothing to keep in step. Safe
     * to run twice: every device in the cohort reaches the same conclusion from the same proofs,
     * and the second write says what the first one said.
     *
     * `removedReason` is left blank on purpose. Blank is what the apps read as "no admin typed
     * this", which is the difference between "an admin decided something about you" and "the
     * count caught up with you".
     */
    suspend fun softRemove(targetAppId: String, byAppId: String) {
        await(
            db.collection("apps").document(targetAppId).update(
                mapOf(
                    "removed" to true,
                    "removedAt" to System.currentTimeMillis(),
                    "removedReason" to "",
                    // The audit trail, and the rule's argument: it names the app that went
                    // unopened, which is what lets the server go and check the proofs itself.
                    "removedByAppId" to byAppId
                )
            )
        )
    }

    suspend fun evict(
        groupId: String,
        targetUid: String,
        targetAppId: String,
        byUid: String,
        byAppId: String,
        day: Int
    ) {
        val groupRef = db.collection("groups").document(groupId)
        val appRef = db.collection("apps").document(targetAppId)
        val now = System.currentTimeMillis()

        await(
            db.runTransaction { tx ->
                val group = tx.get(groupRef)
                if (!group.exists()) return@runTransaction null

                val members = (group.get("memberUids") as? List<*>)
                    ?.filterIsInstance<String>().orEmpty()

                // Someone else's sweep got here first. Every device in the cohort runs the same
                // check against the same proofs, so concurrent agreement is the normal case, not
                // an error — the second one through has nothing left to do.
                if (targetUid !in members) return@runTransaction null

                val appIds = (group.get("appIds") as? List<*>)
                    ?.filterIsInstance<String>().orEmpty()

                tx.update(
                    groupRef,
                    mapOf(
                        "memberUids" to members - targetUid,
                        "appIds" to appIds - targetAppId,
                        "lastEviction" to mapOf(
                            "uid" to targetUid,
                            "appId" to targetAppId,
                            "byUid" to byUid,
                            "byAppId" to byAppId,
                            "day" to day,
                            "at" to now
                        )
                    )
                )
                tx.update(
                    appRef,
                    mapOf(
                        "groupId" to "",
                        "status" to TestApp.STATUS_PENDING,
                        "placedAt" to 0L,
                        "unplacedByAppId" to byAppId,
                        "unplacedAt" to now
                    )
                )

                // The permanent record. `lastEviction` above is only the most recent one on this
                // group and the next removal overwrites it, so without this the history an admin
                // needs when deciding whether to approve someone again is the one thing that never
                // survived. Events are append-only and nothing deletes them.
                tx.set(
                    db.collection("events").document(),
                    mapOf(
                        "uid" to targetUid,
                        "type" to EVENT_REMOVED_MISSED,
                        "title" to "Your app was removed from its group",
                        "body" to "Two days went by without you opening the other apps.",
                        "groupId" to groupId,
                        "appId" to targetAppId,
                        "byAppId" to byAppId,
                        "actorUid" to byUid,
                        "day" to day,
                        "createdAt" to now
                    )
                )
                null
            }
        )
    }
}
