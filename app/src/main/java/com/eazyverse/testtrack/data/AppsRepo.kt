package com.eazyverse.testtrack.data

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

// ---- apps ----------------------------------------------------------------------------

fun parseApp(doc: DocumentSnapshot) =
    TestApp(
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
        notes = doc.getString("notes").orEmpty(),
        // Absent everywhere before this existed, which reads as never synced from the owner's
        // own device — see TestApp.latestVersionCode.
        latestVersionCode = doc.getLong("latestVersionCode") ?: 0L,
        latestVersionName = doc.getString("latestVersionName").orEmpty(),
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
suspend fun Repo.submitApp(
    uid: String,
    email: String,
    packageName: String,
    name: String,
    notes: String,
) {
    // Asked before the write so a blocked account is told why, in words. The rules refuse this
    // anyway, but as a bare PERMISSION_DENIED that names nothing and reads like a fault.
    blockedReason(uid)?.let { throw BlockedException(it) }

    val doc = db.collection("apps").document(packageName)

    // A refusal here is itself the answer: app documents are readable by their owner and by
    // the cohort testing them, so a package we cannot read is one already registered by
    // someone whose group we are not in. Their address stays hidden, which is the point.
    val existing =
        try {
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
            closedAs,
        )
    }

    val body =
        mutableMapOf<String, Any>(
            "id" to packageName,
            "ownerUid" to uid,
            "ownerEmail" to email,
            "name" to name,
            "notes" to notes,
            "packageName" to packageName,
            "submittedAt" to (
                existing.getLong("submittedAt")?.takeIf { it > 0 }
                    ?: System.currentTimeMillis()
            ),
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
suspend fun Repo.updateNotes(
    packageName: String,
    notes: String,
) {
    await(
        db
            .collection("apps")
            .document(packageName)
            .set(mapOf("notes" to notes), SetOptions.merge()),
    )
}

/**
 * Records the versionCode/versionName the owner's own device just read from its Play-installed
 * copy of this app — see [TestApp.latestVersionCode]. Called only once that read has already
 * been confirmed to both come from Play and be newer than what is on record, so there is nothing
 * left to check here; this just writes it down.
 */
suspend fun Repo.updateLatestVersion(
    packageName: String,
    versionCode: Long,
    versionName: String,
) {
    await(
        db
            .collection("apps")
            .document(packageName)
            .set(
                mapOf(
                    "latestVersionCode" to versionCode,
                    "latestVersionName" to versionName,
                ),
                SetOptions.merge(),
            ),
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
suspend fun Repo.withdrawApp(packageName: String) {
    await(
        db.collection("apps").document(packageName).set(
            mapOf(
                "status" to TestApp.STATUS_WITHDRAWN,
                "withdrawnAt" to System.currentTimeMillis(),
            ),
            SetOptions.merge(),
        ),
    )
}

suspend fun Repo.app(id: String): TestApp? = await(db.collection("apps").document(id).get()).takeIf { it.exists() }?.let(::parseApp)

/** The three states an owner cannot write their way out of. */
private val CLOSED =
    setOf(
        TestApp.STATUS_REJECTED,
        TestApp.STATUS_WITHDRAWN,
        TestApp.STATUS_DONE,
    )

/** Everything in one cohort — the tester's worklist, and the owner's peers. */
suspend fun Repo.appsInGroup(groupId: String): List<TestApp> =
    await(db.collection("apps").whereEqualTo("groupId", groupId).get())
        .documents
        .map(::parseApp)
        .sortedBy { it.label.lowercase() }

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
fun Repo.watchAppsInGroup(groupId: String): Flow<List<TestApp>> =
    callbackFlow {
        val registration =
            db
                .collection("apps")
                .whereEqualTo("groupId", groupId)
                .addSnapshotListener { snapshot, error ->
                    when {
                        // Closed rather than swallowed. A listener that has failed will not start
                        // working again on its own, and only the collector can decide what to do.
                        error != null -> close(error)
                        snapshot != null ->
                            trySend(
                                snapshot.documents.map(::parseApp).sortedBy { it.label.lowercase() },
                            )
                    }
                }
        awaitClose { registration.remove() }
    }

/** Submitted, waiting for an admin to place it. */
suspend fun Repo.pendingApps(uid: String): List<TestApp> =
    await(
        db
            .collection("apps")
            .whereEqualTo("ownerUid", uid)
            .whereEqualTo("status", TestApp.STATUS_PENDING)
            .get(),
    ).documents.map(::parseApp).sortedBy { it.submittedAt }
