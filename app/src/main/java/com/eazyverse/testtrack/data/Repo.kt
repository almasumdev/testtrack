package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raised when a package name belongs to another member's app. */
class AppTakenException(packageName: String, ownerEmail: String) : Exception(
    if (ownerEmail.isBlank()) "$packageName is already registered by another member."
    else "$packageName is already registered by $ownerEmail."
)

/**
 * Raised when the owner's own app has been closed and cannot be sent again.
 *
 * A package name is claimed by its first submission and never released, so an app that was turned
 * down, withdrawn, or has finished its fortnight cannot be resubmitted by the person who owns it.
 * That is deliberate rather than an oversight: an app turned down by Play after fourteen days
 * needs another fourteen, and whether a cohort spends its next fortnight on it is a decision
 * somebody makes rather than one a developer takes by pressing submit.
 *
 * Refused here for the message. The security rules refuse it anyway, by pinning `status` on an
 * owner's write, and a bare PERMISSION_DENIED tells nobody who to ask.
 */
class AppClosedException(label: String, status: String) : Exception(
    when (status) {
        TestApp.STATUS_REJECTED ->
            "$label was turned down, so it can't be sent again. Ask an admin to put it back."
        TestApp.STATUS_WITHDRAWN ->
            "You withdrew $label. Ask an admin to put it back in the queue."
        else ->
            "$label has finished a run. Ask an admin to start another one."
    }
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

    /**
     * Records the signed-in tester so other members' grids can show a name, not a raw uid.
     *
     * The name is written on the way in only while nobody has chosen one. This runs on every
     * sign-in, so writing it unconditionally would undo the profile screen: a tester sets their
     * name, signs out and back in, and Google's version lands on top of it.
     *
     * The address with its domain cut off counts as nobody having chosen. That is what this used
     * to store for everybody, so treating it as unset is what repairs the accounts that already
     * carry it, on their next sign-in, without touching a name anybody typed.
     */
    suspend fun upsertUser(uid: String, email: String, displayName: String) {
        val doc = db.collection("users").document(uid)
        val existing = runCatching { await(doc.get()).getString("displayName").orEmpty() }
            .getOrDefault("")
        val chosen = existing.isNotBlank() &&
            !existing.equals(email.substringBefore('@'), ignoreCase = true)

        val fields = mutableMapOf<String, Any>(
            "uid" to uid,
            "email" to email,
            "updatedAt" to System.currentTimeMillis()
        )
        if (!chosen) fields["displayName"] = displayName

        await(doc.set(fields, SetOptions.merge()))
    }

    /**
     * Records which handset this account signs in from.
     *
     * Its own collection rather than a field on the user document, and that is the whole of the
     * privacy design. `users/{uid}` is readable by every signed-in account, because a grid has to
     * turn a uid into a name; a device fingerprint sitting there would let any tester work out
     * which of their cohort share a phone. Here it is readable by its owner and by an admin, and
     * by nobody else.
     *
     * Keyed by uid, so each account claims one row and two accounts on one handset are two rows
     * carrying the same value. That is the whole detection: a duplicate is a repeated string.
     *
     * Failure is swallowed by the caller. A tester whose phone will not report an id still has a
     * working app, and this is a signal for an admin rather than a step in anybody's setup.
     */
    suspend fun claimDevice(uid: String, fingerprint: String) {
        await(
            db.collection("devices").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "deviceId" to fingerprint,
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
        displayName = doc.getString("displayName").orEmpty(),
        photo = doc.getString("photo").orEmpty()
    ).takeIf { it.email.isNotBlank() }

    /** This account's own row, for the profile screen. */
    suspend fun me(uid: String): Tester? =
        parseTester(await(db.collection("users").document(uid).get()))

    /**
     * The name and face this account shows the rest of its cohort.
     *
     * Written as one call because they are edited on one screen and a half-saved profile is worse
     * than an unsaved one. `photo` is a blank string rather than a deletion when it is cleared,
     * which keeps the shape of the document fixed and the rules' key list honest.
     */
    suspend fun updateProfile(uid: String, displayName: String, photo: String) {
        await(
            db.collection("users").document(uid).set(
                mapOf(
                    "displayName" to displayName.trim(),
                    "photo" to photo,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        )
    }

    /**
     * Everything this account has ever submitted, whatever became of it.
     *
     * Constrained on `ownerUid` because rules are not filters: the read rule admits an app to its
     * owner, so the query has to say who is asking or the whole set is refused before a single
     * document is looked at. Unsorted here and ordered by the screen, which wants live ones first
     * rather than oldest first.
     */
    suspend fun myApps(uid: String): List<TestApp> =
        await(
            db.collection("apps")
                .whereEqualTo("ownerUid", uid)
                .get()
        ).documents.map(::parseApp)

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
        removedReason = doc.getString("removedReason").orEmpty(),
        // Also absent from every document written before this existed, and blank there reads as
        // an app with nothing a tester needs before opening it, which is the honest answer.
        notes = doc.getString("notes").orEmpty()
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
    suspend fun submitApp(
        uid: String,
        email: String,
        packageName: String,
        name: String,
        notes: String
    ) {
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

        // Their own app, but finished with. Sending it again is a correction to a live record
        // everywhere else in this method, and here it would be a silent no-op: the merge below
        // leaves `status` alone, so the app would stay closed and the screen would report success.
        val closedAs = existing.getString("status").orEmpty()
        if (existing.exists() && closedAs in CLOSED) {
            throw AppClosedException(
                existing.getString("name")?.takeIf { it.isNotBlank() } ?: packageName,
                closedAs
            )
        }

        val body = mutableMapOf<String, Any>(
            "id" to packageName,
            "ownerUid" to uid,
            "ownerEmail" to email,
            "name" to name,
            "notes" to notes,
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
     * Replaces just the sign-in notes on an app the caller owns.
     *
     * Separate from [submitApp] because the notes are the one thing an owner routinely gets wrong
     * after the fact: the test account expires, or nobody could get in and they need to say so
     * today, and none of that should mean walking back through the submission form.
     *
     * No rule had to be deployed for this. The owner-update rule pins `status`, `groupId` and the
     * three `removed*` keys and says nothing about the rest, so a merge of any other field was
     * already permitted, whether or not the app has been placed in a group.
     */
    suspend fun updateNotes(packageName: String, notes: String) {
        await(
            db.collection("apps").document(packageName)
                .set(mapOf("notes" to notes), SetOptions.merge())
        )
    }

    /**
     * Withdraws an app, which no longer means destroying it.
     *
     * The document stays and its status changes. Deleting it took the only record that the app
     * had ever existed: the developer's profile kept the events but had nothing to point them at,
     * an admin could not undo anything, and the package name went back into circulation as though
     * it had never been claimed.
     *
     * Its proofs were always left behind, keyed by app id, and now they have something to belong
     * to again.
     *
     * Only from the queue. Withdrawing from inside a running cohort would leave twelve people
     * owing days to an app whose owner has walked away, and the rules refuse it for the same
     * reason: the group is an admin's to change.
     */
    suspend fun withdrawApp(packageName: String) {
        await(
            db.collection("apps").document(packageName).set(
                mapOf(
                    "status" to TestApp.STATUS_WITHDRAWN,
                    "withdrawnAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
        )
    }

    suspend fun app(id: String): TestApp? =
        await(db.collection("apps").document(id).get()).takeIf { it.exists() }?.let(::parseApp)

    /** The three states an owner cannot write their way out of. */
    private val CLOSED = setOf(
        TestApp.STATUS_REJECTED, TestApp.STATUS_WITHDRAWN, TestApp.STATUS_DONE
    )

    /** Everything in one cohort — the tester's worklist, and the owner's peers. */
    suspend fun appsInGroup(groupId: String): List<TestApp> =
        await(db.collection("apps").whereEqualTo("groupId", groupId).get())
            .documents.map(::parseApp).sortedBy { it.label.lowercase() }

    /**
     * The same cohort, live.
     *
     * A one-shot read is right for a list that only changes when its owner changes it. This one
     * changes when somebody else does: an admin takes an app out of testing and thirteen people
     * are supposed to stop opening it and uninstall it. Read once on entry, those thirteen carry
     * on being asked to install it until each of them happens to leave the screen and come back,
     * which for anybody sitting on it is never.
     *
     * Firestore answers a listener from the local cache first, so this loses none of the instant
     * first paint the cache was there for.
     */
    fun watchAppsInGroup(groupId: String): Flow<List<TestApp>> = callbackFlow {
        val registration = db.collection("apps").whereEqualTo("groupId", groupId)
            .addSnapshotListener { snapshot, error ->
                when {
                    // Closed rather than swallowed. A listener that has failed will not start
                    // working again on its own, and only the collector can decide what to do.
                    error != null -> close(error)
                    snapshot != null -> trySend(
                        snapshot.documents.map(::parseApp).sortedBy { it.label.lowercase() }
                    )
                }
            }
        awaitClose { registration.remove() }
    }

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
