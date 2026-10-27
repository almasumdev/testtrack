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
import com.eazyverse.testtrack.domain.dockCorner
import com.eazyverse.testtrack.domain.stickTo
import com.eazyverse.testtrack.ui.DockUi
import com.eazyverse.testtrack.ui.VisitDockContent
import com.eazyverse.testtrack.ui.signInParts
import com.eazyverse.testtrack.ui.theme.TestTrackTheme

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

    private var ui by mutableStateOf<DockUi?>(null)
    private var spot by mutableStateOf(RoundStore.spot(context))
    private var open by mutableStateOf(false)

    private val params =
        WindowManager
            .LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Screen coordinates, status bar included, so the edges are the screen's own.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
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

    /** Where the control is on screen now, as left, top, right, bottom: for the device test. */
    internal val bounds: IntArray?
        get() = view?.let { intArrayOf(params.x, params.y, params.x + it.width, params.y + it.height) }

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
                                ui = shown.copy(upright = spot.edge.upright, open = open),
                                onToggleOpen = { open = !open },
                                onPlayPause = onPlayPause,
                                onCopy = ::copy,
                                onDrag = { delta ->
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
        // A new size (opened, closed, turned on a side) is placed back on its edge.
        compose.addOnLayoutChangeListener { v, l, t, r, b, ol, ot, or, ob ->
            if (!dragging && (r - l != or - ol || b - t != ob - ot)) place(v, animate = false)
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
    }

    /** Let go: stick to the edge nearest the control's centre, and remember it. */
    private fun stick() {
        val v = view ?: return
        dragging = false
        val screen = screen()
        spot = stickTo(params.x + v.width / 2f, params.y + v.height / 2f, screen)
        RoundStore.keepSpot(context, spot)
        // Closed once it is let go, not while it moves: shrinking under the finger would lose the
        // drag. The edge is where it was dropped; its small size is placed on it when laid out.
        if (open) {
            open = false
            return
        }
        place(v, animate = true)
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
        val (x, y) = dockCorner(spot, v.width.toFloat(), v.height.toFloat(), screen())
        return x.toInt() to y.toInt()
    }

    private fun update() {
        view?.let { runCatching { windows.updateViewLayout(it, params) } }
    }

    private fun screen(): DockScreen = overlayScreen(context)

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
