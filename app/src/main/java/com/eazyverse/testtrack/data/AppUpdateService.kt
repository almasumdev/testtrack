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
 * In-app update gate, ported from Sound Scheduler's AppUpdateService. Follows
 * docs/in-app-update-system.md, the same flow Bluefy uses:
 *
 *  - **Google Play** ([AppUpdateManager]) decides whether this device can install an update. It is
 *    resolved on device against the track this install actually came from, so it accounts for staged
 *    rollouts, track membership and device compatibility. Always asked first.
 *  - **Firestore `config/app`** decides how strongly to ask: `latestBuild`, `latestVersionName` (its
 *    major drives forcing), `minSupportedBuild` (manual kill-switch, 0 blocks nobody),
 *    `nudgeAfterDays`. Read only after Play says an update is available, so it can never create update
 *    UI on its own.
 *
 * When both answer, their inputs are cached. When Play or Firestore cannot answer, the last decision
 * proven online on this device is replayed from those inputs, until the installed build reaches the
 * cached available build. When Play says there is no update, the cache is cleared.
 *
 * Otherwise everything fails open: no cache, a sideloaded build or a missing doc resolve to
 * [UpdateTier.NONE]. A gate that locks people out on a hiccup is far worse than one that misses an
 * update. Both nudge and blocked use Play's flexible (background) flow; only the UI differs. The
 * nudge never interrupts: it is the icon in home's app bar, and nothing starts until that is tapped.
 *
 * The one thing TestTrack changes: the cached policy is kept in [Session] rather than a room repo,
 * because that is where this app keeps everything that survives a restart. Session is loaded by the
 * activity, after [init] has run, so the cached decision is applied from there through
 * [applyCachedDecision] rather than from [init].
 */
object AppUpdateService {
    private const val TAG = "AppUpdateService"

    private var manager: AppUpdateManager? = null

    private val _tier = MutableStateFlow(UpdateTier.NONE)
    val tier: StateFlow<UpdateTier> = _tier

    /** True once a flexible update finished downloading and only needs a restart to apply. */
    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded: StateFlow<Boolean> = _isDownloaded

    /**
     * True once somebody taps the app bar update icon in this process. Only then does a finished
     * download earn the Restart banner; one left on disk from an earlier session waits behind the
     * same icon instead of greeting them at launch.
     */
    private val _userStartedUpdate = MutableStateFlow(false)
    val userStartedUpdate: StateFlow<Boolean> = _userStartedUpdate

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

    /** Whether Play will download in the background rather than sending the user to the store. */
    val canDownloadInApp: Boolean get() = playUpdateAvailable && flexibleAllowed

    private val installListener =
        InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> _isDownloaded.value = true
                InstallStatus.INSTALLING, InstallStatus.INSTALLED,
                InstallStatus.FAILED, InstallStatus.CANCELED,
                -> _isDownloaded.value = false
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

    /**
     * Until Play answers, show whatever was last proven online, so a forced update survives a cold
     * start with no network. Call once [Session] is loaded and before the first [refresh].
     */
    fun applyCachedDecision() {
        applyOfflinePolicy()
    }

    fun registerInstallListener() {
        manager?.registerListener(installListener)
    }

    fun unregisterInstallListener() {
        manager?.unregisterListener(installListener)
    }

    /** Re-ask Play, then config only if Play has an update. Worth calling on foreground. Never throws. */
    suspend fun refresh() {
        val mgr = manager ?: return

        // 1. Play first.
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
            // Thrown on sideloaded / non-Play installs, or with no connection to Play.
            Log.d(TAG, "Play update check unavailable: ${e.message}")
            applyOfflinePolicy()
            return
        }

        // 2. Play says nothing to install: no policy read, no update UI, and forget any cached decision.
        if (!playUpdateAvailable) {
            resetConfig()
            Session.clearCachedUpdatePolicy()
            _tier.value = UpdateTier.NONE
            return
        }

