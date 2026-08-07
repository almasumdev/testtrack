package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build

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
     * Cached because a list of fourteen rows asks for this on every recomposition, and neither
     * answer changes while the app is running — an install lands as a fresh process anyway.
     */
    private val infoCache = mutableMapOf<String, Info>()
    private val iconCache = mutableMapOf<String, Drawable?>()

    /** The launcher icon, which is what makes a row recognisable at a glance. */
    fun icon(context: Context, pkg: String): Drawable? = iconCache.getOrPut(pkg) {
        runCatching { context.packageManager.getApplicationIcon(pkg) }.getOrNull()
    }

    fun cachedInfo(context: Context, pkg: String): Info =
        infoCache.getOrPut(pkg) { info(context, pkg) }

    /** Forget everything — after an install the streak and the icon both need re-reading. */
    fun forget() {
        infoCache.clear()
        iconCache.clear()
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
