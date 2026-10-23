package com.eazyverse.testtrack.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiAuth
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Feedback news, as a backstop for push.
 *
 * The server pushes each of these the moment it happens (see [fromPush]); this catches anything a
 * push missed. Every half hour, with a connection, it asks the server what the To check and My reviews tabs
 * would show and compares that with what it saw last time. Anything new becomes a notification:
 * a review of your app to check, one about to accept itself, your own review accepted, refused,
 * settled or run out, and cases waiting on a juror. The server sends nothing; the phone notices.
 *
 * The first pass after signing in only takes a reading, so somebody does not open the app to a
 * stack of notifications about things that happened before they had it.
 */
class FeedbackWatch(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!PushRepo.granted(context)) return Result.success()
        if (!runCatching { ApiAuth.ensure(context) }.getOrDefault(false)) return Result.success()
        runCatching { pass(context) }
        return Result.success()
    }

    private suspend fun pass(context: Context) {
        val waiting = Api.waiting()
        val mine = Api.myReviews()
        // Installed for or kept, as this phone sees it, said even while TestTrack stays closed.
        ReviewChecks.report(context, mine)
        val cases = Api.disputes()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val first = !prefs.getBoolean(KEY_STARTED, false)

        val seenWaiting = prefs.getStringSet(KEY_WAITING, emptySet()).orEmpty()
        val warned = prefs.getStringSet(KEY_WARNED, emptySet()).orEmpty()
        val seenCases = prefs.getStringSet(KEY_CASES, emptySet()).orEmpty()
        val states =
            prefs
                .getStringSet(KEY_STATES, emptySet())
                .orEmpty()
                .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 } }
                .associate { (id, state) -> id to state }
        val now = System.currentTimeMillis()
        val nowWarned = warned.toMutableSet()

        if (!first) {
            ensureChannel(context)
            waiting.filter { it.id !in seenWaiting }.forEach { review ->
                post(
                    context,
                    "New review of ${review.appName.ifBlank { "your app" }}",
                    "${review.reviewerName.ifBlank { "A reviewer" }} sent theirs. " +
                        if (review.isLive) {
                            "Check it on Google Play. It accepts itself in a few days if you don't."
                        } else {
                            "It accepts itself in two days if you don't check it."
                        },
                )
            }
            waiting.forEach { review ->
                val until = review.decideBy?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: return@forEach
                val left = until - now
                if (left in 1..SOON && review.id !in warned) {
                    nowWarned += review.id
                    post(
                        context,
                        "${review.appName.ifBlank { "Your app" }}'s review accepts itself soon",
                        "In about ${left / HOUR + 1}h it's accepted and paid. Check it now if anything's wrong.",
                    )
                }
            }
            mine.forEach { review ->
                val before = states[review.id] ?: return@forEach
                if (before == review.state) return@forEach
                val app = review.appName.ifBlank { "the app" }
                when (review.state) {
                    "approved" ->
                        post(
                            context,
                            if (before == "disputed") "The members sided with you" else "Your review of $app was accepted",
                            "+${review.reward} tix are yours.",
                        )
                    "disputed" -> post(context, "Your review of $app was refused", "Three members will decide. You'll hear when they have.")
                    "rejected" ->
                        post(
                            context,
                            "The members sided with the developer",
                            "Your review of $app wasn't paid, and the members' fee came from your tix.",
                        )
                    // Only opened, never installed: nothing was started, so nothing to tell.
                    "expired" ->
                        if (before != "claimed") {
                            post(
                                context,
                                "Your review of $app ran out",
                                "It sat unfinished for ${if (review.isLive) "three days" else "a day"}, so the slot went back.",
                            )
                        }
                }
            }
            val newCases = cases.count { it.id !in seenCases }
            if (newCases > 0) {
                post(
                    context,
                    if (newCases == 1) "A case needs deciding" else "$newCases cases need deciding",
                    "A refused review is waiting on three members. Each vote pays 5 tix.",
                )
            }
        }

        prefs.edit {
            putBoolean(KEY_STARTED, true)
            putStringSet(KEY_WAITING, waiting.map { it.id }.toSet())
            putStringSet(KEY_WARNED, nowWarned.intersect(waiting.map { it.id }.toSet()))
            putStringSet(KEY_CASES, cases.map { it.id }.toSet())
            putStringSet(KEY_STATES, mine.map { "${it.id}=${it.state}" }.toSet())
        }
    }

    companion object {
        private const val WORK_NAME = "feedback_watch"
        private const val PREFS = "feedback_watch"
        private const val KEY_STARTED = "started"
        private const val KEY_WAITING = "waiting"
        private const val KEY_WARNED = "warned"
        private const val KEY_CASES = "cases"
        private const val KEY_STATES = "states"
        private const val CHANNEL = "feedback"
        private const val HOUR = 3_600_000L

        /** How close to accepting itself a review has to be before its owner is warned. */
        private const val SOON = 12 * HOUR

        /** Every half hour with a connection. KEEP, so calling it on every launch does not reset it. */
        fun schedule(context: Context) {
            val work =
                PeriodicWorkRequestBuilder<FeedbackWatch>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
        }

        /**
         * A push about feedback arrived from the server: draw it on this channel, and remember it
         * was said, so the next half-hourly pass does not say it again.
         */
        fun fromPush(
            context: Context,
            title: String,
            body: String,
            event: String,
            ref: String,
        ) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (ref.isNotEmpty()) {
                prefs.edit {
                    when (event) {
                        "new_review" ->
                            putStringSet(KEY_WAITING, prefs.getStringSet(KEY_WAITING, emptySet()).orEmpty() + ref)
                        "approved", "disputed", "rejected", "expired" ->
                            putStringSet(
                                KEY_STATES,
                                prefs
                                    .getStringSet(KEY_STATES, emptySet())
                                    .orEmpty()
                                    .filterNot { it.startsWith("$ref=") }
                                    .toSet() +
                                    "$ref=$event",
                            )
                    }
                }
            }
            ensureChannel(context)
            post(context, title, body)
        }

        /** On sign-out: stop, and forget what was seen, so the next account starts fresh. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Feedback", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reviews to check, your reviews accepted or refused, and cases to decide."
                },
            )
        }

        @SuppressLint("MissingPermission") // checked in doWork through PushRepo.granted
        private fun post(
            context: Context,
            title: String,
            body: String,
        ) {
            val id = (title + body).hashCode()
            val open =
                PendingIntent.getActivity(
                    context,
                    id,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            val note =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification_robot)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setContentIntent(open)
                    .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, note) }
        }
    }
}
