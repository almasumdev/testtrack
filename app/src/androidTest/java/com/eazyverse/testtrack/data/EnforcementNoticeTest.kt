package com.eazyverse.testtrack.data

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The notices an enforcement sweep produces, raised on a real device.
 *
 * [ReminderWorker] is the only thing that turns a sweep into notifications, and its real schedule
 * is a delay until the evening — `cmd jobscheduler run` refuses to start periodic work early, so
 * without a way to run it on demand the only way to watch it work is to wait for one. This runs
 * the actual worker, `doWork()` and all, against whatever account is signed in on the device.
 *
 * Needs a signed-in account and notifications granted; skipped rather than failed otherwise, since
 * neither is something a test can arrange for itself.
 */
@RunWith(AndroidJUnit4::class)
class EnforcementNoticeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun posted(): List<Pair<String, String>> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.activeNotifications.map {
            val extras = it.notification.extras
            extras.getCharSequence("android.title").toString() to
                extras.getCharSequence("android.text").toString()
        }
    }

    /**
     * FirebaseAuth restores the signed-in user from disk asynchronously, and in a process the test
     * runner started by itself it stays null well past any reasonable poll. Opening the activity
     * first puts the process through the same start-up a real launch does, after which the session
     * is there in a second or two.
     *
     * Worth the trouble because the alternative is what this test did at first: read `uid` once,
     * find null, and skip — passing green while testing nothing at all.
     */
    private fun waitForAuth(timeoutMs: Long = 30_000): String? {
        AuthRepo.uid?.let { return it }

        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            AuthRepo.uid?.let { return it }
            Thread.sleep(500)
        }
        return null
    }

    @Test
    fun theWorkerRaisesWhateverTheSweepFound() = runBlocking {
        val uid = waitForAuth()
        assumeTrue("no account signed in on this device", uid != null)
        assumeTrue("notifications not granted", PushRepo.granted(context))
        println("SIGNED IN as $uid")

        context.getSystemService(NotificationManager::class.java).cancelAll()

        // Only the worker sweeps. Calling Enforcement.sweep first would consume the very change
        // being tested — a departure is the difference against what this device last saw, so the
        // first sweep to notice it is also the one that records it as no longer news.
        val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
        val result = worker.doWork()
        println("WORKER result=$result")

        // Give the notification manager a moment to hand them back.
        Thread.sleep(1500)
        posted().forEach { (title, text) -> println("NOTIFICATION | $title | $text") }
    }
}
