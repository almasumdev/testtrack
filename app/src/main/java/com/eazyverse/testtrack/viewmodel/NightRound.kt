package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.data.VisitPictures
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File

/**
 * Tonight's round: the apps to open, the one on screen now, and whether it has counted.
 *
 * App-wide rather than the Crew screen's, because most of a round happens with TestTrack behind
 * somebody else's app. With "display over other apps" allowed, the testing control floats over
 * each app with its ten seconds, its sign-in and one button; play on a counted app records it and
 * opens the next without a trip back to TestTrack. Without it, a round is the old one: open, come
 * back, and the next opens on its own.
 *
 * Every step is written to [RoundStore], so Android closing TestTrack mid-visit loses nothing: the
 * next time TestTrack comes to the front, the app that was on screen is measured and recorded.
 *
 * The proof is two things, as it was in the August app. The phone's own usage record of the app in
 * front for ten seconds, read when the visit is recorded, which the server holds to its floor; and
 * a picture of the app, taken by [VisitCameraService] the moment those ten seconds are in, while
 * the app is still on screen with the control over it. The visit is recorded first and the picture
 * follows through [VisitPictures], and only once it is in does the server count the visit.
 */
object NightRound {
    /**
     * Each app is open for twenty seconds. It counts with ten of them really in front, the
     * server's floor, and its picture is taken at a moment picked at random between the two: past
     * the app's opening logo, and not a moment anybody could time a screen for.
     */
    const val VISIT_MS = 20_000L
    const val COUNT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var round: RoundStore.Round? = null
    private var window: VisitDockWindow? = null
    private var ticker: Job? = null

    /** Time the control has been up and running for this app, and whether it is paused. */
    private var ranMs = 0L
    private var paused = false

    /** Whether this round takes pictures, and this app's: taken, failed, or not yet. */
    private var pictures = false
    private var shot: File? = null
    private var shotFailed = false
    private var shooting: Job? = null

    /** Said on the Crew page the next time it is in front: the round's last word. */
    private var pendingSay: String? = null

