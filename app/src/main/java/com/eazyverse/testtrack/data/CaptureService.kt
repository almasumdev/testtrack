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
 *   capture(pkg, dwellMs)   -> a frame is grabbed after the dwell and TestTrack pulled back
 *   endSession()            -> projection stops
 */
class CaptureService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                goForeground()
                try {
                    open(intent)
                } catch (e: Exception) {
                    status = "screen sharing failed: ${e.message}"
                    sessionActive = false
                    stopSelf()
                }
            }

            ACTION_CAPTURE -> {
                val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
                val dwell = intent.getLongExtra(EXTRA_DWELL, 8_000L)
                if (projection == null) {
                    status = "no active session"
                } else {
                    capturing = pkg
                    status = "capturing in ${dwell / 1000}s…"
                    handler.postDelayed({ attempt(pkg, 0, null) }, dwell)
                }
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

        if (shot == null) {
            status = "no frame captured — try a longer wait"
        } else {
            val file = save(pkg, shot)
            shot.recycle()
            results[pkg] = file.absolutePath
            status = "captured"
        }
        capturing = null
        back()
    }

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
        private const val ACTION_CAPTURE = "capture"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_DWELL = "dwell"
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 7701

        private const val RETRY_MS = 3_000L
        private const val MAX_TRIES = 3
        private const val SAMPLE_STEP = 24
        private const val BLANK_RATIO = 0.96f

        /** Observed by the UI, which is not running while the app under test is on screen. */
        var sessionActive by mutableStateOf(false)
            private set
        var status by mutableStateOf<String?>(null)
            private set

        /** Package currently being captured, or null. */
        var capturing by mutableStateOf<String?>(null)
            private set

        /** package -> local JPEG path, awaiting upload. */
        val results = mutableStateMapOf<String, String>()

        fun startSession(context: Context, code: Int, data: Intent) {
            status = "starting…"
            send(context, Intent(context, CaptureService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_CODE, code)
                putExtra(EXTRA_DATA, data)
            })
        }

        /** [dwellMs] is how long the app under test is left on screen before the grab. */
        fun capture(context: Context, pkg: String, dwellMs: Long) {
            send(context, Intent(context, CaptureService::class.java).apply {
                action = ACTION_CAPTURE
                putExtra(EXTRA_PKG, pkg)
                putExtra(EXTRA_DWELL, dwellMs)
            })
        }

        fun endSession(context: Context) {
            send(context, Intent(context, CaptureService::class.java).apply { action = ACTION_STOP })
        }

        fun consume(pkg: String) {
            results.remove(pkg)
        }

        private fun send(context: Context, intent: Intent) =
            ContextCompat.startForegroundService(context, intent)
    }
}
