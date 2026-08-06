package com.eazyverse.testtrack.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Push registration.
 *
 * Two halves that answer different questions:
 *
 *  - **A token per device**, so a message can reach one person — "you have four apps left today".
 *    Stored at `users/{uid}/tokens/{installId}`.
 *  - **A topic per group**, so a message can reach a cohort without the sender first reading
 *    thirteen tokens — "Group A is short a member". Subscription happens on the device, so nothing
 *    server-side needs to keep a roster in step with `memberUids`.
 *
 * Keyed by a stable install id rather than by the token itself. FCM rotates tokens — on restore, on
 * app data clear, on its own schedule — and a token-keyed document would leave the old one behind
 * every time, so every send would go to a growing pile of dead addresses.
 */
object PushRepo {

    private const val PREFS = "testtrack.push"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_TOPICS = "topics"

    const val CHANNEL_REMINDERS = "reminders"

    private val db get() = FirebaseFirestore.getInstance()

    private suspend fun <T> await(task: Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * This install, named once and for good.
     *
     * Not the Firebase installation id: that one is reset by the same events that rotate the token,
     * which would defeat the point of keying on something stable.
     */
    private fun installId(context: Context): String {
        val store = prefs(context)
        store.getString(KEY_INSTALL_ID, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        store.edit().putString(KEY_INSTALL_ID, fresh).apply()
        return fresh
    }

    // ---- permission ----------------------------------------------------------------------

    /**
     * Whether a notification would actually appear.
     *
     * Read live, never stored. From Android 13 this is a runtime permission, and before that it is
     * a switch in system settings — both can be turned off behind our back, so a cached `true` is
     * a lie the moment someone changes their mind.
     */
    fun granted(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

    /**
     * The channel a reminder lands on.
     *
     * Created before the permission is asked for, deliberately: Android shows the channel's name in
     * the system settings the tester is about to be sent to, and a channel that appears only after
     * the grant is a channel they cannot find when they go looking for it.
     */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_REMINDERS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDERS,
                "Daily reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Nudges when apps in your groups are still waiting on you today."
            }
        )
    }

    // ---- token ---------------------------------------------------------------------------

    /**
     * Publishes this device's address for [uid].
     *
     * Safe to call on every launch: the document id is the install, so this overwrites rather than
     * accumulating. Failure is not surfaced — a tester who cannot be reached by push has lost a
     * convenience, not the ability to test, and an error banner over that would be noise.
     */
    suspend fun register(context: Context, uid: String) {
        val token = runCatching { await(FirebaseMessaging.getInstance().token) }.getOrNull()
            ?: return
        save(context, uid, token)
    }

    /** Called from [PushService.onNewToken] as well, where the token arrives unasked. */
    suspend fun save(context: Context, uid: String, token: String) {
        runCatching {
            await(
                db.collection("users").document(uid)
                    .collection("tokens").document(installId(context))
                    .set(
                        mapOf(
                            "token" to token,
                            "platform" to "android",
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
            )
        }
    }

    // ---- topics --------------------------------------------------------------------------

    /** Firestore ids allow characters an FCM topic does not. Same input, same topic, always. */
    fun topic(groupId: String) = "group_" + groupId.replace(Regex("[^a-zA-Z0-9-_.~%]"), "_")

    /**
     * Brings subscriptions in line with the groups this tester is actually in.
     *
     * A diff rather than a blanket re-subscribe: FCM keeps a subscription until it is revoked, so
     * leaving an old group would otherwise mean hearing about it for good. The set of what we are
     * subscribed to is kept locally because there is no API to ask FCM what a device is on.
     */
    suspend fun syncTopics(context: Context, groupIds: Collection<String>) {
        val wanted = groupIds.map(::topic).toSet()
        val store = prefs(context)
        val held = store.getStringSet(KEY_TOPICS, emptySet()).orEmpty()
        if (wanted == held) return

        val messaging = FirebaseMessaging.getInstance()
        val settled = mutableSetOf<String>()

        (wanted - held).forEach { name ->
            if (runCatching { await(messaging.subscribeToTopic(name)) }.isSuccess) settled += name
        }
        (held - wanted).forEach { name ->
            // A failed unsubscribe stays in the held set, so the next sync tries it again rather
            // than quietly leaving the device on a group it has left.
            if (runCatching { await(messaging.unsubscribeFromTopic(name)) }.isFailure) settled += name
        }

        store.edit().putStringSet(KEY_TOPICS, settled + (wanted intersect held)).apply()
    }

    // ---- sign-out ------------------------------------------------------------------------

    /**
     * Stops this device hearing anything for the account that is leaving.
     *
     * Both halves matter and for different reasons: the token document would keep a signed-out
     * phone on the reminder list, and a topic subscription lives in FCM rather than in our data,
     * so it would survive not just sign-out but a reinstall.
     *
     * **Await this before signing out.** The delete is a Firestore write and goes out under
     * whatever credentials are current when it is sent — fired off next to `AuthRepo.signOut()` it
     * would race, reach the server unauthenticated, and come back PERMISSION_DENIED, leaving the
     * token behind for good.
     */
    suspend fun clear(context: Context, uid: String?) {
        val store = prefs(context)
        val messaging = FirebaseMessaging.getInstance()
        store.getStringSet(KEY_TOPICS, emptySet()).orEmpty().forEach { name ->
            runCatching { await(messaging.unsubscribeFromTopic(name)) }
        }
        store.edit().remove(KEY_TOPICS).apply()

        if (uid != null) {
            runCatching {
                await(
                    db.collection("users").document(uid)
                        .collection("tokens").document(installId(context)).delete()
                )
            }
        }
    }
}