    fun canFloat(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Starts a round. [float] puts the testing control over each app; [pictures] is a round whose
     * camera is on, started by the Crew page with tonight's screen-share consent.
     */
    fun start(
        context: Context,
        stops: List<RoundStore.Stop>,
        float: Boolean,
        pictures: Boolean = false,
    ) {
        if (stops.isEmpty()) return
        val app = context.applicationContext
        end(app, camera = false)
        this.pictures = pictures
        round = RoundStore.Round(stops, 0, System.currentTimeMillis())
        if (float && canFloat(app)) window = VisitDockWindow(app, onPlayPause = { playPause(app) })
        begin(app)
    }

    private fun begin(context: Context) {
        val now = round ?: return
        val stop = now.current ?: return
        round = now.copy(since = System.currentTimeMillis())
        RoundStore.save(context, round)
        ranMs = 0
        paused = false
        shot = null
        shotFailed = false
        window?.show()
        publish()
        // The clock runs without the control too: it is what says when the picture is due.
        tick()
        if (pictures) shoot(context, stop)
        if (!InstalledApps.launch(context, stop.packageName)) {
            pendingSay = "${stop.label} is not on this phone. Install it from its row first."
            end(context)
            bringBack(context)
        }
    }

    private fun tick() {
        ticker?.cancel()
        ticker =
            scope.launch {
                var last = System.currentTimeMillis()
                while (isActive) {
                    delay(250)
                    val now = System.currentTimeMillis()
                    if (!paused && ranMs < VISIT_MS) {
                        ranMs = (ranMs + now - last).coerceAtMost(VISIT_MS)
                        publish()
                    }
                    last = now
                }
            }
    }

    private fun publish() {
        val now = round ?: return
        val stop = now.current ?: return
        val picture =
            if (shot != null) {
                true
            } else if (shotFailed) {
                false
            } else {
                null
            }
        window?.state(stop, now.index, now.stops.size, ranMs, paused, picture)
    }

    /**
     * The app's picture, once its ten seconds are in and while it is still on screen. A press of
     * Next before it is taken waits for it ([picture]) rather than leaving the app without one.
     */
    private fun shoot(
        context: Context,
        stop: RoundStore.Stop,
    ) {
        shooting?.cancel()
        shooting =
            scope.launch {
                val at = COUNT_MS + kotlin.random.Random.nextLong(VISIT_MS - COUNT_MS)
                while (isActive && ranMs < at) delay(200)
                val file = VisitCameraService.take(context, "${stop.seatId.take(8)}_${System.currentTimeMillis()}")
                if (round?.current?.seatId != stop.seatId) {
                    file?.delete()
                    return@launch
                }
                shot = file
                shotFailed = file == null
                publish()
            }
    }

    /** A picture still being taken is waited for, briefly, before the app is left. */
    private suspend fun picture(): File? {
        if (!pictures) return null
        shooting?.let { job -> withTimeoutOrNull(4_000) { job.join() } }
        return shot
    }

    /** The control's one button: pause or play while the clock runs, the next app once it counts. */
    private fun playPause(context: Context) {
        if (ranMs >= VISIT_MS) {
            next(context)
        } else {
            paused = !paused
            publish()
        }
    }

    private fun next(context: Context) {
        val now = round ?: return
        val stop = now.current ?: return
        scope.launch {
            val taken = picture()
            when (val result = record(context, stop, now.since, taken)) {
                is Outcome.Short -> {
                    // Left the app part way, by the phone's own count: the clock goes back to that.
                    ranMs = result.ms
                    paused = false
                    publish()
                }
                is Outcome.Failed -> {
                    pendingSay = result.message
                    end(context)
                    bringBack(context)
                }
                Outcome.Counted -> {
                    if (now.index + 1 < now.stops.size) {
                        round = now.copy(index = now.index + 1)
                        begin(context)
                    } else {
                        pendingSay = "That is tonight done."
                        end(context)
                        bringBack(context)
                    }
                }
            }
        }
    }

    /**
     * TestTrack is in front again. Whatever app was being visited is measured and recorded, and
     * [say] gets the line for the page. A floating round ends here, since its control is not over
     * an app any more; the old round carries on to the next app by itself.
     */
    fun onTestTrackInFront(
        context: Context,
        say: (String?) -> Unit,
    ) {
        val app = context.applicationContext
        pendingSay?.let {
            pendingSay = null
            say(it)
            return
        }
        // The process was closed mid-visit: the round is only on disk now.
        val now = round ?: RoundStore.load(app)?.also { round = it } ?: return
        val stop = now.current ?: return end(app)
        val floating = window != null
        window?.hide()
        ticker?.cancel()
        scope.launch {
            // Back in TestTrack, so a picture not taken yet can no longer be: what is in hand goes.
            shooting?.cancel()
            when (val result = record(app, stop, now.since, shot)) {
                is Outcome.Short -> {
                    end(app)
                    say(
                        "That was ${result.ms / 1000}s in ${stop.label}. " +
                            "It needs ${COUNT_MS / 1000}, so it has not counted yet.",
                    )
                }
                is Outcome.Failed -> {
                    end(app)
                    say(result.message)
                }
                Outcome.Counted -> {
                    val left = now.stops.size - now.index - 1
                    when {
                        left == 0 -> {
                            end(app)
                            say("That is tonight done.")
                        }
                        floating -> {
                            end(app)
                            say("${stop.label} counted. Open all again for the $left left.")
                        }
                        else -> {
                            round = now.copy(index = now.index + 1)
                            say(null)
                            begin(app)
                        }
                    }
                }
            }
        }
    }

    private sealed interface Outcome {
        data object Counted : Outcome

        data class Short(
            val ms: Long,
        ) : Outcome

        data class Failed(
            val message: String,
        ) : Outcome
    }

    private suspend fun record(
        context: Context,
        stop: RoundStore.Stop,
        since: Long,
        taken: File? = null,
    ): Outcome {
        val used = UsageRepo.measure(context, stop.packageName, since, needMs = COUNT_MS, source = "crew")
        if (used < COUNT_MS) return Outcome.Short(used)
        return try {
            // Which copy was opened: a copy Play did not put here, or one older than the owner's
            // newest past its 24 hours, is refused with what to do.
            val copy = InstalledApps.info(context, stop.packageName)
            Api.visit(stop.seatId, used, versionCode = copy.versionCode.takeIf { copy.installed }, installer = copy.installer.orEmpty())
            // The visit first, then its picture, in the background: the next app does not wait.
            if (taken != null) {
                VisitPictures.add(context, VisitPictures.Waiting(stop.seatId, stop.label, taken.path))
                scope.launch { VisitPictures.send(context) }
            }
            Outcome.Counted
        } catch (e: ApiError) {
            Outcome.Failed(e.message)
        } catch (_: Exception) {
            Outcome.Failed("We could not reach TestTrack. Check your connection.")
        }
    }

    private fun end(
        context: Context,
        camera: Boolean = true,
    ) {
        ticker?.cancel()
        shooting?.cancel()
        window?.hide()
        window = null
        round = null
        RoundStore.save(context, null)
        // Off with the round, so Android's sharing chip goes with it.
        if (camera && pictures) VisitCameraService.stop(context)
        if (camera) pictures = false
    }

    /** TestTrack to the front, on the Crew tab, once a floating round is over. */
    private fun bringBack(context: Context) {
        val intent =
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra("openCrew", true)
            } ?: return
        runCatching { context.startActivity(intent) }
    }
}
