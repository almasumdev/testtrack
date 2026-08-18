package com.eazyverse.testtrack.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.VisibleForTesting
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await

/** How hard to push an update. Derived, never stored. */
enum class UpdateTier { NONE, NUDGE, BLOCKED }

/**
 * In-app update gate, ported from Sound Scheduler's AppUpdateService. Two independent inputs:
 *
 *  - **Google Play** ([AppUpdateManager]) answers "can this user install an update right now?" —
 *    resolved on device against the track this install actually came from, so it accounts for staged
 *    rollouts and device compatibility. It is the only source of truth for availability, checked first
 *    and unconditionally.
 *  - **Firestore `config/app`** answers "how hard should we push?": `latestBuild`, `latestVersionName`
 *    (its major drives forcing), `minSupportedBuild` (manual kill-switch, 0 blocks nobody),
 *    `nudgeAfterDays`.
 *
 * Everything fails open: no Play, no network, a sideloaded build or a missing doc all resolve to
 * [UpdateTier.NONE]. A gate that locks people out on a hiccup is far worse than one that misses an
 * update. Both nudge and blocked use Play's flexible (background) flow; only the UI differs.
 *
 * The one thing TestTrack changes: the per-build dismissal is kept in [Session] rather than a room
 * repo, because that is where this app keeps everything that survives a restart.
 */
object AppUpdateService {
    private const val TAG = "AppUpdateService"

    private var manager: AppUpdateManager? = null

    private val _tier = MutableStateFlow(UpdateTier.NONE)
    val tier: StateFlow<UpdateTier> = _tier

    /** True once a flexible update finished downloading and only needs a restart to apply. */
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded

    private var currentBuild = 0
    private var currentMajor = 0

    // Play's answer.
    private var lastInfo: AppUpdateInfo? = null
    private var playUpdateAvailable = false
    private var flexibleAllowed = false
    private var availableBuild = 0
    private var stalenessDays = 0

    // Firestore's answer.
    private var latestBuild = 0
    private var latestVersionName = ""
    private var minSupportedBuild = 0
    private var nudgeAfterDays = 0

    /** The build Play would install now, for the per-build nudge throttle. */
    val availableVersionCode: Int get() = availableBuild

