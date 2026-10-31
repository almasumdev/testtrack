package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.data.EmulatorGate
import com.eazyverse.testtrack.domain.EmulatorSigns
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The block screen, and the check itself on a phone somebody says is real. */
@RunWith(AndroidJUnit4::class)
class EmulatorBlockTest {
    @get:Rule
    val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Run with `-e realPhone true` on a real phone: it must have no sign at all. */
    @Test
    fun aRealPhoneShowsNoSign() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("realPhone") == "true")
        assertEquals(emptyList<String>(), EmulatorSigns.of(EmulatorGate.read(instrumentation.targetContext)))
    }

    @Test
    fun theBlockScreenSaysWhyAndHowToReachUs() {
        compose.setContent { TestTrackTheme { EmulatorBlockScreen(listOf("hardware ranchu")) } }
        compose.onNodeWithText("TestTrack needs a real phone").assertIsDisplayed()
        compose.onNodeWithText("This is a real phone? Tell us").assertIsDisplayed()
        compose.waitForIdle()
        val picture = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.filesDir, "emulator-block.png").outputStream().use {
            picture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
