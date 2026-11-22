package com.eazyverse.testtrack.viewmodel

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.ui.UninstallRunState
import com.eazyverse.testtrack.ui.UninstallStep
import com.eazyverse.testtrack.ui.advanced
import com.eazyverse.testtrack.ui.beginUninstall
import com.eazyverse.testtrack.ui.settled
import com.eazyverse.testtrack.ui.stopped
import com.eazyverse.testtrack.ui.uninstall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * What a run needs from the phone, handed in on each call so the run itself holds no Context:
 * a fresh read of whether a package is installed (never a cached one) and Android's own
 * "uninstall this app?" for a package, which says whether it could be opened at all.
 */
class UninstallPhone(
    val installed: (String) -> Boolean,
    val ask: (String) -> Boolean,
    /** Re-reads what the rows read from the cache, so the card's count follows a removal. */
    val refresh: () -> Unit = {},
) {
    companion object {
        fun of(context: Context) =
            UninstallPhone(
                installed = { InstalledApps.info(context, it).installed },
                ask = { uninstall(context, it) },
                refresh = { InstalledApps.refresh(context) },
            )
    }
}

/**
 * Uninstall all on a finished crew: one app at a time, Android's own confirm for each, and
 * TestTrack never taps OK for anyone.
 *
 * How it knows what happened. After the confirm is opened the page is paused; when it is resumed
 * again the app is read fresh from the phone. Gone: ticked off, a short pause (the page calls
 * [next] after it), then the next confirm. Still there: the person said no, so the run stops on
 * "You kept it" until Next app or Stop. The activity result is never trusted, since the dialogs
 * differ between phones. A confirm cannot be opened from the background (Android 10 and later),
 * so a next app that is due while TestTrack is away waits ([UninstallStep.WAIT]) for the return.
 *
 * The state stays here while the view model lives, so leaving the page pauses the run and coming
 * back shows what is left.
 */
class UninstallRun(
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<UninstallRunState?>(null)
        private set

    private var inFront = true
    private var sawPause = false
    private var reading: Job? = null

    /** The first run, or "the rest": [left] are the packages on the phone now. */
    fun start(
        phone: UninstallPhone,
        crewId: String,
        left: List<String>,
    ) {
        // A second tap while a confirm is up or a tick is showing must not open another.
        val now = state
        if (now?.crewId == crewId && (now.step == UninstallStep.ASKING || now.step == UninstallStep.TICK)) return
        val earlier = state?.takeIf { it.crewId == crewId }?.removed.orEmpty()
        val run = beginUninstall(crewId, left, earlier) ?: return
        state = run
        ask(phone, run)
    }

    /** The page was covered, by Android's confirm or anything else. */
    fun onPause() {
        inFront = false
        if (state?.step == UninstallStep.ASKING) sawPause = true
    }

    /** The page is in front again: read what the confirm came to, or carry on with a waiting run. */
    fun onResume(
        phone: UninstallPhone,
        crewId: String?,
    ) {
        inFront = true
        val run = state ?: return
        // Only the page of the crew the run is for acts on it; any other page just notes it is in front.
        if (run.crewId != crewId) return
        when {
            run.step == UninstallStep.ASKING && sawPause -> read(phone, run)
            run.step == UninstallStep.WAIT -> next(phone)
        }
    }

    /** After a tick, or "Next app": the next confirm, if TestTrack is in front to open it. */
    fun next(phone: UninstallPhone) {
        val run = state ?: return
        if (run.step != UninstallStep.TICK && run.step != UninstallStep.KEPT && run.step != UninstallStep.WAIT) return
        if (!inFront) {
            state = run.copy(step = UninstallStep.WAIT)
            return
        }
        val following = run.advanced(phone.installed)
        state = following
        if (following.step == UninstallStep.ASKING) ask(phone, following)
    }

    fun stop() {
        state = state?.stopped()
    }

    private fun ask(
        phone: UninstallPhone,
        run: UninstallRunState,
    ) {
        val pkg = run.current ?: return
        sawPause = false
        // It could not be opened at all: the same as the person saying no, so the run does not hang.
        if (!phone.ask(pkg)) state = run.settled(pkg, gone = false)
    }

    /**
     * A fresh read of the app. The phone can take a moment to finish a removal after OK, so a
     * package still there is read again a few times before it counts as kept.
     */
    private fun read(
        phone: UninstallPhone,
        run: UninstallRunState,
    ) {
        val pkg = run.current ?: return
        if (reading?.isActive == true) return
        sawPause = false
        reading =
            scope.launch {
                var gone = !phone.installed(pkg)
                var tries = 0
                while (!gone && tries < RECHECKS) {
                    delay(RECHECK_MS)
                    gone = !phone.installed(pkg)
                    tries++
                }
                state = state?.settled(pkg, gone)
                phone.refresh()
            }
    }

    private companion object {
        const val RECHECKS = 8
        const val RECHECK_MS = 400L
    }
}