        // 3. Play has an update: read policy once and combine.
        try {
            val snap =
                FirebaseFirestore
                    .getInstance()
                    .collection("config")
                    .document("app")
                    .get()
                    .await()
            latestBuild = snap.getLong("latestBuild")?.toInt() ?: 0
            latestVersionName = snap.getString("latestVersionName") ?: ""
            minSupportedBuild = snap.getLong("minSupportedBuild")?.toInt() ?: 0
            nudgeAfterDays = snap.getLong("nudgeAfterDays")?.toInt() ?: 0
        } catch (e: Exception) {
            Log.d(TAG, "Config read failed: ${e.message}")
            resetConfig()
            applyOfflinePolicy()
            return
        }
        cachePolicy()
        _tier.value =
            resolveTier(
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

    private fun resetConfig() {
        latestBuild = 0
        latestVersionName = ""
        minSupportedBuild = 0
        nudgeAfterDays = 0
    }

    /** Stores the inputs of a decision Play and Firestore just agreed on. Never the verdict. */
    private fun cachePolicy() {
        if (!playUpdateAvailable || availableBuild <= currentBuild) {
            Session.clearCachedUpdatePolicy()
            return
        }
        Session.updateCachedUpdatePolicy(
            availableBuild = availableBuild,
            stalenessDays = stalenessDays,
            latestBuild = latestBuild,
            latestVersionName = latestVersionName,
            minSupportedBuild = minSupportedBuild,
            nudgeAfterDays = nudgeAfterDays,
        )
    }

    /**
     * Replays the last decision proven online, or NONE. The cached Play answer is restored with
     * flexibleAllowed false and no [lastInfo]: with Play unreachable there is nothing to download
     * in-app, so the UI falls back to opening the store listing.
     */
    private fun applyOfflinePolicy() {
        val cachedAvailable = Session.cachedUpdateAvailableBuild
        lastInfo = null
        flexibleAllowed = false
        if (cachedAvailable <= 0 || currentBuild >= cachedAvailable) {
            // Nothing proven, or the update it was about is already installed: forget it.
            Session.clearCachedUpdatePolicy()
            playUpdateAvailable = false
            availableBuild = 0
            stalenessDays = 0
            resetConfig()
            _tier.value = UpdateTier.NONE
            return
        }
        playUpdateAvailable = true
        availableBuild = cachedAvailable
        stalenessDays = Session.cachedUpdateStalenessDays
        latestBuild = Session.cachedUpdateLatestBuild
        latestVersionName = Session.cachedUpdateLatestVersionName
        minSupportedBuild = Session.cachedUpdateMinSupportedBuild
        nudgeAfterDays = Session.cachedUpdateNudgeAfterDays
        _tier.value =
            resolveCachedTier(
                currentBuild = currentBuild,
                currentMajor = currentMajor,
                cachedAvailableBuild = cachedAvailable,
                cachedStalenessDays = stalenessDays,
                cachedLatestBuild = latestBuild,
                cachedLatestVersionName = latestVersionName,
                cachedMinSupportedBuild = minSupportedBuild,
                cachedNudgeAfterDays = nudgeAfterDays,
            )
    }

    /**
     * The app bar icon's tap, the only way an optional update is ever offered. Returns false when the
     * caller should open the store listing instead: Play can't download in-app (an offline replay, or
     * flexible not allowed) or the flow would not start.
     */
    fun startUserUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        _userStartedUpdate.value = true
        // Already on disk: the Restart banner the tap just revealed is the whole answer.
        if (_isDownloaded.value) return true
        return startFlexibleUpdate(launcher)
    }

    /**
     * Start Play's flexible (background) download flow. Returns false when Play has nothing flexible
     * to offer (including an offline replay), so the caller can open the store listing instead.
     */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        if (!canDownloadInApp) return false
        val info = lastInfo ?: return false
        val mgr = manager ?: return false
        return try {
            mgr.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
            )
            true
        } catch (e: Exception) {
            Log.d(TAG, "Flexible update failed to start: ${e.message}")
            false
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

    /**
     * The offline replay, as a pure function. The installed build is checked before anything else, so a
     * cache left behind after the update was installed can never keep prompting or blocking.
     */
    @VisibleForTesting
    fun resolveCachedTier(
        currentBuild: Int,
        currentMajor: Int,
        cachedAvailableBuild: Int,
        cachedStalenessDays: Int,
        cachedLatestBuild: Int,
        cachedLatestVersionName: String,
        cachedMinSupportedBuild: Int,
        cachedNudgeAfterDays: Int,
    ): UpdateTier {
        if (cachedAvailableBuild <= 0) return UpdateTier.NONE
        if (currentBuild <= 0) return UpdateTier.NONE
        if (currentBuild >= cachedAvailableBuild) return UpdateTier.NONE
        return resolveTier(
            playUpdateAvailable = true,
            availableBuild = cachedAvailableBuild,
            currentBuild = currentBuild,
            currentMajor = currentMajor,
            latestBuild = cachedLatestBuild,
            latestVersionName = cachedLatestVersionName,
            minSupportedBuild = cachedMinSupportedBuild,
            stalenessDays = cachedStalenessDays,
            nudgeAfterDays = cachedNudgeAfterDays,
        )
    }

    /** Leading segment of a version name as an int: "1.3.4" -> 1. Parsed numerically (never lexically,
     *  or 10 would sort before 9). Returns 0 when unreadable, which callers treat as "don't gate". */
    private fun majorOf(version: String): Int {
        val head = version.trim().split(".").firstOrNull() ?: return 0
        val digits = Regex("^\\d+").find(head)?.value ?: return 0
        return digits.toIntOrNull() ?: 0
    }
}
