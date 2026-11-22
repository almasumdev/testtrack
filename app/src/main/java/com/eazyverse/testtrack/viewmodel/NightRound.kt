package com.eazyverse.testtrack.viewmodel

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.eazyverse.testtrack.data.InstalledApps
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.UsageRepo
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.data.VisitPictures
import com.eazyverse.testtrack.domain.RoundRules
import com.eazyverse.testtrack.net.Api
import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.net.PrivateVisitIn
import com.eazyverse.testtrack.net.privateVisit
import com.eazyverse.testtrack.ui.Snack
import com.eazyverse.testtrack.ui.SnackTone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Tonight's round: the apps to open, the one on screen now, and whether it has counted.
 *
 * App-wide rather than the Crew screen's, because most of a round happens with TestTrack behind
 * somebody else's app. With "display over other apps" allowed, the testing control floats over
 * each app with its ten seconds, its sign-in and one button; a counted app is recorded and the next
 * opens by itself once its twenty seconds are up, or sooner with the button, without a trip back
 * to TestTrack. Without it, a round is the old one: open, come
 * back, and the next opens on its own.
 *
 * Every step is written to [RoundStore], so Android closing TestTrack mid-visit loses nothing: the
 * next time TestTrack comes to the front, the app that was on screen is measured and recorded. The
 * camera does not outlive the process, so a round found that way ends there, as does one from an
 * earlier day, which only ever measures today's use.
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

    /**
     * Time the control has been up and running for this app, and whether it is paused. Pause only
     * holds the move to the next app: the clock, the picture and the recording go on without it.
     */
    private var ranMs = 0L
    private var paused = false

    /** This app's recording once it is made (counted or refused), so a held app is never recorded twice. */
    private var recorded: Outcome? = null

    /** The move to the next app is under way, so the clock and the button do not start another. */
    private var advancing = false

    /**
     * Set when TestTrack came back to the front during the move: the round ends once the app on
     * screen is recorded, with this as where its line goes, and the next app is not opened.
     */
    private var stopSay: ((Snack?) -> Unit)? = null

    /** This stop's picture is already waiting to be sent, so a late one is not queued twice. */
    private var pictureQueued = false

    /** Whether this round takes pictures, and this app's: taken, failed, or not yet. */
    private var pictures = false
    private var shot: File? = null
    private var shotFailed = false
    private var shooting: Job? = null

    /** Said on the Crew page the next time it is in front: the round's last word. */
    private var pendingSay: Snack? = null

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
        stopSay = null
        val at = System.currentTimeMillis()
        round = RoundStore.Round(stops, 0, at, pictures = pictures, startedAt = at)
        if (float && canFloat(app)) window = VisitDockWindow(app, onPlayPause = { playPause(app) })
        begin(app)
    }

    private fun begin(context: Context) {
        val now = round ?: return
        val stop = now.current ?: return
        // Past midnight the round's nights are the old one's: stop here, and open the round again.
        if (newNightFor(stop, now.startedAt)) {
            pendingSay = Snack(NEW_NIGHT_LINE)
            end(context)
            bringBack(context)
            return
        }
        round = now.copy(since = System.currentTimeMillis(), recordedIndex = -1)
        RoundStore.save(context, round)
        ranMs = 0
        paused = false
        recorded = null
        pictureQueued = false
        advancing = false
        shot = null
        shotFailed = false
        window?.show()
        publish()
        // The clock runs without the control too: it is what says when the picture is due.
        tick(context)
        if (pictures) shoot(context, stop, round?.since ?: return)
        if (!InstalledApps.launch(context, stop.packageName)) {
            pendingSay = Snack("${stop.label} is not on this phone. Install it from its row first.")
            end(context)
            bringBack(context)
        }
    }

    /**
     * The control's clock. Once an app has had its twenty seconds, a floating round records it and
     * opens the next by itself, as the August app did. Pause does not stop the clock: it only holds
     * the move, so the app is still recorded, with its picture, the moment its time is in, and the
     * next app opens when the tester plays. Without the control there is nothing over the app to
     * move on from, and the round goes on when TestTrack is back in front.
     */
    private fun tick(context: Context) {
        ticker?.cancel()
        ticker =
            scope.launch {
                var last = System.currentTimeMillis()
                while (isActive) {
                    delay(250)
                    val now = System.currentTimeMillis()
                    if (ranMs < VISIT_MS) {
                        ranMs = (ranMs + now - last).coerceAtMost(VISIT_MS)
                        publish()
                    }
                    // A held app that is already recorded waits for play without asking again.
                    if (ranMs >= VISIT_MS && window != null && !(paused && recorded != null)) next(context)
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
     *
     * The phone's usage record is asked whether the app is in front just before the picture and
     * just after it. The clock runs from the moment the app was asked to open, so an app slow to
     * open, one that closed itself, or a tester back in TestTrack would otherwise be a picture of
     * TestTrack. Not in front, it waits and asks again; left while the picture was taken, the
     * picture is thrown away and taken again.
     */
    private fun shoot(
        context: Context,
        stop: RoundStore.Stop,
        since: Long,
    ) {
        shooting?.cancel()
        shooting =
            scope.launch {
                val at = COUNT_MS + kotlin.random.Random.nextLong(VISIT_MS - COUNT_MS)
                while (isActive && ranMs < at) delay(200)
                val name = "${stop.seatId.take(8)}_${System.currentTimeMillis()}"
                var file: File? = null
                while (isActive) {
                    if (UsageRepo.inFront(context, stop.packageName, since)) {
                        file = VisitCameraService.take(context, name) ?: break
                        if (UsageRepo.inFront(context, stop.packageName, since)) break
                        file.delete()
                        file = null
                    }
                    delay(500)
                }
                if (round?.current?.seatId != stop.seatId) {
                    file?.delete()
                    return@launch
                }
                shot = file
                shotFailed = file == null
                // Taken after the app was already counted (a held app, or a slow camera): it was
                // recorded without it, so it is queued now rather than never.
                queueLate(context, stop)
                publish()
            }
    }

    /** Puts a picture on the send queue for [stop]; one crew visit, one picture. */
    private fun queuePicture(
        context: Context,
        stop: RoundStore.Stop,
        file: File,
    ) {
        if (pictureQueued) return
        pictureQueued = true
        VisitPictures.add(context, VisitPictures.Waiting(stop.seatId, stop.label, file.path, day = stop.day))
        scope.launch { VisitPictures.send(context) }
    }

    /**
     * A counted app whose picture came after its recording. Counted apps with no picture were
     * noted as missing when they were recorded, so this one is taken off that count.
     */
    private fun queueLate(
        context: Context,
        stop: RoundStore.Stop,
    ) {
        val file = shot ?: return
        if (pictureQueued || recorded != Outcome.Counted || stop.privateTestId.isNotBlank()) return
        queuePicture(context, stop, file)
        changeMissing(context, -1)
    }

    private fun changeMissing(
        context: Context,
        by: Int,
    ) {
        val now = round ?: return
        round = now.copy(noPicture = (now.noPicture + by).coerceAtLeast(0))
        RoundStore.save(context, round)
    }

    /** [now] with what the recording changed on the live round: the missing-picture count and the held mark. */
    private fun fresh(now: RoundStore.Round): RoundStore.Round =
        round?.let { now.copy(noPicture = it.noPicture, recordedIndex = it.recordedIndex) } ?: now

    /** A picture still being taken is waited for, briefly, before the app is left. */
    private suspend fun picture(): File? {
        if (!pictures) return null
        shooting?.let { job -> withTimeoutOrNull(4_000) { job.join() } }
        return shot
    }

    /**
     * The control's one button. While the clock runs it holds or releases the move to the next app;
     * once the time is in, play moves on at once, and play before that waits only the time left.
     */
    private fun playPause(context: Context) {
        if (advancing) {
            // The recording is under way: a play now is the go-ahead for the move after it.
            if (paused) {
                paused = false
                publish()
            }
            return
        }
        when {
            paused -> {
                paused = false
                publish()
                if (ranMs >= VISIT_MS) next(context)
            }
            ranMs >= VISIT_MS -> next(context)
            else -> {
                paused = true
                publish()
            }
        }
    }

    /**
     * Records the app on screen, with its picture, once its time is in, held or not, and then moves
     * on unless the tester has it paused. A held app that is already recorded moves on at once when
     * played. If TestTrack came to the front during the move ([stopSay]), the round ends here
     * instead, without opening the next app.
     */
    private fun next(context: Context) {
        if (advancing) return
        val now = round ?: return
        val stop = now.current ?: return
        advancing = true
        scope.launch {
            val result =
                recorded ?: run {
                    val taken = picture()
                    record(context, stop, now.since, taken)
                }
            // Ended or restarted by something else while it was recorded: nothing left to move.
            val live = round
            if (live == null || live.startedAt != now.startedAt) {
                advancing = false
                return@launch
            }
            // TestTrack came back in front during the move: this app is recorded once, the round
            // ends, and the page says so. The next app is not opened under it.
            val stopHere = stopSay
            if (stopHere != null) {
                stopSay = null
                val base = fresh(now)
                val moved = if (result is Outcome.Refused) base.copy(skipped = base.skipped + result.message) else base
                end(context)
                stopHere(
                    when (result) {
                        is Outcome.Short -> shortLine(stop, result)
                        is Outcome.Failed -> Snack(result.message, SnackTone.ERROR)
                        is Outcome.Refused, Outcome.Counted -> endedLine(stop, moved, result)
                    },
                )
                return@launch
            }
            when (result) {
                is Outcome.Short -> {
                    // Left the app part way, by the phone's own count: the clock goes back to that.
                    ranMs = result.ms
                    advancing = false
                    publish()
                }
                is Outcome.Failed -> {
                    pendingSay = Snack(result.message, SnackTone.ERROR)
                    end(context)
                    bringBack(context)
                }
                // Refused anyway (an update past its 24 hours, a copy not from Play): skipped, and
                // on to the rest. Its row keeps its button, and the page says what is left.
                is Outcome.Refused, Outcome.Counted -> {
                    recorded = result
                    if (result == Outcome.Counted) {
                        // Kept on disk too, so a restart while it is held never records it again.
                        round = fresh(now).copy(recordedIndex = now.index)
                        RoundStore.save(context, round)
                        // Its picture may have come while the visit was being recorded.
                        queueLate(context, stop)
                    }
                    // Held: it is recorded and stays on screen until the tester plays.
                    if (paused) {
                        advancing = false
                        publish()
                        return@launch
                    }
                    val base = fresh(now)
                    val moved = if (result is Outcome.Refused) base.copy(skipped = base.skipped + result.message) else base
                    if (moved.index + 1 < moved.stops.size) {
                        round = moved.copy(index = moved.index + 1, recordedIndex = -1)
                        begin(context)
                    } else {
                        pendingSay = lastSnack(moved)
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
        say: (Snack?) -> Unit,
    ) {
        val app = context.applicationContext
        pendingSay?.let {
            pendingSay = null
            say(it)
            return
        }
        // Back in TestTrack while the move to the next app is under way: that move records the app
        // once, so this does not, and the round ends when it is done.
        if (advancing) {
            window?.hide()
            ticker?.cancel()
            shooting?.cancel()
            stopSay = say
            return
        }
        // The process was closed mid-visit: the round is only on disk now.
        val restored = round == null
        val now =
            round ?: RoundStore.load(app)?.also {
                round = it
                pictures = it.pictures
                // A held app was counted before the process went: not recorded a second time.
                recorded = if (it.recordedIndex == it.index) Outcome.Counted else null
                pictureQueued = false
                stopSay = null
            } ?: return
        val stop = now.current ?: return end(app)
        // Found after Android closed TestTrack: without its camera, or from an earlier day, it
        // records the app that was on screen and ends rather than opening the next one.
        val today =
            LocalDate
                .now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val stale = restored && !RoundRules.resumes(now.pictures, VisitCameraService.on.value, now.startedAt, today)
        // Only today's use counts as tonight's: a round from yesterday never records yesterday's.
        val since = if (restored) RoundRules.measureFrom(now.since, today) else now.since
        // A floating round ends here, its control no longer over an app, and so does a stale one.
        val endsHere = window != null || stale
        window?.hide()
        ticker?.cancel()
        scope.launch {
            // Back in TestTrack, so a picture not taken yet can no longer be: what is in hand goes.
            shooting?.cancel()
            // A held app was already recorded when its time came in: not recorded a second time.
            when (val result = recorded ?: record(app, stop, since, shot)) {
                is Outcome.Short -> {
                    end(app)
                    say(shortLine(stop, result))
                }
                is Outcome.Failed -> {
                    end(app)
                    say(Snack(result.message, SnackTone.ERROR))
                }
                is Outcome.Refused, Outcome.Counted -> {
                    val base = fresh(now)
                    val moved = if (result is Outcome.Refused) base.copy(skipped = base.skipped + result.message) else base
                    val left = moved.stops.size - moved.index - 1
                    when {
                        left == 0 || endsHere -> {
                            end(app)
                            say(endedLine(stop, moved, result))
                        }
                        // Past midnight: the next app is another night's, so it is not opened.
                        newNightFor(moved.stops[moved.index + 1], moved.startedAt) -> {
                            end(app)
                            say(Snack(NEW_NIGHT_LINE))
                        }
                        else -> {
                            round = moved.copy(index = moved.index + 1, recordedIndex = -1)
                            say(null)
                            begin(app)
                        }
                    }
                }
            }
        }
    }

    private fun shortLine(
        stop: RoundStore.Stop,
        result: Outcome.Short,
    ) = Snack("${stop.label} was open ${result.ms / 1000}s. It needs ${COUNT_MS / 1000} to count.")

    /** The line for a round that ends after [stop] was recorded: its last word, or how many are left. */
    private fun endedLine(
        stop: RoundStore.Stop,
        moved: RoundStore.Round,
        result: Outcome,
    ): Snack {
        val left = moved.stops.size - moved.index - 1
        return when {
            left == 0 -> lastSnack(moved)
            result is Outcome.Refused -> Snack("${result.message} Open all again for the $left left.")
            else -> Snack("${stop.label} counted. Open all again for the $left left.")
        }
    }

    /** The round's last word: tonight done, or how many were left out and why. A private test is its own app's. */
    private fun lastSnack(round: RoundStore.Round): Snack {
        val skipped = round.skipped
        val only = round.stops.singleOrNull()?.takeIf { it.privateTestId.isNotBlank() }
        if (only != null && skipped.isEmpty()) return Snack("${only.label} counted for tonight.", SnackTone.DONE)
        val clean = skipped.isEmpty() && round.noPicture == 0
        return Snack(roundLastWord(skipped, round.noPicture), if (clean) SnackTone.DONE else SnackTone.INFO)
    }

    /** A crew app's visit is dated by its night, so a round that has crossed midnight must not go on. */
    private fun newNightFor(
        stop: RoundStore.Stop,
        startedAt: Long,
    ) = stop.privateTestId.isBlank() && isNewNight(startedAt)

    private sealed interface Outcome {
        data object Counted : Outcome

        data class Short(
            val ms: Long,
        ) : Outcome

        data class Failed(
            val message: String,
        ) : Outcome

        /** The server will not count this app's open as it is: skipped, and the round goes on. */
        data class Refused(
            val message: String,
        ) : Outcome
    }

    private suspend fun record(
        context: Context,
        stop: RoundStore.Stop,
        since: Long,
        taken: File? = null,
    ): Outcome {
        // The visit is dated by the night it is recorded on, and the stop's picture by the night
        // the round began: across midnight the two differ, so nothing is recorded.
        if (newNightFor(stop, round?.startedAt ?: 0L)) return Outcome.Failed(NEW_NIGHT_LINE)
        val used = UsageRepo.measure(context, stop.packageName, since, needMs = COUNT_MS, source = "crew")
        if (used < COUNT_MS) return Outcome.Short(used)
        return try {
            // Which copy was opened: a copy Play did not put here, or one older than the owner's
            // newest past its 24 hours, is refused with what to do.
            val copy = InstalledApps.info(context, stop.packageName)
            // A private test counts on its own ask, from the seconds and the copy, and nothing else.
            if (stop.privateTestId.isNotBlank()) {
                Api.privateVisit(stop.privateTestId, PrivateVisitIn(used, copy.installer.orEmpty()))
                return Outcome.Counted
            }
            Api.visit(stop.seatId, used, versionCode = copy.versionCode.takeIf { copy.installed }, installer = copy.installer.orEmpty())
            // The visit first, then its picture, in the background: the next app does not wait.
            if (taken != null) {
                queuePicture(context, stop, taken)
            } else if (pictures) {
                // Counted without a picture: said plainly in the round's last line. A picture that
                // still comes while the app is held ([queueLate]) takes it off again.
                changeMissing(context, 1)
            }
            Outcome.Counted
        } catch (e: ApiError) {
            if (RoundRules.refusedOpen(e.status, e.isAuth)) Outcome.Refused(e.message) else Outcome.Failed(e.message)
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
        stopSay = null
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

/** Said when a round is found to have crossed midnight: its stops belong to the night it began. */
internal const val NEW_NIGHT_LINE = "It's a new night. Open the round again."

/**
 * Whether the phone's local date has changed since the round began, so a visit would be dated a
 * different night from the one its stop was set up for. 0 (a round from a build that did not keep
 * its start) is never a new night.
 */
internal fun isNewNight(
    startedAt: Long,
    nowMs: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean =
    startedAt > 0 &&
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate() != Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()

/** The round's last line: tonight done, how many were left out, and how many counted apps have no picture. */
internal fun roundLastWord(
    skipped: List<String>,
    noPicture: Int = 0,
): String {
    if (noPicture <= 0) return RoundRules.lastWord(skipped)
    val missing =
        if (noPicture == 1) "1 picture is missing. Add it from the Crew page." else "$noPicture pictures are missing. Add them from the Crew page."
    return if (skipped.isEmpty()) "Counted, but $missing" else "${skipped.size} left. ${skipped.first()} $missing"
}
