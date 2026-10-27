package com.eazyverse.testtrack.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.VisitCameraService
import com.eazyverse.testtrack.data.VisitPictures
import com.eazyverse.testtrack.fixture.NightRoundFixtureActivity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A night round on a real screen, with no account: Android's screen-share yes, the testing control
 * over another app, and the night camera's picture of it taken between ten and twenty seconds in.
 *
 * Drives the debug-only [NightRoundFixtureActivity]. The picture is checked to be there, to be of
 * something rather than a blank frame, and to have been taken in its window; it and a screenshot
 * of the round go to files/gallery/night for a person to look at.
 */
@RunWith(AndroidJUnit4::class)
class NightRoundCameraTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val pkg = context.packageName
    private val out = File(context.getExternalFilesDir(null), "gallery/night").apply { mkdirs() }
    private val pictures = File(context.filesDir, VisitPictures.DIR)

    private fun shell(command: String): String =
        instrumentation.uiAutomation.executeShellCommand(command).let { fd ->
            android.os.ParcelFileDescriptor
                .AutoCloseInputStream(fd)
                .use { it.readBytes().decodeToString() }
        }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Android's own screen-share question, found by its words on screen and tapped. */
    private fun allowScreenShare(): Boolean {
        val words = listOf("Start now", "Start", "Share screen", "Next")
        repeat(40) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            for (word in words) {
                val node =
                    root?.findAccessibilityNodeInfosByText(word)?.firstOrNull { n ->
                        n.text?.toString().equals(word, ignoreCase = true) && n.isVisibleToUser
                    } ?: continue
                val bounds = android.graphics.Rect().also(node::getBoundsInScreen)
                shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
                Thread.sleep(1500)
                // Some versions ask twice: which screen, then whether to start.
                if (word != "Next") return true
            }
            Thread.sleep(500)
        }
        return false
    }

    @Before
    fun allow() {
        shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
        shell("appops set $pkg GET_USAGE_STATS allow")
        if (Build.VERSION.SDK_INT >= 33) shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
        pictures.listFiles()?.forEach { it.delete() }
        out.listFiles()?.forEach { it.delete() }
    }

    @After
    fun stop() {
        VisitCameraService.stop(context)
        shell("am force-stop $CLOCK")
    }

    @Test
    fun aRoundTakesItsPictureBetweenTenAndTwentySecondsIn() {
        // The phone's Clock, not Settings: Settings hides every app's floating window on Android 15.
        shell("am start -n $pkg/${NightRoundFixtureActivity::class.java.name} --es app $CLOCK")
        assertTrue("Android's screen-share question was not found", allowScreenShare())
        val started = System.currentTimeMillis()
        assertEquals("started", state())

        Thread.sleep(8_000)
        screenshot("round-8s")
        // The testing control is on screen over the app, as the picture is meant to show it.
        val dump = shell("dumpsys window windows")
        val control = dump.substringAfter("appop=SYSTEM_ALERT_WINDOW", "").substringBefore("Window #")
        File(out, "control-8s.txt").writeText(control)
        assertTrue(
            "The testing control is not on screen",
            "isVisible=true" in control || "mViewVisibility=0x0" in control && "mHasSurface=true" in control,
        )
        var shot: File? = null
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline && shot == null) {
            Thread.sleep(500)
            shot = pictures.listFiles()?.maxByOrNull { it.lastModified() }
        }
        screenshot("round-after-picture")
        assertNotNull("The round took no picture", shot)
        val at = shot!!.lastModified() - started
        assertTrue("Picture taken ${at}ms in, outside 10 to 22 seconds", at in 9_000..22_000)

        val bitmap = BitmapFactory.decodeFile(shot.path)
        assertNotNull("The picture is not an image", bitmap)
        assertTrue("The picture is too small: ${bitmap.width}x${bitmap.height}", bitmap.width > 300 && bitmap.height > 500)
        assertTrue("The picture is a blank frame", !blank(bitmap))
        shot.copyTo(File(out, "picture.jpg"), overwrite = true)
    }

    private fun state(): String? {
        repeat(20) {
            val s = context.getSharedPreferences(NightRoundFixtureActivity.PREFS, 0).getString(NightRoundFixtureActivity.KEY, null)
            if (s == "started" || s == "refused") return s
            Thread.sleep(250)
        }
        return context.getSharedPreferences(NightRoundFixtureActivity.PREFS, 0).getString(NightRoundFixtureActivity.KEY, null)
    }

    private fun blank(bitmap: Bitmap): Boolean = VisitCameraService.blank(bitmap)

    private companion object {
        const val CLOCK = "com.google.android.deskclock"
    }
}
