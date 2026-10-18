package com.eazyverse.testtrack.viewmodel

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.domain.DockEdge
import com.eazyverse.testtrack.domain.DockSpot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The testing control on a real screen: dragged with real swipes, it sticks to the edge it is let
 * go nearest and keeps that edge, whatever the phone. Every tap and swipe is aimed at where the
 * control actually is. Pictures of each step go to files/gallery/dock for a person to look at.
 */
@RunWith(AndroidJUnit4::class)
class VisitDockWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val out = File(context.getExternalFilesDir(null), "gallery/dock").apply { mkdirs() }
    private lateinit var window: VisitDockWindow

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun shot(name: String) {
        Thread.sleep(800)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun box(): IntArray {
        var b: IntArray? = null
        instrumentation.runOnMainSync { b = window.bounds }
        return requireNotNull(b)
    }

    /** A point inside the control: across from its left by [fx] of its width, down by [fy]. */
    private fun at(
        fx: Float,
        fy: Float,
    ): Pair<Int, Int> {
        val (l, t, r, b) = box().toList()
        return (l + (r - l) * fx).toInt() to (t + (b - t) * fy).toInt()
    }

    private fun tap(
        fx: Float,
        fy: Float,
    ) {
        val (x, y) = at(fx, fy)
        shell("input tap $x $y")
        Thread.sleep(500)
    }

    private fun dragTo(
        x: Int,
        y: Int,
    ) {
        // From the control's ring, which only drags.
        val (sx, sy) = at(0.3f, 0.2f)
        shell("input swipe $sx $sy $x $y 500")
        Thread.sleep(600)
    }

    @Test
    fun it_sticks_to_the_edge_it_is_let_go_nearest() {
        shell("input keyevent KEYCODE_HOME")
        Thread.sleep(800)
        RoundStore.keepSpot(context, DockSpot(DockEdge.RIGHT, 0.5f))
        val stop = RoundStore.Stop("s1", "com.example.quicknotes", "Quick Notes", "Username: demo@quicknotes.app\nPassword: notes2026")
        instrumentation.runOnMainSync {
            window = VisitDockWindow(context) {}
            window.state(stop, 1, 8, 4_000, paused = false)
            window.show()
        }
        val m = context.resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        shot("1-right-closed")

        dragTo(w / 2, (h * 0.08f).toInt())
        shot("2-top-closed")
        assertEquals(DockEdge.TOP, RoundStore.spot(context).edge)

        tap(0.45f, 0.5f)
        shot("3-top-open")
        assertTrue("open is wider than closed", box().let { it[2] - it[0] } > w / 2)

        // The Username line, a little under the middle of the open card.
        tap(0.5f, 0.42f)
        shot("4-top-open-copied")

        // Dragged while open: it closes when let go and sticks to the side it was dropped by.
        dragTo((w * 0.05f).toInt(), h / 2)
        shot("5-left-closed")
        assertEquals(DockEdge.LEFT, RoundStore.spot(context).edge)

        tap(0.5f, 0.15f)
        shot("6-left-open")

        dragTo(w / 2, (h * 0.93f).toInt())
        shot("7-bottom-closed")
        assertEquals(DockEdge.BOTTOM, RoundStore.spot(context).edge)

        instrumentation.runOnMainSync { window.state(stop, 1, 8, NightRound.VISIT_MS, paused = false) }
        tap(0.4f, 0.5f)
        shot("8-bottom-counted-open")
        instrumentation.runOnMainSync { window.hide() }
    }
}
