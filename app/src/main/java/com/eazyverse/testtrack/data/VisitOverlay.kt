package com.eazyverse.testtrack.data

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

/**
 * What a tester sees while an app under test is on screen.
 *
 * A round used to happen entirely behind the app it was visiting: it opened, something was
 * captured at a moment nobody could predict, and control came back a while later. It worked and it
 * gave the person holding the phone nothing to look at, so the only way to know it was working was
 * to wait and find out.
 *
 * This is a ring that empties over the visit, the app's name, its place in the round, and a button
 * to move on early. Both halves of that matter: the ring means somebody who is watching knows how
 * long is left, and the button means somebody who has seen enough does not have to wait for it.
 *
 * It draws over another app, which needs `SYSTEM_ALERT_WINDOW`, and that grant is already asked
 * for on the group screen because switching apps needs it too. Where it has not been given there
 * is simply no overlay: [show] does nothing and the round runs exactly as it did before, which is
 * the same trade the switching already makes.
 *
 * It is in the screenshot, deliberately. Hiding it for the grab and putting it back is a frame of
 * flicker on somebody else's app and a race with the capture, to remove something that gives the
 * picture a timestamp and a name.
 */
class VisitOverlay(private val context: Context) {

    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: View? = null
    private var ring: RingView? = null
    private var animator: ValueAnimator? = null

    val allowed: Boolean get() = Settings.canDrawOverlays(context)

    /**
     * Puts the ring up for one visit.
     *
     * [onNext] is what the button does, and the caller decides what that means — here it is only a
     * press. The ring is wall clock rather than frame driven, so a slow app does not slow it down.
     */
    fun show(label: String, position: String, millis: Long, onNext: () -> Unit) {
        if (!allowed) return
        hide()

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val ringView = RingView(context, density).also { ring = it }

        val name = TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        val place = TextView(context).apply {
            text = position
            setTextColor(Color.parseColor("#99A1B6"))
            textSize = 11f
        }
        val next = TextView(context).apply {
            text = "Next"
            setTextColor(Color.parseColor("#241E52"))
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(14), dp(7), dp(14), dp(7))
            background = pill(Color.parseColor("#A6A0FA"), dp(16).toFloat())
            setOnClickListener { onNext() }
        }

        val words = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            addView(place)
        }

        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = pill(Color.parseColor("#E60B0F19"), dp(26).toFloat())
            addView(ringView, LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(words, LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            })
            addView(next)
        }

        val host = FrameLayout(context).apply {
            setPadding(dp(16), 0, dp(16), 0)
            addView(bar, FrameLayout.LayoutParams(-1, -2))
        }

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            // Not focusable, so the app underneath keeps the keyboard and every touch that is not
            // on the bar itself. NOT_TOUCH_MODAL is what lets those touches through.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = dp(48)
        }

        runCatching {
            windows.addView(host, params)
            view = host
        }

        animator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = millis
            addUpdateListener { ringView.setFraction(it.animatedValue as Float) }
            start()
        }
    }

    fun hide() {
        animator?.cancel()
        animator = null
        ring = null
        view?.let { runCatching { windows.removeView(it) } }
        view = null
    }

    private fun pill(colour: Int, radius: Float) =
        android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(colour)
        }

    /** A ring that empties clockwise from the top. */
    private class RingView(context: Context, private val density: Float) : View(context) {

        private var fraction = 1f

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

        fun setFraction(value: Float) {
            fraction = value
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val inset = fill.strokeWidth / 2f
            val size = min(width, height).toFloat()
            val box = RectF(inset, inset, size - inset, size - inset)
            canvas.drawArc(box, 0f, 360f, false, track)
            canvas.drawArc(box, -90f, 360f * fraction, false, fill)
        }
    }
}
