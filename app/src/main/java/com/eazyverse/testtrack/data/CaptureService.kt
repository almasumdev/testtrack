package com.eazyverse.testtrack.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/** A finished visit: what was on screen, and how long the tester actually spent there today. */
data class Capture(val path: String, val usageMs: Long)

/**
 * Screenshots the app under test.
 *
 * An app cannot screenshot another app with ordinary APIs. MediaProjection is the sanctioned
 * route, and since Android 14 it only runs from a foreground service of type `mediaProjection`
 * that is already foreground **before** getMediaProjection() is called — hence a Service rather
 * than doing this inline in the Activity.
 *
 * The projection is held for as long as the tester is on the group screen, and no longer. It
 * survives app switches, so one confirmation covers a round of twelve, a single app opened again
 * on its own, and everything else done without leaving the page. What it must not survive is the
 * page itself: a grant that outlives the work it was given for leaves a screen recording running
 * on somebody's phone with nothing on screen to account for it, waiting to be noticed.
 *
 * So the group screen ends it on the way out, and [onTaskRemoved] ends it if TestTrack is swiped
 * away first. Ending it when a round finished was tried and was worse: the common case is a round
 * that leaves apps behind, and paying a confirmation for each of them is the cost of a rule nobody
 * asked for.
 *
 *   startSession(consent)   -> projection + virtual display open
 *   startRound(packages)    -> each app in turn: frame grabbed at an unannounced moment, TestTrack
 *                              pulled back when the visit is up, usage read on the way out
 *   endSession()            -> projection stops
 */
class CaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The ring the tester watches while an app under test is on screen.
     *
     * Created once and reused, because a WindowManager view is cheap to add and remove and
     * expensive to leak. It draws nothing at all where the overlay grant has not been given.
     */
    private val overlay by lazy { VisitOverlay(this) }
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0

    /** Elapsed-realtime deadline for ending the current visit. */
    private var returnDue = 0L

    /**
     * Wall-clock start of the current visit, which is the window the usage figure is read over.
     *
     * Wall clock and not [SystemClock.elapsedRealtime], unlike [returnDue] beside it, because this
     * one is handed to `UsageStatsManager` and its events are stamped in wall-clock time. The
     * deadline above never leaves this class, so it uses the monotonic clock that a change of time
     * zone or a corrected clock cannot move.
     */
    private var visitStartedAt = 0L

    /** The frame taken mid-visit, held until the visit is over and its usage can be read. */
    private var captured: Pair<String, String>? = null

    /**
     * The tester has switched off moving on by itself, for this visit.
     *
     * Only the advance is paused. The clock keeps running and the ring keeps emptying, because the
     * twenty seconds are owed to the app whatever the tester decides, and because a paused clock
     * would make the usage figure argue with the picture beside it. Cleared at the top of every
     * visit, so pausing is a decision about this app and not a mode the round gets stuck in.
     */
    private var paused = false

    /** The current visit has served its time and is only still here because [paused] is set. */
    private var overstaying = false

    /** What is left of the round, in order. */
    private val queue = ArrayDeque<String>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Unconditional, and before anything else. Every entry point here arrives via
        // startForegroundService, which gives the service about five seconds to call
        // startForeground or the system kills the process — and it does not care that the command
        // was "stop". A stop that arrived when nothing was running used to tear down and stopSelf
        // without ever going foreground, which crashed the app on sign-out.
        goForeground()

        when (intent?.action) {
            ACTION_START -> {
                try {
                    open(intent)
                } catch (e: Exception) {
                    status = "Screen sharing didn't start. ${e.message}"
                    sessionActive = false
                    stopSelf()
                }
            }

            ACTION_ROUND -> {
                val packages = intent.getStringArrayListExtra(EXTRA_QUEUE).orEmpty()
                if (projection == null) {
                    status = "Screen sharing isn't running."
                } else {
                    queue.clear()
                    queue.addAll(packages)
                    missed.clear()
                    roundTotal = queue.size
                    roundIndex = 0
                    Telemetry.roundStarted(roundTotal)
                    visitNext()
                }
            }

            ACTION_ABORT -> {
                queue.clear()
                handler.removeCallbacksAndMessages(null)
                overlay.hide()
                capturing = null
                paused = false
                overstaying = false
                roundTotal = 0
                roundIndex = 0
                status = null
                // Not when the tester walked back here themselves. They are already looking at
                // TestTrack, and re-launching the activity under them is a flicker that says
                // something went wrong.
                if (intent.getBooleanExtra(EXTRA_RETURN, true)) back()
            }

            ACTION_STOP -> {
                teardown()
                stopSelf()
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun open(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)

        @Suppress("DEPRECATION")
        val data: Intent = (
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
            else
                intent.getParcelableExtra(EXTRA_DATA)
            ) ?: error("no consent token")

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Nullable, and not only in theory: since Android 14 a consent token is spent by the
        // projection it creates, so a second start on the same token comes back empty. Thrown
        // rather than swallowed, because onStartCommand turns it into a status the tester can
        // read — a silent null would leave a session that looks live and captures nothing.
        val mp = manager.getMediaProjection(code, data) ?: error("consent token already used")
        projection = mp

        // Android 14+ requires a callback registered before createVirtualDisplay().
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                status = "Screen sharing stopped."
                teardown()
            }
        }, handler)

        mirror()
        displays.registerDisplayListener(rotations, handler)

        sessionActive = true
        status = "Ready. Open an app to start."
    }

    private val displays by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /**
     * Rebuilds the mirror when the real screen changes shape underneath it.
     *
     * An app that forces landscape rotates the display, and the mirror does not survive it. The
     * projection stays open, the virtual display stays alive, the tester sees nothing wrong — and
     * frames simply stop arriving, for good. One piano app a third of the way through a round is
     * enough to lose every capture after it.
     */
    private val rotations = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId != Display.DEFAULT_DISPLAY || projection == null) return
            val (w, h, _) = screen()
            if (w != width || h != height) mirror()
        }
    }

    /**
     * Points the mirror at a fresh reader, at whatever size the screen is now.
     *
     * The virtual display is created once per consent and never again. Android 14 made
     * `createVirtualDisplay` one call per token and throws a SecurityException on the second, and
     * this used to release and rebuild — so the moment an app that forces landscape rotated the
     * screen, the listener fired, the second call went in, and the whole app went down mid-round.
     * A tester who opened a piano app lost the round and everything after it.
     *
     * Resizing and handing over a new surface is the supported way to follow the screen, and it
     * reattaches the mirror, which is the thing that was actually broken. Failure ends the round
     * with something to do about it rather than taking the app with it: without a mirror there
     * are no frames, and carrying on would bank nothing while saying nothing.
     */
    private fun mirror() {
        val mp = projection ?: return
        val (w, h, dpi) = screen()

        runCatching {
            // No OnImageAvailableListener on purpose. Draining frames as they arrive races the
            // grab: a static screen stops producing, so the listener empties the queue and the
            // grab finds nothing. VirtualDisplay drops old buffers rather than stalling, so
            // acquiring on demand reliably yields the most recent frame.
            val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            val old = reader
            reader = ir
            width = w
            height = h

            val vd = display
            if (vd == null) {
                display = mp.createVirtualDisplay(
                    "TestTrackCapture", w, h, dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    ir.surface, null, handler
                )
            } else {
                vd.resize(w, h, dpi)
                vd.setSurface(ir.surface)
            }

            // Closed a beat late. The display is still writing to the old surface at the moment
            // it is swapped, and closing underneath it abandons a queue mid-frame.
            old?.let { handler.postDelayed({ runCatching { it.close() } }, HANDOVER_MS) }
        }.onFailure {
            // The one that took a whole release to find, because the only person who could see it
            // was the tester it happened to. See [Telemetry].
            Telemetry.broke("mirror", it)
            status = "Screen sharing stopped. Start the round again from your group."
            teardown()
        }
    }

    /**
     * Grabs, and gives a still-loading app more time rather than filing its splash as proof.
     *
     * [previous] carries the last good frame forward. A static screen produces no new frames, so
     * `acquireLatestImage` returns null on every retry — without this, retrying a still screen
     * loses the capture entirely instead of keeping the perfectly good frame already in hand.
     */
    private fun attempt(pkg: String, tries: Int, previous: Bitmap?) {
        val fresh = frame()
        val shot = fresh ?: previous
        if (fresh != null && previous != null && previous !== fresh) previous.recycle()

        // Nothing at all, on a screen that is showing something: the mirror has come off the real
        // display. It is rebuilt and asked again rather than treated as a screen with no picture
        // on it, because the alternative is banking nothing and saying nothing.
        if (shot == null && tries < MAX_TRIES) {
            mirror()
            handler.postDelayed({ attempt(pkg, tries + 1, null) }, REMIRROR_MS)
            return
        }

        if (shot != null && looksBlank(shot) && tries < MAX_TRIES) {
            status = "Waiting for the app to load…"
            handler.postDelayed({ attempt(pkg, tries + 1, shot) }, RETRY_MS)
            return
        }

        if (shot != null) {
            val file = save(pkg, shot)
            shot.recycle()
            captured = pkg to file.absolutePath
        }

        // The shot is not the end of the visit. The tester was asked for the full ten seconds and
        // the usage figure has to be able to show it, so hold here until the visit is up.
        val remaining = returnDue - SystemClock.elapsedRealtime()
        if (remaining > 0) handler.postDelayed({ served(pkg) }, remaining) else served(pkg)
    }

    /**
     * The visit has served its time. Either it ends here or it waits to be sent on.
     *
     * This is the whole of what Pause buys. Every visit reaches this point at the same moment
     * whether the tester touched anything or not, and all the button decides is which of the two
     * things happens next.
     */
    private fun served(pkg: String) {
        if (capturing != pkg) return
        if (paused) {
            // Already showing play, and it stays showing play. The button does not change at the
            // end of a paused visit because what it is offering has not changed: carry on.
            overstaying = true
        } else {
            finish(pkg)
        }
    }

    /**
     * The one button on the overlay, pressed.
     *
     * Play means the same thing in both places it appears, which is why there are two icons rather
     * than three. Before the ring empties it hands the visit back to the clock; after it, there is
     * no clock left to hand it to, so it moves on itself.
     */
    private fun tap(pkg: String) {
        if (capturing != pkg) return
        if (overstaying) {
            finish(pkg)
            return
        }
        paused = !paused
        overlay.action(if (paused) VisitOverlay.Mode.PLAY else VisitOverlay.Mode.PAUSE)
    }

    /**
     * Banks the visit, then moves straight on to the next app.
     *
     * TestTrack is deliberately not brought forward between apps — a round of twelve should be one
     * uninterrupted stretch, not twelve trips back to a list to press the same button again.
     */
    private fun finish(pkg: String) {
        overlay.hide()
        val path = captured?.takeIf { it.first == pkg }?.second
        if (path != null) {
            results[pkg] = Capture(path, UsageRepo.foregroundMsSince(this, pkg, visitStartedAt))
            captured = null
        } else {
            // A visit that produced no picture used to end exactly like one that did, and the app
            // it was for stayed on the list with nothing said about why. Twenty seconds of the
            // tester's evening went into it, so it is worth a sentence.
            missed += pkg
        }
        capturing = null
        visitNext()
    }

    /**
     * Opens the next app in the round and schedules its capture, or ends the round.
     *
     * Launching another app from a service is a background activity launch, which Android refuses
     * without SYSTEM_ALERT_WINDOW — the same grant [back] needs. Without it the round stalls after
     * the first app rather than failing loudly, so the caller checks for it up front.
     */
    private fun visitNext() {
        val pkg = queue.removeFirstOrNull()
        if (pkg == null) {
            Telemetry.roundFinished(captured = results.size, missed = missed.size)
            roundTotal = 0
            roundIndex = 0
            status = null
            back()
            return
        }

        roundIndex += 1
        capturing = pkg
        paused = false
        overstaying = false
        status = "Opening ${label(pkg)}, $roundIndex of $roundTotal"
        returnDue = SystemClock.elapsedRealtime() + VISIT_MS
        visitStartedAt = System.currentTimeMillis()
        InstalledApps.launch(this, pkg)
        handler.postDelayed(
            { attempt(pkg, 0, null) },
            Random.nextLong(SHOT_EARLIEST_MS, SHOT_LATEST_MS)
        )

        // Raised after the launch, so it lands on top of the app rather than under it.
        overlay.show(millis = VISIT_MS, onTap = { tap(pkg) })
    }

    private fun label(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** The most recent frame off the virtual display, cropped back from its padded stride. */
    private fun frame(): Bitmap? {
        val image = reader?.acquireLatestImage() ?: return null
        return try {
            // The buffer is row-padded to a hardware-friendly stride, so the bitmap comes out
            // slightly too wide and has to be cropped back.
            val plane = image.planes[0]
            val padding = plane.rowStride - plane.pixelStride * width
            val padded = Bitmap.createBitmap(
                width + padding / plane.pixelStride, height, Bitmap.Config.ARGB_8888
            )
            padded.copyPixelsFromBuffer(plane.buffer)
            Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    /**
     * Is this still a splash screen?
     *
     * A launch screen is overwhelmingly one colour; a loaded page is not. Sampling a sparse grid
     * and asking how dominant the commonest colour is separates them cheaply. Needed because a
     * WebView wrapper can still be showing its logo well past eight seconds.
     *
     * The threshold is deliberately strict: a legitimately clean UI on a white background sits
     * around 90%, and waiting on those would slow every capture for nothing.
     */
    private fun looksBlank(bitmap: Bitmap): Boolean {
        val counts = HashMap<Int, Int>()
        var total = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                // Quantise to 4 bits per channel so near-identical shades collapse together.
                val p = bitmap.getPixel(x, y)
                val key = (((p shr 20) and 0xF) shl 8) or
                    (((p shr 12) and 0xF) shl 4) or
                    ((p shr 4) and 0xF)
                counts[key] = (counts[key] ?: 0) + 1
                total++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }
        val dominant = counts.values.maxOrNull() ?: return false
        return total > 0 && dominant.toFloat() / total > BLANK_RATIO
    }

    /** 1080px on the long edge, JPEG 80 — roughly 150 KB, well inside anyone's Drive quota. */
    private fun save(pkg: String, bitmap: Bitmap): File {
        val dir = File(filesDir, "captures").apply { mkdirs() }
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest <= 1080) bitmap else {
            val ratio = 1080f / longest
            Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true
            )
        }
        val file = File(dir, "${pkg}_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file
    }

    private fun screen(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            Triple(bounds.width(), bounds.height(), resources.configuration.densityDpi)
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            Triple(dm.widthPixels, dm.heightPixels, dm.densityDpi)
        }
    }

    /**
     * Bring TestTrack forward so the tester lands back on the list.
     *
     * This is a background activity launch, which Android blocks outright (BAL_BLOCK) unless the
     * app holds SYSTEM_ALERT_WINDOW. Without that grant the capture still succeeds and the tester
     * simply switches back by hand.
     */
    private fun back() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            )
        }
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Proof capture", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("TestTrack")
            .setContentText("Capturing today's proof")
            // The app's own mark, like every other notification it raises. This was Android's
            // stock camera glyph, which is the one notification a tester sees while their screen
            // is being recorded and the one that most needs to say who is doing it.
            .setSmallIcon(R.drawable.ic_notification)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun teardown() {
        overlay.hide()
        runCatching { displays.unregisterDisplayListener(rotations) }
        handler.removeCallbacksAndMessages(null)
        capturing = null
        captured = null
        paused = false
        overstaying = false
        returnDue = 0L
        visitStartedAt = 0L
        queue.clear()
        roundTotal = 0
        roundIndex = 0
        sessionActive = false
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
    }

    /**
     * TestTrack was swiped out of recents.
     *
     * Safe to treat as an ending even mid-round, because a round leaves TestTrack's task sitting
     * in recents while somebody else's app is on screen — removing it is a deliberate act and not
     * something the round does on its own.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        teardown()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_ROUND = "round"
        private const val ACTION_ABORT = "abort"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_QUEUE = "queue"
        private const val EXTRA_RETURN = "return"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 7701

        private const val RETRY_MS = 3_000L

        /**
         * How long a rebuilt mirror is given before it is asked for a frame.
         *
         * Much shorter than [RETRY_MS], which is a wait for somebody else's splash screen to
         * finish. This one is a wait for our own virtual display to render its first frame, and
         * that is a matter of a frame or two.
         */
        private const val REMIRROR_MS = 700L

        /** Long enough for the display to have let go of the surface it was handed. */
        private const val HANDOVER_MS = 250L
        private const val MAX_TRIES = 3
        private const val SAMPLE_STEP = 24
        private const val BLANK_RATIO = 0.96f

        /**
         * How long a visit is held open.
         *
         * Longer than the ten seconds a day has to show, on purpose. The clock starts when the
         * service schedules the visit, but usage only accrues once the app has actually reached
         * the foreground a second or two later, so a visit measured at exactly the bar comes back
         * a shade under it and fails the very rule it satisfied.
         *
         * Not ten plus a margin, though, and this is the part the number cannot be cut past: the
         * screenshot has to land after the app under test has finished showing its logo, and a
         * WebView wrapper can still be on its splash at eight seconds. Ten would photograph
         * splash screens. So the visit stays long enough to take a picture worth keeping, and the
         * bar underneath it is what actually has to be cleared.
         */
        const val VISIT_MS = 20_000L

        /**
         * The window the screenshot lands in, somewhere at random.
         *
         * A fixed delay is learnable — open, wait for the flash, leave. A shot that could arrive
         * anywhere across seven seconds cannot be timed around, so the only way to pass is to
         * actually be there.
         *
         * The earliest is held at ten seconds even though the visit is shorter now, because that
         * is the splash screen budget rather than a fraction of the visit.
         */
        const val SHOT_EARLIEST_MS = 10_000L
        const val SHOT_LATEST_MS = 17_000L

        /** Observed by the UI, which is not running while the app under test is on screen. */
        var sessionActive by mutableStateOf(false)
            private set
        var status by mutableStateOf<String?>(null)
            private set

        /** Package currently being visited, or null. */
        var capturing by mutableStateOf<String?>(null)
            private set

        /** Position in the round, for a "3 of 12" line while the tester is in someone else's app. */
        var roundIndex by mutableStateOf(0)
            private set
        var roundTotal by mutableStateOf(0)
            private set

        val roundActive: Boolean get() = roundTotal > 0

        /** package -> a finished visit, awaiting upload. */
        val results = mutableStateMapOf<String, Capture>()

        /**
         * Packages that were visited and came back without a picture.
         *
         * Kept apart from [results] because there is nothing to upload and nothing to record — the
         * only thing owed here is telling the tester, so that a round which quietly did nothing for
         * five apps cannot look like a round that worked.
         */
        val missed = mutableStateListOf<String>()

        fun startSession(context: Context, code: Int, data: Intent) {
            status = "Starting…"
            send(context, Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CODE, code)
                putExtra(EXTRA_DATA, data)
            })
        }

        /**
         * Runs a round: each app in turn, [VISIT_MS] apiece, frame grabbed at an unannounced
         * moment inside each visit, no stop back at the list in between. A round of one is how a
         * single app's Open button works, so both paths behave identically.
         */
        fun startRound(context: Context, packages: List<String>) {
            if (packages.isEmpty()) return
            send(context, Intent(context, CaptureService::class.java).apply {
                action = ACTION_ROUND
                putStringArrayListExtra(EXTRA_QUEUE, ArrayList(packages))
            })
        }

        /** Abandons whatever is left of the round and comes back. */
        fun abortRound(context: Context) {
            if (!roundActive) return
            send(context, Intent(context, CaptureService::class.java).apply { action = ACTION_ABORT })
        }

        /**
         * The tester came back to TestTrack in the middle of a round, so the round is over.
         *
         * A round is meant to be one uninterrupted stretch, and the service is deliberately deaf
         * to where the tester actually is — it opens the next app on a timer whether or not
         * anybody is still following it. Walking back here used to leave it running, so the next
         * app would open a few seconds later and take the phone off them again.
         *
         * Safe to call on every resume. `roundActive` is already false by the time a round that
         * ended on its own brings the app forward, so this only ever fires on a departure.
         */
        fun leftRound(context: Context) {
            if (!roundActive) return
            send(
                context,
                Intent(context, CaptureService::class.java).apply {
                    action = ACTION_ABORT
                    putExtra(EXTRA_RETURN, false)
                }
            )
        }

        /**
         * Ends screen sharing. A no-op when there is nothing to end — starting a service purely to
         * stop it is wasteful, and it is the path that used to crash.
         */
        fun endSession(context: Context) {
            if (!sessionActive) return
            send(context, Intent(context, CaptureService::class.java).apply { action = ACTION_STOP })
        }

        fun consume(pkg: String) {
            results.remove(pkg)
        }

        /** Called once the tester has been told, so the same sentence is not shown twice. */
        fun forgetMissed() {
            missed.clear()
        }

        private fun send(context: Context, intent: Intent) =
            ContextCompat.startForegroundService(context, intent)
    }
}
