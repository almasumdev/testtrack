package com.eazyverse.testtrack.ui

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/**
 * The store buttons on a real phone: Google Play comes to the front, not the phone's own store,
 * which claims the same links and has none of these apps.
 */
@RunWith(AndroidJUnit4::class)
class PlayLinksTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun topActivity(): String {
        val out = instrumentation.uiAutomation.executeShellCommand("dumpsys activity activities")
        return FileInputStream(out.fileDescriptor).bufferedReader().use { reader ->
            reader.lineSequence().firstOrNull { "topResumedActivity" in it }.orEmpty()
        }
    }

    private fun playComesUp(): Boolean {
        repeat(20) {
            if (PLAY_STORE in topActivity()) return true
            SystemClock.sleep(500)
        }
        return false
    }

    @Test
    fun getItOnPlayOpensGooglePlay() {
        openPlay(instrumentation.targetContext, "com.eazyverse.testtrack")
        assertTrue("Top was: ${topActivity()}", playComesUp())
    }
}
