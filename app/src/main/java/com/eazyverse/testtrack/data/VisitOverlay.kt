package com.eazyverse.testtrack.data

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.min

/**
 * What a tester sees while an app under test is on screen.
 *
 * One circle, and nothing else. The ring around the edge empties over the visit and the button in
 * the middle is the only control: pause while it is running, play to set it going again, and play
 * once more at the end to move on.
 *
 * It used to be a bar carrying the app's name and its place in the round as well. That was more
 * honest about what was happening and it was also a strip of somebody else's app covered over for
 * twenty seconds at a time, thirteen times an evening. The name is on the screen underneath it
 * already, which is the whole point of the visit.
 *
 * Drawing over another app needs `SYSTEM_ALERT_WINDOW`, and that grant is already asked for on the
 * group screen because switching apps needs it too. Where it has not been given there is simply no
 * overlay: [show] does nothing and the round runs exactly as it did before.
 *
 * It is in the screenshot, deliberately. Hiding it for the grab and putting it back is a frame of
 * flicker on somebody else's app and a race with the capture, to remove something that gives the
 * picture a timestamp.
 */
class VisitOverlay(private val context: Context) {

    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var dial: Dial? = null
    private var animator: ValueAnimator? = null

    val allowed: Boolean get() = Settings.canDrawOverlays(context)

    /**
     * What the middle of the circle is offering.
     *
     * Two, because there are only ever two things to say. [PLAY] covers both "let it carry on by
     * itself" and, once the ring has emptied, "move on now" — which is the same intent twice and
     * reads as one button rather than a control that changes its mind at the end.
     */
    enum class Mode { PAUSE, PLAY }

    /**
     * Puts the dial up for one visit.
     *
     * The ring is wall clock rather than frame driven, so an app that stutters does not slow it
     * down. [onTap] is a press and nothing more: what a press means belongs to the service, which
     * is the only thing that knows whether the visit still has time on it.
     */
    fun show(millis: Long, onTap: () -> Unit) {
        if (!allowed) return
        hide()

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val view = Dial(context, density).apply {
            setOnClickListener { onTap() }
            mode(Mode.PAUSE)
        }
        dial = view

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            dp(SIZE), dp(SIZE),
            type,
            // Not focusable, so the app underneath keeps the keyboard and every touch that is not
            // on the circle itself. NOT_TOUCH_MODAL is what lets those through, and the window is
            // now only as big as the circle, so almost the whole screen is "not on it".
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(80)
        }

        runCatching {
            windows.addView(view, params)
        }.onFailure { dial = null }

        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = millis
            addUpdateListener { view.fraction(it.animatedValue as Float) }
            start()
        }
    }

    fun action(mode: Mode) {
        dial?.mode(mode)
    }

    fun hide() {
        animator?.cancel()
        animator = null
        dial?.let { runCatching { windows.removeView(it) } }
        dial = null
    }

    /** A ring that empties clockwise from the top, around a play or pause glyph. */
    private class Dial(context: Context, private val density: Float) : View(context) {

        private var left = 1f
        private var mode = Mode.PAUSE

        private val ground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E60B0F19")
        }
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            color = Color.parseColor("#33FFFFFF")
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * density
            strokeCap = Paint.Cap.ROUND
            color = Color.parseColor("#A6A0FA")
        }
        private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EEF1F8")
        }

        private val wedge = Path()

        fun fraction(value: Float) {
            left = value
            invalidate()
        }

        fun mode(value: Mode) {
            mode = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val size = min(width, height).toFloat()
            val middle = size / 2f
            canvas.drawCircle(middle, middle, middle, ground)

            // Inset by a whole stroke rather than half, so the ring sits inside the disc instead of
            // straddling its edge, where the outer half would be drawn against another app.
            val inset = fill.strokeWidth
            val box = RectF(inset, inset, size - inset, size - inset)
            canvas.drawArc(box, 0f, 360f, false, track)
            if (left > 0f) canvas.drawArc(box, -90f, 360f * left, false, fill)

            if (mode == Mode.PAUSE) drawPause(canvas, middle) else drawPlay(canvas, middle)
        }

        private fun drawPause(canvas: Canvas, middle: Float) {
            val bar = 3.5f * density
            val tall = 13f * density
            val gap = 4f * density
            val top = middle - tall / 2f
            val corner = bar / 2f
            canvas.drawRoundRect(
                middle - gap / 2f - bar, top, middle - gap / 2f, top + tall, corner, corner, glyph
            )
            canvas.drawRoundRect(
                middle + gap / 2f, top, middle + gap / 2f + bar, top + tall, corner, corner, glyph
            )
        }

        private fun drawPlay(canvas: Canvas, middle: Float) {
            val tall = 14f * density
            val wide = 12f * density
            // Nudged right, because a triangle centred on its bounding box reads as sitting left
            // of centre. The eye balances it against the centroid, which is a third of the way in.
            val start = middle - wide / 2f + 1.5f * density
            wedge.reset()
            wedge.moveTo(start, middle - tall / 2f)
            wedge.lineTo(start + wide, middle)
            wedge.lineTo(start, middle + tall / 2f)
            wedge.close()
            canvas.drawPath(wedge, glyph)
        }
    }

    private companion object {
        /** Big enough to be an easy target on top of another app, small enough to ignore. */
        const val SIZE = 56
    }
}
