package com.eazyverse.testtrack.data

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Admin decisions, delivered by the device that cares about them.
 *
 * A tester needs to hear four things they did not cause: their app was placed in a group, it was
 * taken back out, the group it was in was dissolved, or the run it belongs to has ended. All four
 * happen in the admin console, which is a phone app — and a phone app cannot send push. FCM's v1
 * API authenticates with a service account, and a service account key shipped inside an APK is a
 * service account key in everybody's hands.
 *
 * So the admin writes the decision down and this reads it. The cost is latency: a device that is
 * never opened hears nothing until [ReminderWorker] next runs. That is the right trade for news
 * measured in days — an app is not approved twice, and nobody is waiting on the notification to
 * know whether to test tonight.
 *
 * The document is also the audit trail. `actorUid` records which admin did it, which matters the
 * moment there is more than one.
 */
object AdminEvents {

    private const val PREFS = "testtrack.events"
    private const val KEY_SEEN = "last_seen_at"

    /**
     * Nothing older than this is ever raised.
     *
     * A phone that has been off for a fortnight should not open into two weeks of stale news, and
     * a fresh install must not replay the entire history of an account.
     */
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Marks everything up to now as already seen, without raising any of it.
     *
     * Called the first time an account signs in on this device. Without it, a tester who joins
     * today would be greeted by every decision ever made about them.
     */
    fun markCaughtUp(context: Context) {
        prefs(context).edit().putLong(KEY_SEEN, System.currentTimeMillis()).apply()
    }

    /**
     * Raises anything new addressed to [uid], then remembers how far it got.
     *
     * The watermark is only advanced past events that were actually notified, so a failure part
     * way through means they are seen again next time rather than lost.
     */
    suspend fun drain(context: Context, uid: String) {
        val store = prefs(context)
        val since = store.getLong(KEY_SEEN, 0L)
            .coerceAtLeast(System.currentTimeMillis() - MAX_AGE_MS)

        val snapshot = await(
            db.collection("events")
                .whereEqualTo("uid", uid)
                .whereGreaterThan("createdAt", since)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .limit(20)
                .get()
        )
        if (snapshot.isEmpty) return

        var high = since
        snapshot.documents.forEach { doc ->
            val title = doc.getString("title").orEmpty()
            if (title.isNotBlank()) {
                ReminderWorker.notify(
                    context = context,
                    // Keyed on the event, so the same decision arriving twice — this pass and a
                    // push, if one ever exists — replaces rather than stacks.
                    id = doc.id.hashCode(),
                    title = title,
                    body = doc.getString("body").orEmpty(),
                    groupId = doc.getString("groupId")?.takeIf { it.isNotBlank() }
                )

                // A removal is the one thing this device can also work out for itself: the cohort
                // simply vanishes from under it, and [Enforcement] says so. Now that the removal
                // writes an event too, both would fire — so the event, which is the only one of
                // the two that knows *why*, claims the group and the sweep stays quiet about it.
                if (doc.getString("type") == EVENT_REMOVED_MISSED) {
                    doc.getString("groupId")?.takeIf { it.isNotBlank() }
                        ?.let { Enforcement.markExplained(context, it) }
                }
            }
            high = maxOf(high, doc.getLong("createdAt") ?: 0L)
        }

        store.edit().putLong(KEY_SEEN, high).apply()
    }

    /** Signing out must not leave the next account inheriting this one's watermark. */
    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_SEEN).apply()
    }
}
