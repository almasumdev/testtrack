package com.eazyverse.testtrack.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R

/**
 * Crew chat on the notification shade: one notification per crew, replaced as messages come, so a
 * busy evening is "5 new in Crew 81" rather than five. A mention has its own, louder channel, so it
 * can be heard while ordinary messages are quiet. Tapping opens that crew's chat.
 *
 * No Reply or Mark-read actions yet: a reply from the shade needs the composer's rules (links,
 * the day's allowance) somewhere other than the chat, and that is a later piece.
 */
object ChatNotifications {
    const val CHANNEL = "crew_chat"
    const val CHANNEL_MENTIONS = "crew_chat_mentions"

    /** Both channels, made with the others so they are in system settings before the first message. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Crew chat", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New messages in your crews' chats."
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_MENTIONS) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_MENTIONS, "Crew chat mentions", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When someone in a crew chat mentions you."
                },
            )
        }
    }

    /**
     * A chat push, drawn. The server's data: `ref` the crew, `count` the messages waiting,
     * `mention` "1" when one of them names you, and `title` and `body` in its own words.
     *
     * The caller has checked the permission; the notify is in runCatching for a switch flipped since.
     */
    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        data: Map<String, String>,
    ) {
        val crewId = data["ref"].orEmpty()
        if (crewId.isBlank()) return
        val title = data["title"].orEmpty()
        if (title.isBlank()) return
        val count = data["count"]?.toIntOrNull() ?: 1
        val mention = data["mention"] == "1"
        // "Crew 81" and "5 new messages. Sofia: ..." from the server read as one line here.
        val heading = if (count > 1 && !mention) "$count new in $title" else title
        val body = data["body"].orEmpty().removePrefix("$count new messages. ")
        ensureChannels(context)
        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN_CHAT, crewId)
            }
        val pending =
            PendingIntent.getActivity(
                context,
                tag(crewId).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(context, if (mention) CHANNEL_MENTIONS else CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(heading)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setNumber(count)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setOnlyAlertOnce(!mention)
                .setPriority(if (mention) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pending)
                .build()
        runCatching { NotificationManagerCompat.from(context).notify(tag(crewId), ID, notification) }
    }

    /** Gone from the shade: the chat it was about is open. */
    fun cancel(
        context: Context,
        crewId: String,
    ) {
        runCatching { NotificationManagerCompat.from(context).cancel(tag(crewId), ID) }
    }

    private fun tag(crewId: String) = "chat:$crewId"

    /** One id for all of them; the tag tells crews apart. */
    private const val ID = 7_301
}
