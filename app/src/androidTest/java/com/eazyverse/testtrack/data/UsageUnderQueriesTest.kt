package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
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
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
            .close()
        Thread.sleep(1_000)
        assertTrue("usage access was not granted", UsageRepo.hasAccess(context))
    }

    /** Every app the launcher query reaches — which is every app a cohort can contain. */
    private fun launchableApps(): List<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return context.packageManager.queryIntentActivities(intent, 0)
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
        assertTrue("$pkg visible to queryIntentActivities but not to getPackageInfo",
            InstalledApps.info(context, pkg).installed)
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
        val pkg = launchableApps().first()

        assertTrue("could not launch $pkg", InstalledApps.launch(context, pkg))
        Thread.sleep(15_000)

        val ms = UsageRepo.foregroundMsToday(context, pkg)
        assertTrue(
            "UsageStatsManager reported ${ms}ms for $pkg after a 15s visit — " +
                "package visibility is filtering usage stats, and the proof mechanism is gone",
            ms >= 5_000
        )
    }
}
