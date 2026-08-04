package com.eazyverse.testtrack.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * What we can learn about another tester's app, and how to open it.
 *
 * All of this needs QUERY_ALL_PACKAGES. Without it every lookup below throws
 * NameNotFoundException — indistinguishable from "not installed" — which is why the permission
 * is in the manifest. It is protectionLevel "normal", so the tester is never prompted; that only
 * holds because TestTrack is sideloaded rather than published to Play, where it is restricted.
 */
object InstalledApps {

    data class Info(
        val installed: Boolean,
        /** When it was first installed. Survives updates, resets on uninstall — so it *is* the streak. */
        val firstInstall: Long = 0L,
        val lastUpdate: Long = 0L,
        /** `com.android.vending` means it came from Play rather than a sideload. */
        val installer: String? = null,
        val version: String? = null,
        val label: String? = null
    ) {
        /** Whole days the app has been continuously installed. */
        val streakDays: Int
            get() = if (!installed) 0
            else ((System.currentTimeMillis() - firstInstall) / 86_400_000L).toInt()

        val fromPlay: Boolean get() = installer == "com.android.vending"
    }

    fun info(context: Context, pkg: String): Info {
        val pm = context.packageManager
        return try {
            val p = pm.getPackageInfo(pkg, 0)
            Info(
                installed = true,
                firstInstall = p.firstInstallTime,
                lastUpdate = p.lastUpdateTime,
                installer = runCatching { pm.getInstallSourceInfo(pkg).installingPackageName }.getOrNull(),
                version = p.versionName,
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
