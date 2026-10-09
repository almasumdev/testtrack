package com.eazyverse.testtrack.net

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException

/** Revocation survives offline sign-out and process death. Tokens stay in private preferences. */
class SessionLogout(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ApiSession.load(applicationContext)
        return try {
            for (token in ApiSession.pendingRevocations()) {
                Api.revoke(token)
                ApiSession.revoked(token)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            val request =
                OneTimeWorkRequestBuilder<SessionLogout>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "session-revocations",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
