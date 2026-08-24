package com.eazyverse.testtrack.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Raised when a package name belongs to another member's app. */
class AppTakenException(
    packageName: String,
    ownerEmail: String,
) : Exception(
        if (ownerEmail.isBlank()) {
            "$packageName is already registered by another member."
        } else {
            "$packageName is already registered by $ownerEmail."
        },
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
class AppClosedException(
    label: String,
    status: String,
) : Exception(
        when (status) {
            TestApp.STATUS_REJECTED ->
                "$label was turned down, so it can't be sent again. Ask an admin to put it back."
            TestApp.STATUS_WITHDRAWN ->
                "You withdrew $label. Ask an admin to put it back in the queue."
            else ->
                "$label has finished a run. Ask an admin to start another one."
        },
    )

/** Raised when an admin has barred this account from submitting. */
class BlockedException(
    reason: String,
) : Exception(
        if (reason.isBlank()) {
            "An admin has paused your account, so you can't submit an app right now."
        } else {
            "An admin has paused your account, so you can't submit an app right now. Reason given: " +
                reason
        },
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
    val db get() = FirebaseFirestore.getInstance()

    suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }
}
