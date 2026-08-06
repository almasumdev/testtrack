package com.eazyverse.testtrack.data

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Incoming push.
 *
 * Messages are sent **data-only**, on purpose. A message carrying a `notification` block is posted
 * by the system when the app is in the background, and this class never runs — which means no
 * channel of ours, no icon of ours, and no way to open the group it is about. Data-only messages
 * always arrive here, so every notification looks the same whichever state the app is in.
 *
 * Recognised keys:
 *
 * ```
 * title    headline. Required; a message without one is dropped rather than posted blank.
 * body     supporting line.
 * groupId  optional. Tapping opens that group instead of home.
 * ```
 */
class PushService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * A token can be reissued at any time, unprompted.
     *
     * Written straight back to Firestore, because until it is, this device is unreachable and
     * nothing else in the app has a reason to notice.
     */
    override fun onNewToken(token: String) {
        val uid = AuthRepo.uid ?: return
        scope.launch { PushRepo.save(applicationContext, uid, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body.orEmpty()

        // Nothing to show and nowhere to send them: a tester who has signed out has no groups, and
        // a notification about someone else's cohort would be worse than silence.
        if (AuthRepo.uid == null) return

        PushRepo.ensureChannel(this)
        if (!PushRepo.granted(this)) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data["groupId"]?.let { putExtra(MainActivity.EXTRA_GROUP_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            // One slot per group, so a nudge about Group A does not overwrite the intent of a
            // nudge about Group C that is still on screen.
            (data["groupId"] ?: "").hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, PushRepo.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .build()

        // Same id per group, so a second reminder about the same cohort replaces the first rather
        // than stacking up over a day of them.
        runCatching {
            NotificationManagerCompat.from(this)
                .notify((data["groupId"] ?: "general").hashCode(), notification)
        }
    }
}
