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
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.eazyverse.testtrack.MainActivity
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
 * The projection is held open for the whole session, because it survives app switches: the tester
 * confirms screen sharing once and every app that day is captured under that single grant.
 *
 *   startSession(consent)   -> projection + virtual display stay open
 *   capture(pkg)            -> a visit: frame grabbed at an unannounced moment, TestTrack pulled
 *                              back when the thirty seconds are up, usage read on the way out
 *   endSession()            -> projection stops
 */
class CaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0

    /** Elapsed-realtime deadline for ending the current visit. */
    private var returnDue = 0L

    /** The frame taken mid-visit, held until the visit is over and its usage can be read. */
    private var captured: Pair<String, String>? = null

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
                    status = "screen sharing failed: ${e.message}"
                    sessionActive = false
                    stopSelf()
                }
            }

            ACTION_ROUND -> {
                val packages = intent.getStringArrayListExtra(EXTRA_QUEUE).orEmpty()
                if (projection == null) {
                    status = "no active session"
                } else {
                    queue.clear()
                    queue.addAll(packages)
                    roundTotal = queue.size
                    roundIndex = 0
                    visitNext()
                }
            }

            ACTION_ABORT -> {
                queue.clear()
                handler.removeCallbacksAndMessages(null)
                capturing = null
                roundTotal = 0
                roundIndex = 0
                status = null
                back()
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
        val mp = manager.getMediaProjection(code, data)
        projection = mp

        // Android 14+ requires a callback registered before createVirtualDisplay().
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                status = "screen sharing stopped"
                teardown()
            }
        }, handler)

        val (w, h, dpi) = screen()
        width = w
        height = h

        // No OnImageAvailableListener on purpose. Draining frames as they arrive races the grab:
        // a static screen stops producing, so the listener empties the queue and the grab finds
        // nothing. VirtualDisplay drops old buffers rather than stalling, so acquiring on demand
        // reliably yields the most recent frame.
        val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = ir

        display = mp.createVirtualDisplay(
            "TestTrackCapture", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            ir.surface, null, handler
        )

        sessionActive = true
        status = "ready — open an app"
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

        if (shot != null && looksBlank(shot) && tries < MAX_TRIES) {
            status = "waiting for the app to load…"
            handler.postDelayed({ attempt(pkg, tries + 1, shot) }, RETRY_MS)
            return
        }

        if (shot != null) {
            val file = save(pkg, shot)
            shot.recycle()
            captured = pkg to file.absolutePath
        }

        // The shot is not the end of the visit. The tester was asked for a full thirty seconds and
        // the usage figure has to be able to show it, so hold here until the visit is up.
        val remaining = returnDue - SystemClock.elapsedRealtime()
        if (remaining > 0) handler.postDelayed({ finish(pkg) }, remaining) else finish(pkg)
    }

    /**
     * Banks the visit, then moves straight on to the next app.
     *
     * TestTrack is deliberately not brought forward between apps — a round of twelve should be one
     * uninterrupted stretch, not twelve trips back to a list to press the same button again.
     */
    private fun finish(pkg: String) {
        val path = captured?.takeIf { it.first == pkg }?.second
        if (path != null) {
            results[pkg] = Capture(path, UsageRepo.foregroundMsToday(this, pkg))
            captured = null
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
            roundTotal = 0
            roundIndex = 0
            status = null
            back()
            return
        }

        roundIndex += 1
        capturing = pkg
        status = "Opening ${label(pkg)} — $roundIndex of $roundTotal"
        returnDue = SystemClock.elapsedRealtime() + VISIT_MS
        InstalledApps.launch(this, pkg)
        handler.postDelayed(
            { attempt(pkg, 0, null) },
            Random.nextLong(SHOT_EARLIEST_MS, SHOT_LATEST_MS)
        )
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
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun teardown() {
        handler.removeCallbacksAndMessages(null)
        capturing = null
        captured = null
        returnDue = 0L
        queue.clear()
        roundTotal = 0
        roundIndex = 0
        sessionActive = false
        display?.release(); display = null
        reader?.close(); reader = null
        projection?.stop(); projection = null
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
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 7701

        private const val RETRY_MS = 3_000L
        private const val MAX_TRIES = 3
        private const val SAMPLE_STEP = 24
        private const val BLANK_RATIO = 0.96f

        /**
         * How long a visit is held open.
         *
         * Longer than the thirty seconds a day has to show, on purpose. The clock starts when the
         * service schedules the visit, but usage only accrues once the app has actually reached
         * the foreground a second or two later — measured at exactly thirty, honest full-length
         * visits came back at 28.7s and 29.4s and failed the very rule they had satisfied.
         */
        const val VISIT_MS = 36_000L

        /**
         * The window the screenshot lands in, somewhere at random.
         *
         * A fixed delay is learnable — open, wait for the flash, leave. A shot that could arrive
         * anywhere across twenty seconds cannot be timed around, so the only way to pass is to
         * actually be there.
         */
        const val SHOT_EARLIEST_MS = 10_000L
        const val SHOT_LATEST_MS = 32_000L

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

        fun startSession(context: Context, code: Int, data: Intent) {
            status = "starting…"
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

        private fun send(context: Context, intent: Intent) =
            ContextCompat.startForegroundService(context, intent)
    }
}
