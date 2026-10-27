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
import com.eazyverse.testtrack.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.math.abs

/**
 * The night's camera: one picture of each app, taken the moment its visit counts.
 *
 * Started once a night with the screen-share consent the Crew page collected, and kept for the
 * whole round, because Android hands out one consent for one virtual display and asks again for
 * the next. Each [take] grabs the newest frame off the mirror while the app is still in front,
 * with the testing control in it on purpose: the picture then carries the app's name and its
 * place in the round, as the August app's did.
 *
 * Two things the August app learned are kept. A screen that is not changing sends no new frames,
 * so the last frame in hand is used rather than waiting for one; and an app still on its splash is
 * a near-blank frame, which is retried rather than filed as proof.
 */
class VisitCameraService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var width = 0
    private var height = 0

    /** The last good frame off the mirror, for a screen that has stopped changing. */
    private var lastFrame: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // First, always: a projection may only be taken by a service already in the foreground.
        goForeground()
        when (intent?.action) {
            ACTION_START ->
                runCatching { start(intent) }.onFailure {
                    Telemetry.broke("visit_camera_start", it)
                    finish()
                }
            else -> finish()
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        val code = intent.getIntExtra(EXTRA_CODE, 0)

        @Suppress("DEPRECATION")
        val data: Intent =
            (
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_DATA)
                }
            ) ?: error("no consent token")
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = manager.getMediaProjection(code, data) ?: error("consent token already used")
        projection = mp
        // Android 14 wants the callback before the display. Stopping from the status bar chip
        // lands here too.
        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { finish() }
                }
            },
            handler,
        )
        val (w, h, dpi) = screen()
        width = w
        height = h
        val ir = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = ir
        display =
            mp.createVirtualDisplay(
                "TestTrackNight",
                w,
                h,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                ir.surface,
                null,
                handler,
            )
        current = this
        ready.value = true
    }

    /** The newest frame, or the last one kept when the screen has not changed since. */
    private fun grab(): Bitmap? {
        val image = reader?.acquireLatestImage()
        if (image == null) return lastFrame?.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val plane = image.planes[0]
            val padding = plane.rowStride - plane.pixelStride * width
            val padded = Bitmap.createBitmap(width + padding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            val frame = Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
            lastFrame?.recycle()
            lastFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
            frame
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private fun finish() {
        handler.removeCallbacksAndMessages(null)
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        lastFrame?.recycle()
        lastFrame = null
        if (current === this) current = null
        ready.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Tonight's pictures", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification =
            Notification
                .Builder(this, CHANNEL)
                .setContentTitle("TestTrack")
                .setContentText("Taking one picture of each app you open tonight")
                .setSmallIcon(R.drawable.ic_notification_robot)
                .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
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

    override fun onDestroy() {
        display?.release()
        reader?.close()
        projection?.stop()
        lastFrame?.recycle()
        if (current === this) current = null
        ready.value = false
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.eazyverse.testtrack.VISIT_CAMERA"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL = "visit_camera"
        private const val NOTIF_ID = 4218
        private const val TRIES = 8
        private const val RETRY_MS = 400L

        @Volatile private var current: VisitCameraService? = null
        private val ready = MutableStateFlow(false)

        /** Whether tonight's camera is on, ready for a picture. */
        val on: StateFlow<Boolean> = ready

        /** Turn the camera on for a round, with the consent [code] and [data] the page collected. */
        fun start(
            context: Context,
            code: Int,
            data: Intent,
        ) {
            context.startForegroundService(
                Intent(context, VisitCameraService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_CODE, code)
                    .putExtra(EXTRA_DATA, data),
            )
        }

        /** Off at the end of the round, so Android's sharing chip goes with it. */
        fun stop(context: Context) {
            current?.handler?.post { current?.finish() }
            context.stopService(Intent(context, VisitCameraService::class.java))
        }

        /**
         * One picture of what is on screen now, saved under [name], or null when none could be had:
         * the camera is off, or every frame stayed blank. Waits a moment for a camera still starting.
         */
        suspend fun take(
            context: Context,
            name: String,
        ): File? {
            withTimeoutOrNull(3_000) { while (current == null) delay(100) } ?: return null
            repeat(TRIES) { attempt ->
                val camera = current ?: return null
                val frame = camera.grab()
                if (frame != null && !blank(frame)) return save(context, frame, name)
                frame?.recycle()
                if (attempt < TRIES - 1) delay(RETRY_MS)
            }
            return null
        }

        /** Nearly one colour all over: a splash or a screen still drawing, not proof of anything. */
        private fun blank(bitmap: Bitmap): Boolean {
            val first = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
            var same = 0
            var total = 0
            for (y in 1 until 12) {
                for (x in 1 until 8) {
                    val p = bitmap.getPixel(bitmap.width * x / 8, bitmap.height * y / 12)
                    total++
                    if (near(p, first)) same++
                }
            }
            return same >= total * 0.95
        }

        private fun near(
            a: Int,
            b: Int,
        ): Boolean =
            abs((a shr 16 and 0xff) - (b shr 16 and 0xff)) < 12 &&
                abs((a shr 8 and 0xff) - (b shr 8 and 0xff)) < 12 &&
                abs((a and 0xff) - (b and 0xff)) < 12

        private fun save(
            context: Context,
            bitmap: Bitmap,
            name: String,
        ): File {
            val dir = File(context.filesDir, VisitPictures.DIR).apply { mkdirs() }
            val file = File(dir, "$name.jpg")
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            bitmap.recycle()
            return file
        }
    }
}
