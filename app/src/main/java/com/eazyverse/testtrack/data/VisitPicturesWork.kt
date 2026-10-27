package com.eazyverse.testtrack.data

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.eazyverse.testtrack.net.ApiAuth
import java.util.concurrent.TimeUnit

/**
 * Tonight's pictures, sent even with TestTrack closed.
 *
 * A picture that could not go when it was taken (no connection on the train home, Drive slow to
 * answer) would otherwise wait until the Crew page was next opened, and a night counts only once
 * its picture is in. This runs as soon as there is a connection and tries again, further apart
 * each time, until every picture is on its visit. Drive's grant is asked for quietly; when Google
 * wants the member to agree again, it stops and the Crew page asks them.
 */
class VisitPicturesWork(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        VisitPictures.load(context)
        if (VisitPictures.waiting.value.isEmpty()) return Result.success()
        if (!runCatching { ApiAuth.ensure(context) }.getOrDefault(false)) return Result.retry()
        val token = AuthRepo.driveTokenOrNull(context) ?: return Result.success().also { VisitPictures.needsDrive() }
        VisitPictures.token = token
        return if (VisitPictures.send(context, token) == null) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "visit_pictures"

        /** Send what is waiting as soon as there is a connection; a send already queued stands. */
        fun enqueue(context: Context) {
            val work =
                OneTimeWorkRequestBuilder<VisitPicturesWork>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, work)
        }
    }
}
