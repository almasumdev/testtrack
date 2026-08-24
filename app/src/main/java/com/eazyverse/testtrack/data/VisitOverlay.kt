package com.eazyverse.testtrack.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import kotlin.math.abs

/**
 * What a tester sees while an app under test is on screen.
 *
 * A capsule. The ring and its play/pause glyph sit in the top half exactly as they always have —
 * emptying over the visit, the only control being pause while it runs and play to set it going
 * again or to move on once it is empty. Underneath, only for a visit whose app asked for a test
 * account or a sign-in code (see [Repo] and `TestApp.notes`), a second button copies that text to
 * the clipboard. Getting past an app's front door used to mean leaving TestTrack to go read it —
 * this is the same information, one tap away, without leaving the app under test at all.
 *
 * It used to be a bar carrying the app's name and its place in the round as well. That was more
 * honest about what was happening and it was also a strip of somebody else's app covered over for
 * twenty seconds at a time, thirteen times an evening. The name is on the screen underneath it
 * already, which is the whole point of the visit.
 *
 * Draggable like a chat head, anywhere on screen — but only draggable. There is no target to drop
 * it on and no gesture that dismisses it; the round decides when it goes away, not a thumb. It
 * remembers where it was left for as long as this object lives, so pulling it clear of one app's
 * content does not undo itself the moment the next visit starts.
 *
 * Drawing over another app needs `SYSTEM_ALERT_WINDOW`, and that grant is already asked for on the
 * group screen because switching apps needs it too. Where it has not been given there is simply no
 * overlay: [show] does nothing and the round runs exactly as it did before.
 *
 * It is in the screenshot, deliberately. Hiding it for the grab and putting it back is a frame of
 * flicker on somebody else's app and a race with the capture, to remove something that gives the
 * picture a timestamp.
 */
