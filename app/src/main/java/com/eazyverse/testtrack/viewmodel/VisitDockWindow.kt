package com.eazyverse.testtrack.viewmodel

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.data.overlayScreen
import com.eazyverse.testtrack.domain.DockScreen
import com.eazyverse.testtrack.domain.dockAnchor
import com.eazyverse.testtrack.domain.dockCorner
import com.eazyverse.testtrack.domain.stickTo
import com.eazyverse.testtrack.ui.DockCardGap
import com.eazyverse.testtrack.ui.DockShadowRoom
import com.eazyverse.testtrack.ui.DockSizes
import com.eazyverse.testtrack.ui.DockUi
import com.eazyverse.testtrack.ui.VisitDockContent
import com.eazyverse.testtrack.ui.signInParts
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The window the testing control floats in, over whatever app is being tested.
 *
 * It owns where the control is: dragged anywhere, then stuck to the nearest edge when let go, flat
 * on the top or bottom and upright on a side. The edge and the spot along it are kept, and the
 * corner is worked out again whenever the control changes size (opened, closed, turned), so it
 * never comes loose from its edge. The spot is remembered for the next app and the next night.
 *
 * Not focusable, so the app underneath keeps the keyboard and every touch outside the control.
 */
class VisitDockWindow(
    private val context: Context,
    private val onPlayPause: () -> Unit,
) {
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: ComposeView? = null
    private var owner: DockOwner? = null
    private var animator: ValueAnimator? = null
    private var dragging = false

    /**
     * Opening or closing is under way. The window is then held at the larger of the two sizes (an
     * exact size, set together with its place), so it never changes size mid-beat; [settle] puts it
     * back to wrapping the card once the card has finished.
     */
    private var morphing = false
    private var sizes: DockSizes? = null

    private var ui by mutableStateOf<DockUi?>(null)
    private var spot by mutableStateOf(RoundStore.spot(context))
    private var open by mutableStateOf(false)

    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Screen coordinates, status bar included, so the edges are the screen's own. No
                // limits: the shadow room around the card may reach past the screen edge.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply { gravity = Gravity.TOP or Gravity.START }

    /** What the control shows for the app on screen now. */
    fun state(
        stop: RoundStore.Stop,
        index: Int,
        total: Int,
        ranMs: Long,
        paused: Boolean,
        picture: Boolean? = null,
    ) {
        ui =
            DockUi(
                app = stop.label,
                index = index,
                total = total,
                secondsLeft = ((NightRound.VISIT_MS - ranMs + 999) / 1000).toInt().coerceAtLeast(0),
                paused = paused,
                counted = ranMs >= NightRound.VISIT_MS,
                totalSeconds = (NightRound.VISIT_MS / 1000).toInt(),
                picture = picture,
                signIn = signInParts(stop.notes),
                upright = spot.edge.upright,
                open = open,
            )
    }

    /** The transparent room round the card for its shadow, in pixels. */
    private val room: Float get() = DockShadowRoom.value * context.resources.displayMetrics.density

    /**
     * Where the card is on screen now, as left, top, right, bottom: for the device test. The card,
     * not the window, so the shadow room is left out.
     */
    internal val bounds: IntArray?
        get() =
            view?.let {
                val r = room.toInt()
                intArrayOf(params.x + r, params.y + r, params.x + it.width - r, params.y + it.height - r)
            }

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (view != null) return
        val lifecycle = DockOwner().also { it.start() }
        owner = lifecycle
        val compose =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycle)
                setViewTreeSavedStateRegistryOwner(lifecycle)
                setContent {
                    TestTrackTheme {
                        ui?.let { shown ->
                            VisitDockContent(
                                ui =
                                    dockAnchor(spot).let { (ax, ay) ->
                                        shown.copy(upright = spot.edge.upright, open = open, anchorX = ax, anchorY = ay)
                                    },
                                onToggleOpen = ::toggle,
                                onPlayPause = onPlayPause,
                                onCopy = ::copy,
                                onSizes = ::measured,
                                onSettled = ::settle,
                                onDrag = { delta ->
                                    // Not while the card is changing size: it is held still till it is done.
                                    if (morphing) return@VisitDockContent
                                    dragging = true
                                    animator?.cancel()
                                    params.x += delta.x.toInt()
                                    params.y += delta.y.toInt()
                                    update()
                                },
                                onDragEnd = ::stick,
                            )
                        }
                    }
                }
            }
        // A new size (the text changed, turned on a side) is placed back on its edge. Not while
        // opening or closing: the window then holds one size and its place, and [settle] ends it.
        compose.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            if (!dragging && !morphing && (r - l != or - ol || b - t != ob - ot)) place(v, animate = false)
        }
        view = compose
        val (x, y) = corner(compose)
        params.x = x
        params.y = y
        runCatching { windows.addView(compose, params) }
    }

    fun hide() {
        animator?.cancel()
        view?.let { runCatching { windows.removeView(it) } }
        view = null
        owner?.stop()
        owner = null
        open = false
        morphing = false
        sizes = null
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    /** Let go: stick to the edge nearest the control's centre, and remember it. */
    private fun stick() {
        val v = view ?: return
        if (!dragging) return
        dragging = false
        val screen = screen()
        spot = stickTo(params.x + v.width / 2f, params.y + v.height / 2f, screen)
        RoundStore.keepSpot(context, spot)
        // Closed once it is let go, not while it moves: shrinking under the finger would lose the
        // drag. The edge is where it was dropped; the card closes onto it.
        if (open) {
            beginMorph()
            open = false
            return
        }
        place(v, animate = true)
    }

    /** A tap on the control: open it, or close it. */
    private fun toggle() {
        beginMorph()
        open = !open
    }

    /** The card's sizes, closed and open, as last laid out. */
    private fun measured(s: DockSizes) {
        sizes = s
        // The card turned on a side, or its text grew, while the window holds one size: fit again.
        if (morphing && !dragging && (params.width != morphWidth(s) || params.height != morphHeight(s))) {
            view?.post { if (morphing) refit() }
        }
    }

    private fun morphWidth(s: DockSizes) = max(s.closedW, s.openW) + 2 * room.roundToInt()

    private fun morphHeight(s: DockSizes) = max(s.closedH, s.openH) + 2 * room.roundToInt()

    /**
     * Before the card changes size: hold the window at the larger of the two sizes, and put it where
     * a window of that size goes on this edge. Both are set in one update, so the card, which sits
     * on the docked edge inside the window, does not move on screen. Then the card can change size
     * inside it without the window being resized or moved once on the way.
     */
    private fun beginMorph() {
        val s = sizes ?: return
        if (view == null || morphing) return
        morphing = true
        animator?.cancel()
        refit(s)
    }

    private fun refit(s: DockSizes? = sizes) {
        s ?: return
        animator?.cancel()
        params.width = morphWidth(s)
        params.height = morphHeight(s)
        val (x, y) = dockCorner(spot, params.width.toFloat(), params.height.toFloat(), screen(), room)
        params.x = x.toInt()
        params.y = y.toInt()
        update()
    }

    /**
     * The card has finished: the window goes back to wrapping it, and to the place for the size it
     * ended at, in one update.
     */
    private fun settle() {
        val s = sizes ?: return
        if (view == null || !morphing) return
        morphing = false
        animator?.cancel()
        val w = (if (open) s.openW else s.closedW) + 2 * room.roundToInt()
        val h = (if (open) s.openH else s.closedH) + 2 * room.roundToInt()
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        val (x, y) = dockCorner(spot, w.toFloat(), h.toFloat(), screen(), room)
        params.x = x.toInt()
        params.y = y.toInt()
        update()
    }

    private fun place(
        v: View,
        animate: Boolean,
    ) {
        val (x, y) = corner(v)
        animator?.cancel()
        if (!animate) {
            params.x = x
            params.y = y
            update()
            return
        }
        val fromX = params.x
        val fromY = params.y
        animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val f = it.animatedValue as Float
                    params.x = (fromX + (x - fromX) * f).toInt()
                    params.y = (fromY + (y - fromY) * f).toInt()
                    update()
                }
                start()
            }
    }

    private fun corner(v: View): Pair<Int, Int> {
        val (x, y) = dockCorner(spot, v.width.toFloat(), v.height.toFloat(), screen(), room)
        return x.toInt() to y.toInt()
    }

    private fun update() {
        view?.let { runCatching { windows.updateViewLayout(it, params) } }
    }

    /** The card's old built-in gap to its window counts toward its gap to the screen edge. */
    private fun screen(): DockScreen {
        val base = overlayScreen(context)
        return base.copy(margin = base.margin + DockCardGap.value * context.resources.displayMetrics.density)
    }

    private fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Sign-in", text))
    }

    /** What a Compose view needs to run outside an activity: a lifecycle and a saved-state home. */
    private class DockOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

        fun start() {
            saved.performRestore(null)
            registry.currentState = Lifecycle.State.RESUMED
        }

        fun stop() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }
}
