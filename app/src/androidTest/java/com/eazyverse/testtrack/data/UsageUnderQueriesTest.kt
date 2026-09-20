package com.eazyverse.testtrack.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one question the move off `QUERY_ALL_PACKAGES` could not be answered from a desk.
 *
 * Package visibility filters what `PackageManager` will admit to. Whether it also filters
 * `UsageStatsManager` is undocumented, and the answer decides whether TestTrack still has a proof
 * mechanism at all — foreground time is the measurement, and everything else is decoration.
 *
 * Runs on a device because there is no other place the answer exists.
 */
@RunWith(AndroidJUnit4::class)
class UsageUnderQueriesTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * `PACKAGE_USAGE_STATS` is an appop, so there is no runtime grant to request — but
     * instrumentation carries shell privilege, which is enough to set it without a trip to
     * Settings that no test could make.
     */
    @Before
    fun grantUsageAccess() {
        InstrumentationRegistry
            .getInstrumentation()
            .uiAutomation
            .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
            .close()
        Thread.sleep(1_000)
        assertTrue("usage access was not granted", UsageRepo.hasAccess(context))

        // Granted is not the same as working. On some handsets the appop flips to allow and
        // UsageStatsManager still answers with nothing for a package installed seconds ago, and
        // the grant only takes for real on the next install. That is worth skipping on rather
        // than failing on: this test exists to tell "package visibility is filtering usage
        // stats" apart from "there is no usage data to filter", and it cannot do that with no
        // access. A red here would say the proof mechanism is gone when it is only unmeasurable.
        val manager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val day = 24 * 60 * 60 * 1000L
        val now = System.currentTimeMillis()
        val anything = manager.queryEvents(now - day, now).hasNextEvent()
        assumeTrue(
            "Usage access is set to allow but this device returns no events at all. Grant " +
                "Usage access to ${context.packageName} by hand and run it again.",
            anything,
        )
    }

    /** Every app the launcher query reaches — which is every app a cohort can contain. */
    private fun launchableApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager
            .queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
    }

    /**
     * The `<queries>` filter reaches other apps at all.
     *
     * Without it this list is empty and every row in the app reads "Not installed", so it is worth
     * failing on separately — a zero here would make the usage result below meaningless rather
     * than wrong.
     */
    @Test
    fun launcherQuerySeesOtherApps() {
        val apps = launchableApps()
        assertTrue("launcher <queries> returned nothing", apps.size > 5)

        val pkg = apps.first()
        assertTrue(
            "$pkg visible to queryIntentActivities but not to getPackageInfo",
            InstalledApps.info(context, pkg).installed,
        )
    }

    /**
     * The measurement itself: open another app, leave it there, and see whether TestTrack can
     * still say how long it was on screen.
     *
     * Reads while the app under test is still foreground, exactly as a round does — the visit is
     * measured on the way out, not after.
     */
    @Test
    fun foregroundTimeIsReadableForAnotherApp() {
        // Tried in order rather than taking whichever package happens to sort first. That is what
        // this test used to do, and it made the result depend on the handset: the emulator's
        // first launchable app is a camera that does not always come up, and the phone's is
        // Settings. A launch that silently did not happen looks exactly like a measurement that
        // silently did not work, which is the one thing this test exists to tell apart.
        val tried = mutableListOf<String>()
        for (pkg in launchableApps().take(6)) {
            tried += pkg
            val since = System.currentTimeMillis()
            if (!InstalledApps.launch(context, pkg)) continue
            Thread.sleep(15_000)

            // Did it actually get there? Asked of UsageStatsManager itself, since that is the
            // thing under test and a foreground check from anywhere else would prove nothing
            // about it.
            val ms = UsageRepo.foregroundMsSince(context, pkg, since)
            if (ms >= 5_000) return
        }

        fail(
            "No launchable app reported foreground time after a 15s visit. Tried: " +
                tried.joinToString() +
                ". Either package visibility is filtering usage stats and the proof mechanism " +
                "is gone, or nothing on this device would come to the foreground.",
        )
    }
}
