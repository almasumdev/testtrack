package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
 * The proof is unchanged. The control's clock is only a guide; what counts is the phone's own
 * usage record of the app in front for ten seconds, read when the visit is recorded, and the
 * server refuses anything under its floor regardless.
 */
object NightRound {
    const val VISIT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var round: RoundStore.Round? = null
    private var window: VisitDockWindow? = null
    private var ticker: Job? = null

    /** Time the control has been up and running for this app, and whether it is paused. */
    private var ranMs = 0L
    private var paused = false

    /** Said on the Crew page the next time it is in front: the round's last word. */
    private var pendingSay: String? = null

    fun canFloat(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Starts a round. [float] puts the testing control over each app. */
    fun start(
        context: Context,
        stops: List<RoundStore.Stop>,
        float: Boolean,
    ) {
        if (stops.isEmpty()) return
        val app = context.applicationContext
        end(app)
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
        window?.let {
            it.show()
            publish()
            tick()
        }
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
        window?.state(stop, now.index, now.stops.size, ranMs, paused)
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
            when (val result = record(context, stop, now.since)) {
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
            when (val result = record(app, stop, now.since)) {
                is Outcome.Short -> {
                    end(app)
                    say(
                        "That was ${result.ms / 1000}s in ${stop.label}. " +
                            "It needs ${VISIT_MS / 1000}, so it has not counted yet.",
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
    ): Outcome {
        val used = UsageRepo.measure(context, stop.packageName, since, needMs = VISIT_MS, source = "crew")
        if (used < VISIT_MS) return Outcome.Short(used)
        return try {
            Api.visit(stop.seatId, used)
            Outcome.Counted
        } catch (e: ApiError) {
            Outcome.Failed(e.message)
        } catch (_: Exception) {
            Outcome.Failed("We could not reach TestTrack. Check your connection.")
        }
    }

    private fun end(context: Context) {
        ticker?.cancel()
        window?.hide()
        window = null
        round = null
        RoundStore.save(context, null)
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
