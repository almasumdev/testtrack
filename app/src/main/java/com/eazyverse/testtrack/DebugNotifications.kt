package com.eazyverse.testtrack

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eazyverse.testtrack.data.PushRepo

/**
 * Debug-only. Posts a local notification built the same way as a real crew reminder (same channel,
 * same small icon) so the owner can look at how TestTrack notifications appear on a phone without
 * waiting for a push.
 *
 * Lives in the root package (the shell) so ui can call it without importing data.
 * A release build returns on the first line, so nothing is ever posted there.
 */
object DebugNotifications {
    private const val TEST_ID = 9001

    fun showTest(context: Context) {
        if (!BuildConfig.DEBUG) return
        val manager = NotificationManagerCompat.from(context)
        // The app's own flow asks for the permission. Without it, stay quiet.
        if (!PushRepo.granted(context) || !manager.areNotificationsEnabled()) return
        PushRepo.ensureChannel(context)
        val notification =
            NotificationCompat
                .Builder(context, PushRepo.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Test notification")
                .setContentText("This is how TestTrack notifications look.")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
        // Fixed id, so another tap replaces the notification instead of stacking.
        runCatching { manager.notify(TEST_ID, notification) }
    }
}
