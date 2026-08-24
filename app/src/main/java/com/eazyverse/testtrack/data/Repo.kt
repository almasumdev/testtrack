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
 * An app owner's own "update available" broadcast to their cohort — the other event kind a
 * tester's device writes. Named here, rather than left as a literal at the one call site, so
 * [AdminEvents] can recognise it and offer a Play Store shortcut without repeating the string.
 */
const val EVENT_OWNER_BROADCAST = "owner_broadcast"

/**
 * An app owner asking their cohort for feedback — the third event kind a tester's device
 * writes. Named here for the same reason as [EVENT_OWNER_BROADCAST]: so [AdminEvents] can
 * recognise it and route its notification straight to the feedback prompt rather than just
 * opening the group.
 */
const val EVENT_FEEDBACK_ASK = "feedback_ask"

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
        // Absent everywhere before flagging existed, which reads as never flagged.
        flaggedAt = doc.getLong("flaggedAt") ?: 0L,
        flaggedByAppId = doc.getString("flaggedByAppId").orEmpty(),
        reinstatedAt = doc.getLong("reinstatedAt") ?: 0L,
        // Absent everywhere before this existed, which reads as nobody having reported anything.
        reportedAt = doc.getLong("reportedAt") ?: 0L,
        reportedBy = doc.getString("reportedBy").orEmpty(),
        reportedReason = doc.getString("reportedReason").orEmpty(),
        // Absent everywhere before broadcasting existed, which reads as never sent.
        lastBroadcastAt = doc.getLong("lastBroadcastAt") ?: 0L,
        // Absent everywhere before asking for feedback existed, which reads as never asked.
        lastFeedbackAskAt = doc.getLong("lastFeedbackAskAt") ?: 0L,
        // Absent everywhere before this existed, which reads as not skippable.
        skippable = doc.getBoolean("skippable") ?: false,
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
        runStartedAt = doc.getLong("runStartedAt") ?: 0L,
        // Absent on everything posted before manual capture existed, which reads as automated —
        // the only kind there was.
        manual = doc.getBoolean("manual") ?: false
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
                        "runStartedAt" to proof.runStartedAt,
                        "manual" to proof.manual
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

    /**
     * The same day [myProofsForDay] reads, but appId -> whether that proof was supplied by hand —
     * for [GroupViewModel] to mark a manually-completed day yellow rather than green. One query
     * rather than two: [myProofsForDay]'s own callers in [Enforcement] don't need this, so it
     * stays a separate function instead of changing that one's return shape for everybody.
     */
    suspend fun myProofDetailsForDay(
        uid: String,
        groupId: String,
        day: Int,
        runStartedAt: Long
    ): Map<String, Boolean> =
        await(
            db.collection("proofs")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("day", day)
                .whereEqualTo("runStartedAt", runStartedAt)
                .get()
        ).documents.map(::parseProof)
            .filter { it.meetsBar }
            .associate { it.appId to it.manual }

    /** Every day this tester has posted anything in one group, whatever app it was about. */
    suspend fun myReportedDays(uid: String, groupId: String): Set<Int> =
        await(
            db.collection("proofs")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .get()
        ).documents.map(::parseProof).map { it.day }.toSet()

    // ---- feedback --------------------------------------------------------------------------

    private fun parseFeedback(doc: DocumentSnapshot) = Feedback(
        appId = doc.getString("appId").orEmpty(),
        groupId = doc.getString("groupId").orEmpty(),
        ownerUid = doc.getString("ownerUid").orEmpty(),
        testerUid = doc.getString("testerUid").orEmpty(),
        day = (doc.getLong("day") ?: 0L).toInt(),
        runStartedAt = doc.getLong("runStartedAt") ?: 0L,
        text = doc.getString("text").orEmpty(),
        createdAt = doc.getLong("createdAt") ?: 0L
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
    suspend fun submitFeedback(app: TestApp, uid: String, groupId: String, day: Int, runStartedAt: Long, text: String) {
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
                "createdAt" to System.currentTimeMillis()
            )
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
                "createdAt" to System.currentTimeMillis()
            )
        )
        await(batch.commit())
    }

    /** Which apps this tester has already left today's note on, so the row can stop offering it. */
    suspend fun myFeedbackToday(uid: String, groupId: String, day: Int, runStartedAt: Long): Set<String> =
        await(
            db.collection("feedback")
                .whereEqualTo("testerUid", uid)
                .whereEqualTo("groupId", groupId)
                .whereEqualTo("day", day)
                .whereEqualTo("runStartedAt", runStartedAt)
                .get()
        ).documents.map(::parseFeedback).map { it.appId }.toSet()

    /** Every note left on one app, newest first — the owner's own feed of what testers found. */
    suspend fun feedbackForApp(appId: String, ownerUid: String): List<Feedback> =
        await(
            db.collection("feedback")
                .whereEqualTo("appId", appId)
                .whereEqualTo("ownerUid", ownerUid)
                .get()
        ).documents.map(::parseFeedback).sortedByDescending { it.createdAt }

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
    suspend fun flagAtRisk(targetAppId: String, byAppId: String) {
        await(
            db.collection("apps").document(targetAppId).update(
                mapOf(
                    "flaggedAt" to System.currentTimeMillis(),
                    // The audit trail, and the rule's argument: it names the app that went
                    // unopened, which is what lets the server go and check the proofs itself.
                    "flaggedByAppId" to byAppId
                )
            )
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
    suspend fun reportProblem(appId: String, uid: String, reason: String) {
        await(
            db.collection("apps").document(appId).update(
                mapOf(
                    "reportedAt" to System.currentTimeMillis(),
                    "reportedBy" to uid,
                    "reportedReason" to reason.take(NOTES_MAX)
                )
            )
        )
    }

    /**
     * Takes a report back — the tester who filed it changing their mind, without waiting on an
     * admin to notice and clear it from the console.
     *
     * The rules only accept this from `reportedBy` itself, so the write below either lands
     * exactly as sent or is refused outright; there is no partial-success case to handle.
     */
    suspend fun clearMyReport(appId: String) {
        await(
            db.collection("apps").document(appId).update(
                mapOf(
                    "reportedAt" to 0L,
                    "reportedBy" to "",
                    "reportedReason" to ""
                )
            )
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
    suspend fun broadcastUpdate(app: TestApp, group: TestGroup) {
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
                    "createdAt" to now
                )
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
    suspend fun askForFeedback(app: TestApp, group: TestGroup) {
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
                    "createdAt" to now
                )
            )
        }
        batch.update(db.collection("apps").document(app.id), mapOf("lastFeedbackAskAt" to now))
        await(batch.commit())
    }

    /** {appId}__{uid}, exactly as firestore.rules' skips block expects to derive it. */
    private fun skipId(appId: String, uid: String) = "${appId}__$uid"

    /** Whether [uid] has personally opted out of testing [appId] — see the `skips` collection. */
    suspend fun hasSkipped(appId: String, uid: String): Boolean =
        await(db.collection("skips").document(skipId(appId, uid)).get()).exists()

    /** Every app in [groupId] the caller has personally skipped, for [TestRow]'s own state. */
    suspend fun mySkips(uid: String, groupId: String): Set<String> =
        await(
            db.collection("skips")
                .whereEqualTo("uid", uid)
                .whereEqualTo("groupId", groupId)
                .get()
        ).documents.mapNotNull { it.getString("appId") }.toSet()

    /** Opts the caller out of testing [app] — only admitted when [TestApp.skippable] is set. */
    suspend fun setSkip(app: TestApp, uid: String, groupId: String) {
        await(
            db.collection("skips").document(skipId(app.id, uid)).set(
                mapOf(
                    "appId" to app.id,
                    "uid" to uid,
                    "groupId" to groupId,
                    "skippedAt" to System.currentTimeMillis()
                )
            )
        )
    }

    /** Reverses [setSkip] — the app owes its ordinary day again for this member from now on. */
    suspend fun clearSkip(appId: String, uid: String) {
        await(db.collection("skips").document(skipId(appId, uid)).delete())
    }
}
