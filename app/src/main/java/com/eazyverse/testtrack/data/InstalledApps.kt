package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * What we can learn about another tester's app, and how to open it.
 *
 * Reaches packages with a launcher activity and no others, which is what the `<queries>` block in
 * the manifest asks for. Every app in a cohort has one, so this is no narrower in practice than
 * QUERY_ALL_PACKAGES was, and unlike that permission it does not cost us Play.
 *
 * So NameNotFoundException now means "not installed, or has no launcher entry" rather than plainly
 * "not installed". The ambiguity is theoretical: an app with no launcher entry cannot be opened by
 * a round either, so it never reaches a cohort to be asked about.
 */
object InstalledApps {

    data class Info(
        val installed: Boolean,
        /** When it was first installed. Survives updates, resets on uninstall — so it *is* the streak. */
        val firstInstall: Long = 0L,
        /**
         * `com.android.vending` means it came from Play rather than a sideload.
         *
         * Nothing reads this yet. A closed-test build can only be installed from Play by someone
         * already opted in, so the source is the one thing on the device that says the tester
         * opted in at all — and opting in is the only thing Play counts. A sideloaded copy passed
         * around the group would otherwise pass every check here and still be worth nothing.
         */
        val installer: String? = null,
        val label: String? = null
    ) {
        /** Whole days the app has been continuously installed. */
        val streakDays: Int
            get() = if (!installed) 0
            else ((System.currentTimeMillis() - firstInstall) / 86_400_000L).toInt()

        /**
         * The streak, said in a way that isn't nonsense.
         *
         * A preinstalled app reports an epoch `firstInstallTime`, which comes out as "6426 days" —
         * true, useless, and it makes the row look broken. Anything past a year is just "installed";
         * the number only matters while it is comparable to the fourteen-day run.
         */
        val streakLabel: String
            get() = when {
                !installed -> "Not installed"
                streakDays >= 365 -> "Installed"
                streakDays <= 0 -> "Installed today"
                streakDays == 1 -> "1 day installed"
                else -> "$streakDays days installed"
            }

        val fromPlay: Boolean get() = installer == "com.android.vending"
    }

    /**
     * Cached because a list of fourteen rows asks for this on every recomposition.
     *
     * The cache used to be justified by "an install lands as a fresh process anyway", which is
     * true of installing *this* app and not of installing any other. TestTrack sits in the
     * background the whole time the tester is in Play, so it comes back to the same process
     * holding the same answer it cached before they left: not installed. Hence [refresh].
     */
    private val infoCache = mutableMapOf<String, Info>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    /**
     * Bumped whenever a cached answer actually changes.
     *
     * Compose state rather than a plain counter, so that reading it is a subscription. Clearing a
     * map cannot make anything recompose on its own: a screen that never re-reads is exactly as
     * stale as a cache that was never cleared.
     */
    private var generation by mutableIntStateOf(0)

    /** Read this as a `remember` key to be re-run when the answers move. */
    val revision: Int get() = generation

    /** The launcher icon, which is what makes a row recognisable at a glance. */
    fun icon(context: Context, pkg: String): Drawable? = iconCache.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    fun cachedInfo(context: Context, pkg: String): Info {
        // Touched so that a composable reaching this through a helper subscribes too, rather than
        // only the call sites that pass `revision` as a key.
        @Suppress("UNUSED_EXPRESSION") generation
        return infoCache.getOrPut(pkg) { info(context, pkg) }
    }

    /**
     * Re-reads everything already cached, and says whether any of it moved.
     *
     * Called when TestTrack returns to the front, because that is the far side of the one event
     * this cache cannot observe: the tester tapped Install, went to Play, and came back. There is
     * no broadcast for "an app you asked about earlier now exists" that does not cost a receiver
     * and a permission.
     *
     * The generation moves only when an answer genuinely changed, so the ordinary resume costs one
     * PackageManager call per row and recomposes nothing. An uninstall is caught by the same pass,
     * in the same way.
     */
    fun refresh(context: Context) {
        var moved = false
        for (pkg in infoCache.keys.toList()) {
            val fresh = info(context, pkg)
            if (fresh != infoCache[pkg]) {
                infoCache[pkg] = fresh
                // The icon could not be read while the app was absent, so a null was cached for
                // it. Dropping that is what lets the row stop being a grey placeholder.
                iconCache.remove(pkg)
                moved = true
            }
        }
        if (moved) generation++
    }

    fun info(context: Context, pkg: String): Info {
        val pm = context.packageManager
        return try {
            val p = pm.getPackageInfo(pkg, 0)
            Info(
                installed = true,
                firstInstall = p.firstInstallTime,
                // getInstallSourceInfo is API 30. runCatching would swallow the NoSuchMethodError
                // on anything older and report every install as sourceless, which reads the same
                // as a sideload — so ask the older question there instead of guessing.
                installer = runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                        pm.getInstallSourceInfo(pkg).installingPackageName
                    else
                        @Suppress("DEPRECATION") pm.getInstallerPackageName(pkg)
                }.getOrNull(),
                label = runCatching { pm.getApplicationLabel(p.applicationInfo!!).toString() }.getOrNull()
            )
        } catch (_: PackageManager.NameNotFoundException) {
            Info(installed = false)
        }
    }

    /** Opens the app's launcher activity. False if it isn't installed or has no launchable entry. */
    fun launch(context: Context, pkg: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return true
    }
}