class VisitOverlay(
    private val context: Context,
    private val handler: Handler,
) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubble: Bubble? = null
    private var ticker: Runnable? = null

    /** Where the tester last dragged it to, so the next visit reopens there instead of the start. */
    private var lastX: Int? = null
    private var lastY: Int? = null

    val allowed: Boolean get() = Settings.canDrawOverlays(context)

    /**
     * What the middle of the ring is offering.
     *
     * Two, because there are only ever two things to say. [PLAY] covers both "let it carry on by
     * itself" and, once the ring has emptied, "move on now" — which is the same intent twice and
     * reads as one button rather than a control that changes its mind at the end.
     */
    enum class Mode { PAUSE, PLAY }

    /**
     * Puts the bubble up for one visit.
     *
     * The ring is wall clock rather than frame driven, so an app that stutters does not slow it
     * down. [onTap] is a press and nothing more: what a press means belongs to the service, which
     * is the only thing that knows whether the visit still has time on it. [copyText] is the app's
     * own sign-in note, or null for the ordinary case of an app that needs none — its presence is
     * what decides whether the capsule grows a second button at all.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun show(
        millis: Long,
        copyText: String?,
        onTap: () -> Unit,
    ) {
        if (!allowed) return
        hide()

        val metrics = context.resources.displayMetrics
        val density = metrics.density

        fun dp(value: Int) = (value * density).toInt()

        val view =
            Bubble(context, density, copyText?.takeIf { it.isNotBlank() }).apply {
                mode(Mode.PAUSE)
            }
        bubble = view

        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val lp =
            WindowManager
                .LayoutParams(
                    view.widthPx,
                    view.heightPx,
                    type,
                    // Not focusable, so the app underneath keeps the keyboard and every touch that is not
                    // on the capsule itself. NOT_TOUCH_MODAL is what lets those through.
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    // START rather than END even though the default sits on the right: free-drag tracks
                    // x/y as plain left-origin offsets (see the touch listener below), and mixing that
                    // with END-relative coordinates would have the capsule drift the wrong way the first
                    // time it was dragged.
                    gravity = Gravity.TOP or Gravity.START
                    x = lastX ?: (metrics.widthPixels - view.widthPx - dp(16))
                    // A starting point only, chosen for a one-handed hold: not the top, not the very
                    // bottom, roughly where a thumb rests mid-grip. A drag remembers where it lands (see
                    // [lastX]/[lastY]) so this default is only ever seen once per session.
                    y = lastY ?: (metrics.heightPixels * 0.62f).toInt()
                }

        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        var dragging = false
        val slop = dp(8)

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = lp.x
                    downY = lp.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        lp.x = (downX + dx.toInt()).coerceIn(0, metrics.widthPixels - view.widthPx)
                        lp.y = (downY + dy.toInt()).coerceIn(0, metrics.heightPixels - view.heightPx)
                        runCatching { windows.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        lastX = lp.x
                        lastY = lp.y
                    } else if (view.hitCopy(event.x, event.y)) {
                        view.copy()
                    } else {
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        lastX = lp.x
                        lastY = lp.y
                    }
                    true
                }
                else -> false
            }
        }

        runCatching {
            windows.addView(view, lp)
        }.onFailure { bubble = null }

        tick(view, deadline = SystemClock.elapsedRealtime() + millis, total = millis)
    }

    /**
     * A self-correcting tick, in place of the [android.animation.ValueAnimator] this used to be.
     *
     * An animator ties its updates to Choreographer frame callbacks on this process's main thread,
     * and an overlay window sitting on top of whatever app the tester actually has in front of them
     * is exactly the kind of view some OEM power managers deprioritise those callbacks for once
     * this process is no longer the one visibly in front — which is every single visit by design.
     * The ring could stop moving and just sit there, part way round, for the rest of the twenty
     * seconds. Recomputing the fraction from the deadline on every tick instead of accumulating
     * from the last one means a late or skipped tick only ever costs a stutter: whichever tick does
     * run next still lands on the correct fraction, because it was never carrying forward an error
     * from the tick before it.
     */
    private fun tick(
        view: Bubble,
        deadline: Long,
        total: Long,
    ) {
        val now = SystemClock.elapsedRealtime()
        val fraction = ((deadline - now).toFloat() / total).coerceIn(0f, 1f)
        view.fraction(fraction)
        if (fraction <= 0f) return
        val next = Runnable { tick(view, deadline, total) }
        ticker = next
        handler.postDelayed(next, TICK_MS)
    }

    fun action(mode: Mode) {
        bubble?.mode(mode)
    }

    fun hide() {
        ticker?.let { handler.removeCallbacks(it) }
        ticker = null
        bubble?.let { runCatching { windows.removeView(it) } }
        bubble = null
    }

    /**
     * The capsule: a ring and its play/pause glyph on top, an optional copy button underneath.
     *
     * Without [copyText] the bottom section simply is not there — height collapses back to exactly
     * the width, which draws as a plain circle, indistinguishable from a visit with nothing to
     * copy. With it, the capsule grows straight down from the same top cap, so the ring never moves
     * and a tester's eye does not have to find it again.
     */
    private class Bubble(
        context: Context,
        private val density: Float,
        private val copyText: String?,
    ) : View(context) {
        private fun dp(v: Int) = (v * density).toInt()

        private val ringSize = dp(RING_SIZE)
        private val padH = dp(PAD_H)
        private val padTop = dp(PAD_TOP)
        private val gap = dp(GAP)
        private val buttonSize = dp(BUTTON_SIZE)
        private val padBottom = dp(PAD_BOTTOM)
        private val hasCopy = copyText != null

        val widthPx = ringSize + padH * 2
        val heightPx =
            if (hasCopy) padTop + ringSize + gap + buttonSize + padBottom else padTop + ringSize + padTop

        private val ringCenterX get() = widthPx / 2f
        private val ringCenterY get() = padTop + ringSize / 2f
        private val buttonCenterY get() = padTop + ringSize + gap + buttonSize / 2f

        private var left = 1f
        private var mode = Mode.PAUSE

        private val ground =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E60B0F19")
            }
        private val track =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                color = Color.parseColor("#33FFFFFF")
            }
        private val fill =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * density
                strokeCap = Paint.Cap.ROUND
                color = Color.parseColor("#A6A0FA")
            }
        private val glyph =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#EEF1F8")
            }
        private val buttonGround =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#33FFFFFF")
            }
        private val copyStroke =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.6f * density
                color = Color.parseColor("#EEF1F8")
            }

        private val wedge = Path()
        private val capsule = RectF()
        private val ringBox = RectF()
        private val copyBack = RectF()
        private val copyFront = RectF()

        fun fraction(value: Float) {
            left = value
            invalidate()
        }

        fun mode(value: Mode) {
            mode = value
            invalidate()
        }

        /** Whether a tap at this view-local point landed on the copy button, not the ring. */
        fun hitCopy(
            x: Float,
            y: Float,
        ): Boolean {
            if (!hasCopy) return false
            val r = buttonSize / 2f
            val dx = x - ringCenterX
            val dy = y - buttonCenterY
            return dx * dx + dy * dy <= r * r
        }

        fun copy() {
            val text = copyText ?: return
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            manager?.setPrimaryClip(ClipData.newPlainText("TestTrack", text))
            // Android 13+ already raises its own confirmation chip for a clipboard write. Below
            // that there is none, so a toast is the only sign the tap did anything at all.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onDraw(canvas: Canvas) {
            val radius = widthPx / 2f
            capsule.set(0f, 0f, widthPx.toFloat(), heightPx.toFloat())
            canvas.drawRoundRect(capsule, radius, radius, ground)

            val ringRadius = ringSize / 2f
            val cx = ringCenterX
            val cy = ringCenterY

            // Inset by a whole stroke rather than half, so the ring sits inside the disc instead of
            // straddling its edge, where the outer half would be drawn against another app.
            val inset = fill.strokeWidth
            ringBox.set(cx - ringRadius + inset, cy - ringRadius + inset, cx + ringRadius - inset, cy + ringRadius - inset)
            canvas.drawArc(ringBox, 0f, 360f, false, track)
            if (left > 0f) canvas.drawArc(ringBox, -90f, 360f * left, false, fill)

            if (mode == Mode.PAUSE) drawPause(canvas, cx, cy) else drawPlay(canvas, cx, cy)

            if (hasCopy) {
                canvas.drawCircle(ringCenterX, buttonCenterY, buttonSize / 2f, buttonGround)
                drawCopyGlyph(canvas, ringCenterX, buttonCenterY)
            }
        }

        private fun drawPause(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            val bar = 3.5f * density
            val tall = 13f * density
            val gap = 4f * density
            val top = cy - tall / 2f
            val corner = bar / 2f
            canvas.drawRoundRect(
                cx - gap / 2f - bar,
                top,
                cx - gap / 2f,
                top + tall,
                corner,
                corner,
                glyph,
            )
            canvas.drawRoundRect(
                cx + gap / 2f,
                top,
                cx + gap / 2f + bar,
                top + tall,
                corner,
                corner,
                glyph,
            )
        }

        private fun drawPlay(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            val tall = 14f * density
            val wide = 12f * density
            // Nudged right, because a triangle centred on its bounding box reads as sitting left
            // of centre. The eye balances it against the centroid, which is a third of the way in.
            val start = cx - wide / 2f + 1.5f * density
            wedge.reset()
            wedge.moveTo(start, cy - tall / 2f)
            wedge.lineTo(start + wide, cy)
            wedge.lineTo(start, cy + tall / 2f)
            wedge.close()
            canvas.drawPath(wedge, glyph)
        }

        /** Two overlapping sheets, the standard shorthand for "copy" — drawn, not imported. */
        private fun drawCopyGlyph(
            canvas: Canvas,
            cx: Float,
            cy: Float,
        ) {
            val w = 10f * density
            val h = 12f * density
            val corner = 2f * density
            val shift = 3f * density

            copyBack.set(cx - w / 2f + shift, cy - h / 2f - shift, cx + w / 2f + shift, cy + h / 2f - shift)
            canvas.drawRoundRect(copyBack, corner, corner, copyStroke)

            copyFront.set(cx - w / 2f - shift, cy - h / 2f + shift, cx + w / 2f - shift, cy + h / 2f + shift)
            canvas.drawRoundRect(copyFront, corner, corner, glyph)
        }
    }

    private companion object {
        /** Big enough to be an easy target on top of another app, small enough to ignore. */
        const val RING_SIZE = 56
        const val PAD_H = 10
        const val PAD_TOP = 10
        const val GAP = 8
        const val BUTTON_SIZE = 40
        const val PAD_BOTTOM = 10
        const val TICK_MS = 100L
    }
}
