package com.eazyverse.testtrack.data

import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** How hard to push an update. Worked out on the spot, never stored. */
enum class UpdateTier { NONE, NUDGE, BLOCKED }

/**
 * Getting testers onto the build in front of them.
 *
 * This exists because of a real report: somebody's grid legend read "under 30s" two days after the
 * bar became ten, because the rule ships inside the APK and theirs was old. Nothing was wrong with
 * their data and nothing could be done about their screen. A cohort is thirteen people holding
 * each other to a fortnight, and thirteen different builds means thirteen different answers to the
 * same question.
 *
 * Two independent inputs, and the order between them matters:
 *
 *  - **Play** answers "can this install update right now?" It resolves against the track the copy
 *    actually came from, so it knows about staged rollouts and device compatibility in a way
 *    nothing here could. It is the only source of availability, asked first, and nothing below may
 *    overrule a no.
 *  - **Firestore `config/app`** answers "how hard should we push?" — `latestBuild`,
 *    `latestVersionName` whose major drives forcing, `minSupportedBuild` as a manual switch, and
 *    `nudgeAfterDays`.
 *
 * Everything fails open. No Play, no network, a sideloaded build, a missing document: all of them
 * come out [UpdateTier.NONE]. A gate that locks somebody out of their own cohort on a hiccup is
 * far worse than one that misses an update, and this is the only code in the app that can shut the
 * door on a paying day.
 *
 * Ported from the same gate in Sound Scheduler, with its state model swapped for the Compose one
 * the rest of this app uses.
 */
object UpdateGate {

    private var manager: AppUpdateManager? = null

    /** Read by the UI. Compose state rather than a flow, which is how the rest of this app talks. */
    var tier by mutableStateOf(UpdateTier.NONE)
        private set

    /** A flexible update has finished downloading and needs only a restart. */
    var downloaded by mutableStateOf(false)
        private set

    private var runningBuild = 0
    private var runningMajor = 0

    // What Play said.
    private var info: AppUpdateInfo? = null
    private var available = false
    private var flexibleAllowed = false
    private var availableBuild = 0
    private var stalenessDays = 0

    // What the config document said.
    private var latestBuild = 0
    private var latestVersionName = ""
    private var minSupportedBuild = 0
    private var nudgeAfterDays = 0

    /** Whether Play will fetch it in the background rather than sending them to the store. */
    val canDownloadInApp: Boolean get() = available && flexibleAllowed

    private val installs = InstallStateUpdatedListener { state ->
        downloaded = when (state.installStatus()) {
            InstallStatus.DOWNLOADED -> true
            else -> false
        }
    }

    /** Once, at startup. Reads the running build and never throws. */
    fun init(context: Context) {
        if (manager == null) manager = AppUpdateManagerFactory.create(context.applicationContext)
        runCatching {
            val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            runningBuild =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pkg.longVersionCode.toInt()
                else pkg.versionCode
            runningMajor = majorOf(pkg.versionName.orEmpty())
        }
    }

    fun listen() = manager?.registerListener(installs)

    fun stopListening() = manager?.unregisterListener(installs)

    /**
     * Ask Play and the config document again. Worth doing whenever the app comes forward, and it
     * cannot throw: both halves swallow their own failure and leave the field at its resting value.
     */
    suspend fun refresh() {
        val mgr = manager ?: return

        runCatching { await(mgr.appUpdateInfo) }
            .onSuccess {
                info = it
                available = it.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                flexibleAllowed = it.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
                availableBuild = it.availableVersionCode()
                // Null while Play has no staleness reading yet, which reads as brand new — so a
                // nudgeAfterDays of zero still offers it immediately.
                stalenessDays = it.clientVersionStalenessDays() ?: 0
                if (it.installStatus() == InstallStatus.DOWNLOADED) downloaded = true
            }
            .onFailure {
                // Thrown on a sideloaded copy, which is every debug build on your own phone.
                info = null
                available = false
                flexibleAllowed = false
                availableBuild = 0
                stalenessDays = 0
            }

        runCatching { await(FirebaseFirestore.getInstance().collection("config").document("app").get()) }
            .onSuccess {
                latestBuild = it.getLong("latestBuild")?.toInt() ?: 0
                latestVersionName = it.getString("latestVersionName").orEmpty()
                minSupportedBuild = it.getLong("minSupportedBuild")?.toInt() ?: 0
                nudgeAfterDays = it.getLong("nudgeAfterDays")?.toInt() ?: 0
            }
            .onFailure {
                // Blocking nobody is the only safe reading of "we could not find out".
                minSupportedBuild = 0
            }

        tier = resolve(
            available = available,
            availableBuild = availableBuild,
            runningBuild = runningBuild,
            runningMajor = runningMajor,
            latestBuild = latestBuild,
            latestVersionName = latestVersionName,
            minSupportedBuild = minSupportedBuild,
            stalenessDays = stalenessDays,
            nudgeAfterDays = nudgeAfterDays
        )
    }

