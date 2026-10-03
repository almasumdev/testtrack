package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A visit exactly as a reviewer makes one: open the app, stay past the bar, come back to
 * TestTrack, and only then take the reading.
 *
 * The other usage test reads while the app is still in front. This is the order the review page
 * and crew visits actually use, and the one a phone that records events late would get wrong.
 */
@RunWith(AndroidJUnit4::class)
class UsageRoundTripTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun grantUsageAccess() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
            .close()
        Thread.sleep(1_000)
        assertTrue("usage access was not granted", UsageRepo.hasAccess(context))
    }

    @Test
    fun aVisitPastTheBarIsCountedAfterComingBack() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val candidates =
            context.packageManager
                .queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
                .take(5)

        for (pkg in candidates) {
            val since = System.currentTimeMillis()
            if (!InstalledApps.launch(context, pkg)) continue
            Thread.sleep(33_000)

            // Back to TestTrack, which pauses the app under test, as a reviewer coming back does.
            context.startActivity(
                context.packageManager
                    .getLaunchIntentForPackage(context.packageName)!!
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            Thread.sleep(2_000)

            val ms = runBlocking { UsageRepo.measure(context, pkg, since, needMs = 30_000, source = "test") }
            if (ms >= 30_000) return
            // A launch that never reached the front reads as nothing; try the next app.
            if (ms > 1_000) fail("$pkg was open about 33s but only ${ms / 1000}s was counted")
        }
        fail("None of $candidates came to the front long enough to measure")
    }
}
