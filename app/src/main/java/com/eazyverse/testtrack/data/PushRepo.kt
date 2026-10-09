package com.eazyverse.testtrack.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.eazyverse.testtrack.net.Api
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Push: whether a notification would show, where it lands, and telling our server where to send.
 *
 * Our server sends every push (see app/services/notify.py in the backend): crew news, reminders,
 * feedback. This phone's Firebase token goes to it after sign-in and is taken back at sign-out.
 *
 * The version before this one registered with the old Firestore system instead: a token document
 * under the user and a topic per group. That version is still out there for people who have not
 * updated, and its sender may still be running, so a phone that has updated leaves it once, on its
 * first run here ([leaveOldSystem]), and PushService ignores anything that is not ours.
 */
object PushRepo {
    private const val PREFS = "testtrack.push"
    private const val KEY_INSTALL_ID = "install_id"
    private const val KEY_TOPICS = "topics"
    private const val KEY_LEFT_OLD = "left_old_system"

    const val CHANNEL_REMINDERS = "reminders"

    private suspend fun <T> await(task: Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
     * The channel crew news lands on. The id is the one the old version created, so a phone that
     * updated keeps whatever the person chose for it in system settings.
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
                "Crews",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Your crews: filling, starting, apps to open tonight, reports and missed days."
            },
        )
    }

    // ---- the API -------------------------------------------------------------------------

    /**
     * Tells our server where this phone is, for whoever is signed in on it.
     *
     * Safe on every launch; the server keys on the token and moves it to whoever registered it
     * last. A failure only means no push until the next launch.
     */
    suspend fun toServer() {
        val token = runCatching { await(FirebaseMessaging.getInstance().token) }.getOrNull() ?: return
        runCatching { Api.keepPushToken(token) }
    }

    /** The same, for a token FCM hands over unasked. */
    suspend fun toServer(token: String) {
        runCatching { Api.keepPushToken(token) }
    }

    /**
     * Stop our server sending to this phone. **Await this before the session is dropped**: the
     * request needs the very credentials sign-out is about to throw away.
     */
    suspend fun forgetOnServer() {
        val token = runCatching { await(FirebaseMessaging.getInstance().token) }.getOrNull() ?: return
        runCatching { withTimeoutOrNull(4_000) { Api.dropPushToken(token) } }
    }

    // ---- leaving the old version's push --------------------------------------------------

    /**
     * Once per install: stop hearing from the old Firestore-era system.
     *
     * Unsubscribes from the group topics the old version joined, which FCM keeps until told
     * otherwise, and deletes the token document it wrote under the user, which its sender reads.
     * Best effort: a failure is tried again next launch, and PushService ignores old messages
     * anyway.
     */
    suspend fun leaveOldSystem(
        context: Context,
        uid: String?,
    ) {
        val store = prefs(context)
        if (store.getBoolean(KEY_LEFT_OLD, false)) return
        val messaging = FirebaseMessaging.getInstance()
        var clean = true
        store.getStringSet(KEY_TOPICS, emptySet()).orEmpty().forEach { name ->
            if (runCatching { await(messaging.unsubscribeFromTopic(name)) }.isFailure) clean = false
        }
        val install = store.getString(KEY_INSTALL_ID, null)
        if (uid != null && install != null) {
            val gone =
                runCatching {
                    withTimeoutOrNull(20_000) {
                        await(
                            FirebaseFirestore
                                .getInstance()
                                .collection("users")
                                .document(uid)
                                .collection("tokens")
                                .document(install)
                                .delete(),
                        )
                        // delete() resolves to null, so mark success; null below means it timed out.
                        true
                    }
                }
            if (gone.getOrNull() != true) clean = false
        }
        if (clean) {
            store
                .edit()
                .remove(KEY_TOPICS)
                .remove(KEY_INSTALL_ID)
                .putBoolean(KEY_LEFT_OLD, true)
                .apply()
        }
    }
}
