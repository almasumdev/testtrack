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
import android.view.Gravity
import android.view.WindowManager
import android.widget.ImageView
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * The review's one screenshot: a camera button that floats over Play, and one frame when it is
 * pressed.
 *
 * Started with the screen-capture consent the review page just collected, it puts a round button
 * in the right thumb's reach, fixed where it is, and waits. A press hides the button, takes the
 * frame under it, ends the capture at once, and brings TestTrack back to the review with the
 * picture attached ([captured]). Nothing is recorded before the press and nothing after it.
 *
 * Its own service rather than the nightly round's [CaptureService], which carries a queue, a
 * schedule and a timer this has no use for. The pieces that took a release each to get right are
 * the same: foreground before the projection, the callback before the display, the newest frame
 * acquired on demand, the padded row cropped back.
 */
class ReviewCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var button: ImageView? = null
    private var width = 0
    private var height = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // First, always: started with startForegroundService, and a projection may only be taken
        // by a service that is already foreground.
        goForeground()
        when (intent?.action) {
            ACTION_START ->
                runCatching { start(intent) }.onFailure {
                    Telemetry.broke("review_capture_start", it)
                    finish(null)
                }
            else -> finish(null)
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
        // lands here too, and takes the button away with it.
        mp.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    handler.post { finish(null) }
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
                "TestTrackReview",
                w,
                h,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                ir.surface,
                null,
                handler,
            )
        showButton()
        state.value = State.WAITING
    }

    /**
     * The button: round, in the right thumb's reach, and not draggable on purpose. It sits where
     * the guide said it would, every time, so nobody has to look for it.
     */
    private fun showButton() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = dp(64)
        // Read out here: inside the LayoutParams block below, `height` is the window's own.
        val lift = (height * 0.24f).toInt()
        val view =
            ImageView(this).apply {
                setImageResource(R.drawable.review_capture_button)
                contentDescription = "Take the review screenshot"
                setOnClickListener { capture() }
            }
        val params =
            WindowManager
                .LayoutParams(
                    size,
                    size,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    x = dp(20)
                    // About a quarter of the way up: where a right thumb rests holding the phone,
                    // clear of Play's own bottom bar and its Post button.
                    y = lift
                }
        wm.addView(view, params)
        button = view
    }

    private fun hideButton() {
        val view = button ?: return
        button = null
        runCatching { (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view) }
    }

    /** Press: hide the button so it is not in the picture, give the screen a moment, take it. */
    private fun capture() {
        hideButton()
        state.value = State.CAPTURING
        attempt(0)
    }

    private fun attempt(tries: Int) {
        handler.postDelayed({
            val shot = frame()
            if (shot != null) {
                finish(save(shot))
            } else if (tries < 8) {
                attempt(tries + 1)
            } else {
                finish(null)
            }
        }, if (tries == 0) FIRST_GRAB_MS else RETRY_MS)
    }

    /** The newest frame off the mirror, cropped back from its padded row stride. */
    private fun frame(): Bitmap? {
        val image = reader?.acquireLatestImage() ?: return null
        return try {
            val plane = image.planes[0]
            val padding = plane.rowStride - plane.pixelStride * width
            val padded = Bitmap.createBitmap(width + padding / plane.pixelStride, height, Bitmap.Config.ARGB_8888)
            padded.copyPixelsFromBuffer(plane.buffer)
            Bitmap.createBitmap(padded, 0, 0, width, height).also { padded.recycle() }
        } catch (_: Exception) {
            null
        } finally {
            image.close()
        }
    }

    private fun save(bitmap: Bitmap): File {
        val dir = File(cacheDir, "review_proof").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "proof_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bitmap.recycle()
        return file
    }

    /**
     * Ends the capture, hands the picture (if any) to the review page, and brings TestTrack back.
     * The return is a background activity launch, allowed because the app holds the overlay grant
     * the button needed anyway.
     */
    private fun finish(file: File?) {
        hideButton()
        handler.removeCallbacksAndMessages(null)
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
        if (file != null) {
            captured.value = file
            runCatching {
                startActivity(
                    Intent(this, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                )
            }
        }
        state.value = State.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun goForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Review screenshot", NotificationManager.IMPORTANCE_LOW))
        val notification: Notification =
            Notification
                .Builder(this, CHANNEL)
                .setContentTitle("TestTrack")
                .setContentText("Tap the camera button to take your review screenshot")
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

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        hideButton()
        display?.release()
        reader?.close()
        projection?.stop()
        if (state.value != State.IDLE) state.value = State.IDLE
        super.onDestroy()
    }

    enum class State { IDLE, WAITING, CAPTURING }

    companion object {
        private const val ACTION_START = "com.eazyverse.testtrack.REVIEW_CAPTURE"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val CHANNEL = "review_capture"
        private const val NOTIF_ID = 4217
        private const val FIRST_GRAB_MS = 350L
        private const val RETRY_MS = 150L

        private val state = MutableStateFlow(State.IDLE)
        private val captured = MutableStateFlow<File?>(null)

        /** Whether the button is out on screen, waiting for its press. */
        val running: StateFlow<State> = state

        /** The last screenshot taken, until the review page has taken it. */
        val proof: StateFlow<File?> = captured

        fun takeProof() {
            captured.value = null
        }

        /** Put the button up, with the consent [code] and [data] the review page collected. */
        fun start(
            context: Context,
            code: Int,
            data: Intent,
        ) {
            val intent =
                Intent(context, ReviewCaptureService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_CODE, code)
                    .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }

        /** Take the button away without a picture, when somebody came back on their own. */
        fun cancel(context: Context) {
            if (state.value == State.IDLE) return
            context.stopService(Intent(context, ReviewCaptureService::class.java))
            state.value = State.IDLE
        }
    }
}
