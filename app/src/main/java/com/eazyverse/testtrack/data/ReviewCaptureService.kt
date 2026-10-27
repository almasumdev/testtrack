package com.eazyverse.testtrack.data

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import com.eazyverse.testtrack.MainActivity
import com.eazyverse.testtrack.R
import com.eazyverse.testtrack.domain.DockEdge
import com.eazyverse.testtrack.domain.DockSpot
import com.eazyverse.testtrack.domain.dockCorner
import com.eazyverse.testtrack.domain.stickTo
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
     * The button: round, and the first time in the right thumb's reach, a quarter of the way up the
     * right edge, clear of Play's own bottom bar and its Post button. It moves like the night's
     * testing control: dragged anywhere, stuck to the nearest edge when let go, and found where it
     * was left the next time. A tap without a drag takes the screenshot.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun showButton() {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = dp(64)
        val view =
            ImageView(this).apply {
                setImageResource(R.drawable.review_capture_button)
                contentDescription = "Take the review screenshot. Drag to move it."
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
                ).apply { gravity = Gravity.TOP or Gravity.START }
        val (x, y) = dockCorner(spot(), size.toFloat(), size.toFloat(), overlayScreen(this))
        params.x = x.toInt()
        params.y = y.toInt()

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var fromX = 0
        var fromY = 0
        var dragged = false
        view.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    fromX = params.x
                    fromY = params.y
                    dragged = false
                    slide?.cancel()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragged && dx * dx + dy * dy > slop * slop) dragged = true
                    if (dragged) {
                        params.x = fromX + dx.toInt()
                        params.y = fromY + dy.toInt()
                        runCatching { wm.updateViewLayout(v, params) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) stick(wm, v, params, size) else v.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragged) stick(wm, v, params, size)
                    true
                }
                else -> false
            }
        }
        wm.addView(view, params)
        button = view
    }

    private var slide: ValueAnimator? = null

    /** Let go: onto the edge nearest the button's centre, gently, and remembered for next time. */
    private fun stick(
        wm: WindowManager,
        v: android.view.View,
        params: WindowManager.LayoutParams,
        size: Int,
    ) {
        val screen = overlayScreen(this)
        val spot = stickTo(params.x + size / 2f, params.y + size / 2f, screen)
        keepSpot(spot)
        val (toX, toY) = dockCorner(spot, size.toFloat(), size.toFloat(), screen)
        val startX = params.x
        val startY = params.y
        slide?.cancel()
        slide =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    params.x = (startX + (toX - startX) * f).toInt()
                    params.y = (startY + (toY - startY) * f).toInt()
                    if (button != null) runCatching { wm.updateViewLayout(v, params) }
                }
                start()
            }
    }

    private fun prefs() = applicationContext.getSharedPreferences(SPOT_PREFS, Context.MODE_PRIVATE)

    /** Where the button was left; the right edge, a quarter of the way up, the first time. */
    private fun spot(): DockSpot {
        val p = prefs()
        val edge = runCatching { DockEdge.valueOf(p.getString(SPOT_EDGE, null) ?: "") }.getOrDefault(DockEdge.RIGHT)
        return DockSpot(edge, p.getFloat(SPOT_ALONG, 0.76f))
    }

    private fun keepSpot(spot: DockSpot) {
        prefs()
            .edit()
            .putString(SPOT_EDGE, spot.edge.name)
            .putFloat(SPOT_ALONG, spot.along)
            .apply()
    }

    private fun hideButton() {
        slide?.cancel()
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
            // A crew member's Play feedback has its own taker, so a review page left open
            // behind it never takes the picture as its proof.
            if (forCrew) crewCaptured.value = file else captured.value = file
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
        private const val SPOT_PREFS = "review_capture_button"
        private const val SPOT_EDGE = "edge"
        private const val SPOT_ALONG = "along"

        private val state = MutableStateFlow(State.IDLE)
        private val captured = MutableStateFlow<File?>(null)
        private val crewCaptured = MutableStateFlow<File?>(null)

        /** The camera is up for a crew's Play feedback rather than a Circle review. */
        @Volatile private var forCrew = false

        /** The last picture taken for a crew's Play feedback, until the Crew page has taken it. */
        val crewProof: StateFlow<File?> = crewCaptured

        fun takeCrewProof() {
            crewCaptured.value = null
        }

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
            crew: Boolean = false,
        ) {
            forCrew = crew
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
