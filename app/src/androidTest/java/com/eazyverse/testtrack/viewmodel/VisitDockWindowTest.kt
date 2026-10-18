package com.eazyverse.testtrack.viewmodel

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.RoundStore
import com.eazyverse.testtrack.domain.DockEdge
import com.eazyverse.testtrack.domain.DockSpot
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The testing control on a real screen: dragged with real swipes, it sticks to the edge it is let
 * go nearest and keeps that edge. Pictures of each step go to files/gallery/dock for a person.
 */
@RunWith(AndroidJUnit4::class)
class VisitDockWindowTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val out = File(context.getExternalFilesDir(null), "gallery/dock").apply { mkdirs() }

    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).close()

    private fun shot(name: String) {
        Thread.sleep(700)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun it_sticks_to_the_edge_it_is_let_go_nearest() {
        shell("appops set ${context.packageName} SYSTEM_ALERT_WINDOW allow")
        shell("input keyevent KEYCODE_HOME")
        RoundStore.keepSpot(context, DockSpot(DockEdge.RIGHT, 0.5f))
        val stop = RoundStore.Stop("s1", "com.example.quicknotes", "Quick Notes", "Username: demo@quicknotes.app\nPassword: notes2026")
        lateinit var window: VisitDockWindow
        instrumentation.runOnMainSync {
            window = VisitDockWindow(context) {}
            window.state(stop, 1, 8, 4_000, paused = false)
            window.show()
        }
        val m = context.resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val dp = m.density
        shot("1-right-closed")

        // From the right edge, a little left of it and at its middle, to near the top.
        shell("input swipe ${w - (30 * dp).toInt()} ${h / 2} ${w / 2} ${(120 * dp).toInt()} 400")
        shot("2-top-closed")
        assertEquals(DockEdge.TOP, RoundStore.spot(context).edge)

        // Its middle along the top: tap to open it.
        shell("input tap ${w / 2} ${(92 * dp).toInt()}")
        shot("3-top-open")

        // Copy the username: the line turns green for a moment.
        shell("input tap ${w / 2} ${(160 * dp).toInt()}")
        Thread.sleep(150)
        shot("4-top-open-copied")

        // Close it, then drag it to the left side.
        shell("input tap ${w / 2} ${(92 * dp).toInt()}")
        Thread.sleep(400)
        shell("input swipe ${w / 2} ${(92 * dp).toInt()} ${(40 * dp).toInt()} ${h / 2} 400")
        shot("5-left-closed")
        assertEquals(DockEdge.LEFT, RoundStore.spot(context).edge)

        shell("input tap ${(28 * dp).toInt()} ${h / 2}")
        shot("6-left-open")

        // And to the bottom.
        shell("input tap ${(28 * dp).toInt()} ${h / 2 - (60 * dp).toInt()}")
        Thread.sleep(400)
        shell("input swipe ${(28 * dp).toInt()} ${h / 2} ${w / 2} ${h - (150 * dp).toInt()} 400")
        shot("7-bottom-closed")
        assertEquals(DockEdge.BOTTOM, RoundStore.spot(context).edge)

        instrumentation.runOnMainSync {
            window.state(stop, 1, 8, NightRound.VISIT_MS, paused = false)
        }
        shot("8-bottom-counted")
        instrumentation.runOnMainSync { window.hide() }
    }
}