    /** A nudge for a build they have not already waved away. */
    val nudging: Boolean
        get() = tier == UpdateTier.NUDGE && availableBuild > Session.nudgedBuild

    /** They waved this one away. Asked again only when Play has something newer than it. */
    fun nudged() {
        if (availableBuild > 0) Session.updateNudgedBuild(availableBuild)
    }

    /** Play's background download. Does nothing if there is nothing to fetch. */
    fun start(launcher: ActivityResultLauncher<IntentSenderRequest>) {
        val ready = info ?: return
        val mgr = manager ?: return
        runCatching {
            mgr.startUpdateFlowForResult(
                ready, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()
            )
        }
    }

    /** Swaps the binary. Play restarts the app to do it. */
    fun install() {
        manager?.completeUpdate()
    }

    /**
     * The whole decision, with nothing in it to mock.
     *
     * Kept as a pure function deliberately: this is the one path in the app that can lock somebody
     * out of a run they are being held to, so it has to be readable end to end and testable
     * without a device or a network.
     */
    fun resolve(
        available: Boolean,
        availableBuild: Int,
        runningBuild: Int,
        runningMajor: Int,
        latestBuild: Int,
        latestVersionName: String,
        minSupportedBuild: Int,
        stalenessDays: Int,
        nudgeAfterDays: Int
    ): UpdateTier {
        // Play knows what this install can actually be served. Nothing below overrules it.
        if (!available) return UpdateTier.NONE
        // No readable build number here, so nothing safe to compare against.
        if (runningBuild <= 0) return UpdateTier.NONE
        // The manual switch. Zero is the resting position and blocks nobody.
        if (minSupportedBuild > 0 && runningBuild < minSupportedBuild) return UpdateTier.BLOCKED
        // A new major means the old build no longer agrees with the new one about something that
        // matters. Only when what Play would actually hand this tester *is* the release the config
        // is describing, so a 2.0.0 sitting on internal testing cannot wall off a production user
        // whose best available build is still 1.x, who would then have no way out at all.
        val reachable = availableBuild > 0 && latestBuild > 0 && availableBuild >= latestBuild
        if (reachable && runningMajor > 0 && majorOf(latestVersionName) > runningMajor) {
            return UpdateTier.BLOCKED
        }
        return if (stalenessDays >= nudgeAfterDays) UpdateTier.NUDGE else UpdateTier.NONE
    }

    /**
     * The leading number of a version name: `1.3.4` gives 1.
     *
     * Parsed as a number and never compared as text, or 10 would sort below 9 and a major release
     * would stop forcing exactly when it mattered most. Zero when it cannot be read, which every
     * caller treats as "do not gate".
     */
    private fun majorOf(version: String): Int {
        val head = version.trim().substringBefore('.')
        return Regex("^\\d+").find(head)?.value?.toIntOrNull() ?: 0
    }

    /** The same shape [Repo] uses, rather than pulling in the coroutines-play-services artifact. */
    private suspend fun <T> await(task: com.google.android.gms.tasks.Task<T>): T =
        suspendCancellableCoroutine { cont ->
            task.addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
}