    /** Whether Play will download in the background rather than sending the user to the store. */
    val canDownloadInApp: Boolean get() = playUpdateAvailable && flexibleAllowed

    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> _isDownloaded.value = true
            InstallStatus.INSTALLING, InstallStatus.INSTALLED,
            InstallStatus.FAILED, InstallStatus.CANCELED -> _isDownloaded.value = false
            else -> {}
        }
    }

    /** Once, at process start. Reads the running build/version; never throws. */
    fun init(context: Context) {
        if (manager == null) manager = AppUpdateManagerFactory.create(context.applicationContext)
        try {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            currentBuild = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode.toInt() else pkg.versionCode
            currentMajor = majorOf(pkg.versionName ?: "")
        } catch (e: Exception) {
            Log.d(TAG, "Could not read package info: ${e.message}")
        }
    }

    fun registerInstallListener() {
        manager?.registerListener(installListener)
    }

    fun unregisterInstallListener() {
        manager?.unregisterListener(installListener)
    }

    /** Re-ask Play + config. Worth calling on foreground. Never throws. */
    suspend fun refresh() {
        val mgr = manager ?: return
        try {
            val info = mgr.appUpdateInfo.await()
            lastInfo = info
            playUpdateAvailable = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
            flexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            availableBuild = info.availableVersionCode()
            // Null when Play has no staleness info yet -> treat as brand new, so nudgeAfterDays 0 still
            // offers immediately.
            stalenessDays = info.clientVersionStalenessDays() ?: 0
            if (info.installStatus() == InstallStatus.DOWNLOADED) _isDownloaded.value = true
        } catch (e: Exception) {
            // Thrown on sideloaded / non-Play installs. Fail open: no update, no block.
            Log.d(TAG, "Play update check unavailable: ${e.message}")
            lastInfo = null
            playUpdateAvailable = false
            flexibleAllowed = false
            availableBuild = 0
            stalenessDays = 0
        }
        try {
            val snap = FirebaseFirestore.getInstance().collection("config").document("app").get().await()
            latestBuild = snap.getLong("latestBuild")?.toInt() ?: 0
            latestVersionName = snap.getString("latestVersionName") ?: ""
            minSupportedBuild = snap.getLong("minSupportedBuild")?.toInt() ?: 0
            nudgeAfterDays = snap.getLong("nudgeAfterDays")?.toInt() ?: 0
        } catch (e: Exception) {
            Log.d(TAG, "Config read failed: ${e.message}")
            minSupportedBuild = 0
        }
        _tier.value = resolveTier(
            playUpdateAvailable = playUpdateAvailable,
            availableBuild = availableBuild,
            currentBuild = currentBuild,
            currentMajor = currentMajor,
            latestBuild = latestBuild,
            latestVersionName = latestVersionName,
            minSupportedBuild = minSupportedBuild,
            stalenessDays = stalenessDays,
            nudgeAfterDays = nudgeAfterDays,
        )
    }

    /** True when there's a nudge for a build we haven't dismissed yet (throttled per available build). */
    fun hasPendingNudge(): Boolean =
        _tier.value == UpdateTier.NUDGE && availableBuild > Session.updatePromptedBuild

    /** Records that the current available build's nudge has been dismissed, so it isn't shown again. */
    fun markNudgeShown() {
        if (availableBuild > 0) Session.updateUpdatePromptedBuild(availableBuild)
    }

    /** Start Play's flexible (background) download flow. No-op if there's nothing to install. */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val info = lastInfo ?: return
        val mgr = manager ?: return
        try {
            mgr.startUpdateFlowForResult(
                info, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        } catch (e: Exception) {
            Log.d(TAG, "Flexible update failed to start: ${e.message}")
        }
    }

    /** Applies a finished download. Play restarts the app to swap the binary. */
    fun completeUpdate() {
        manager?.completeUpdate()
    }

    /**
     * The whole decision, as a pure function so it can be tested directly. Kept free of Play/Firestore
     * dependencies on purpose — this is the path that can lock a user out.
     */
    @VisibleForTesting
    fun resolveTier(
        playUpdateAvailable: Boolean,
        availableBuild: Int,
        currentBuild: Int,
        currentMajor: Int,
        latestBuild: Int,
        latestVersionName: String,
        minSupportedBuild: Int,
        stalenessDays: Int,
        nudgeAfterDays: Int,
    ): UpdateTier {
        // Play knows what this user's track can actually serve; nothing below may override it.
        if (!playUpdateAvailable) return UpdateTier.NONE
        // No usable local build number, so nothing safe to compare.
        if (currentBuild <= 0) return UpdateTier.NONE
        // Manual override. 0 is the resting state and blocks nobody.
        if (minSupportedBuild > 0 && currentBuild < minSupportedBuild) return UpdateTier.BLOCKED
        // Automatic rule: a new major means breaking changes, so block — but only when what Play will
        // actually hand this user IS the release the config describes, so a 2.0.0 on internal can't
        // block a production user whose best available build is still 1.x (they'd be stuck).
        val playCanReachLatest = availableBuild > 0 && latestBuild > 0 && availableBuild >= latestBuild
        if (playCanReachLatest && currentMajor > 0 && majorOf(latestVersionName) > currentMajor) {
            return UpdateTier.BLOCKED
        }
        return if (stalenessDays >= nudgeAfterDays) UpdateTier.NUDGE else UpdateTier.NONE
    }

    /** Leading segment of a version name as an int: "1.3.4" -> 1. Parsed numerically (never lexically,
     *  or 10 would sort before 9). Returns 0 when unreadable, which callers treat as "don't gate". */
    private fun majorOf(version: String): Int {
        val head = version.trim().split(".").firstOrNull() ?: return 0
        val digits = Regex("^\\d+").find(head)?.value ?: return 0
        return digits.toIntOrNull() ?: 0
    }
}
