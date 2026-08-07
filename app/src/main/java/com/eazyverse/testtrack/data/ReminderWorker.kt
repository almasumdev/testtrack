package com.eazyverse.testtrack.data

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * The daily nudge, worked out on the phone.
 *
 * Nothing is sent to this device to make a reminder happen. The worker reads the same data the
 * home screen reads, counts what is still owed today, and raises a local notification if anything
 * is — which means reminders keep working with no server, no scheduler, and no fleet of tokens to
 * keep in step with who is still in which cohort.
 *
 * It also carries admin decisions. Those are written as documents rather than pushed
 * ([AdminEvents]), so the same pass that counts the day's work picks up "your app was placed in
 * Group B" and raises it too. One mechanism, one place notifications are decided.
 *
 * Timing is deliberately loose. WorkManager will not run more often than fifteen minutes and will
 * defer under Doze; a reminder that lands within an hour of the evening is a reminder that worked,
 * and one that wakes the phone on a schedule is a battery complaint.
 */
class ReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = AuthRepo.uid ?: return Result.success()
        if (!PushRepo.granted(applicationContext)) return Result.success()

        PushRepo.ensureChannel(applicationContext)

        // Admin decisions first: they are news, and a nudge underneath them reads as a
        // consequence of the news rather than a separate demand.
        runCatching { AdminEvents.drain(applicationContext, uid) }

        // Then the run's own rules, which are news of the same kind and produce the one warning
        // that is genuinely urgent: miss tonight as well and the group is gone.
        runCatching { raise(Enforcement.sweep(applicationContext, uid)) }

        runCatching { nudge(uid) }
        return Result.success()
    }

    /**
     * Turns a sweep into notifications.
     *
     * Distinct ids per group and per app, so a warning about one cohort does not overwrite a
     * warning about another. Everything here is local: none of it was sent to this device.
     */
    private fun raise(sweep: Enforcement.Sweep) {
        sweep.evictedFrom.forEach { group ->
            post(
                id = ID_EVICTED + slot(group),
                title = "Your app is out of $group",
                // Deliberately not asserting which of the three causes it was. See the matching
                // note in Enforcement.sweep.
                body = "It's back in the queue. That happens after two days without opening the " +
                    "other apps, and it also happens when an admin takes an app out or dissolves " +
                    "a group. Submit it again when you're ready."
            )
        }

        sweep.departed.forEach { (group, label) ->
            post(
                id = ID_DEPARTED + slot(label),
                title = "Stop testing $label",
                body = "It has left ${group.ifBlank { "your group" }}. Uninstall it and leave " +
                    "its test, and don't count it towards your day any more."
            )
        }

        sweep.standings.forEach { standing ->
            post(
                id = ID_WARNING + slot(standing.groupId),
                title = if (standing.finalWarning) "Last chance in ${standing.groupName}"
                        else "You missed a day in ${standing.groupName}",
                body = if (standing.finalWarning)
                    "That's two days without opening ${standing.names}. Open ${standing.it} " +
                        "today or your app leaves the group."
                else
                    "You didn't open ${standing.names} yesterday. Open ${standing.it} today and " +
                        "nothing comes of it.",
                groupId = standing.groupId
            )
        }
    }

    /**
     * One notification for everything still owed, across every cohort.
     *
     * Not one per group: a tester in three cohorts owes one evening, not three interruptions, and
     * the screen they land on shows the split anyway.
     */
    private suspend fun nudge(uid: String) {
        var outstanding = 0
        var groups = 0

        Repo.myGroups(uid).forEach { group ->
            val day = group.dayIndex() ?: return@forEach
            val others = Repo.appsInGroup(group.id).filter { it.ownerUid != uid }
            if (others.isEmpty()) return@forEach
            val done = Repo.myProofsForDay(uid, group.id, day, group.startDate).size
            val left = others.size - done
            if (left > 0) {
                outstanding += left
                groups += 1
            }
        }

        if (outstanding == 0) return

        val body = if (groups == 1) "Open TestTrack and clear them before the day rolls over."
                   else "Across $groups groups. Open TestTrack and clear them before the day " +
                       "rolls over."

        post(
            id = NOTIFICATION_ID,
            title = "$outstanding app${if (outstanding == 1) "" else "s"} still waiting today",
            body = body
        )
    }

    // Lint wants POST_NOTIFICATIONS checked or the SecurityException handled. The notify below is
    // already inside runCatching, which catches Throwable — lint just cannot see through an inline
    // lambda. Declining notifications is a supported state, not an error path.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, body: String, groupId: String? = null) {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            groupId?.let { putExtra(MainActivity.EXTRA_GROUP_ID, it) }
        }
        val pending = PendingIntent.getActivity(
            applicationContext, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(applicationContext, PushRepo.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching { NotificationManagerCompat.from(applicationContext).notify(id, notification) }
    }

    companion object {
        private const val WORK_NAME = "testtrack.reminder"
        private const val NOTIFICATION_ID = 4201

        // Bases for the enforcement notices, 1000 apart, with the per-item hash masked to a byte
        // by [slot]. Two cohorts must not share a notification id or the second warning silently
        // replaces the first, and an unmasked hashCode is negative about half the time.
        private const val ID_WARNING = 5000
        private const val ID_DEPARTED = 6000
        private const val ID_EVICTED = 7000

        private fun slot(key: String) = key.hashCode() and 0xFF

        /** Raised from the worker and from [AdminEvents], which share this notifier. */
        @SuppressLint("MissingPermission") // runCatching below; see post()
        fun notify(context: Context, id: Int, title: String, body: String, groupId: String?) {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                groupId?.let { putExtra(MainActivity.EXTRA_GROUP_ID, it) }
            }
            val pending = PendingIntent.getActivity(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, PushRepo.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
        }

        /**
         * Schedules the daily pass, first firing in the evening.
         *
         * `KEEP` rather than `UPDATE`, so calling this on every launch — which is what makes it
         * survive a reboot without a BOOT_COMPLETED receiver — does not push the next run further
         * away each time the app is opened.
         */
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(untilEvening(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        /**
         * Milliseconds until 7pm, or straight away if the evening has already passed.
         *
         * A reminder at eight in the morning is about a day that has barely started. Seven leaves
         * time to actually go and do it.
         */
        private fun untilEvening(): Long {
            val now = Calendar.getInstance()
            val target = (now.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 19)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            if (target.before(now)) return 0L
            return target.timeInMillis - now.timeInMillis
        }
    }
}
