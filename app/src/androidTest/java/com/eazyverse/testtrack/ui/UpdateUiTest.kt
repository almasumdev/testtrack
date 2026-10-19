package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.ui.theme.exchangeBackground
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An update as somebody meets it: the header's button for an optional one, Restart once it is
 * downloaded, and the full screen for a forced one. Each state is also saved as a picture under
 * the app's external files, in gallery/, for review.
 */
@RunWith(AndroidJUnit4::class)
class UpdateUiTest {
    @get:Rule
    val compose = createComposeRule()

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)

    private val out: File by lazy {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), "gallery").apply { mkdirs() }
    }

    private fun shot(
        name: String,
        content: @Composable () -> Unit,
    ) {
        screen = { key(name) { TestTrackTheme { content() } } }
        // The header's robot never stops moving, so the clock is moved by hand.
        compose.mainClock.advanceTimeBy(1_500)
        Thread.sleep(400)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun Header(update: UpdateOffer?) {
        Column(Modifier.fillMaxSize().exchangeBackground().statusBarsPadding()) {
            TopHud(level = 3, intoLevel = 25, forNext = 100, tix = 340, update = update)
        }
    }

    @Test
    fun an_optional_update_is_a_button_that_becomes_restart() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        var taps = 0
        shot("50-update-none") { Header(null) }
        compose.onNodeWithContentDescription("Update available").assertDoesNotExist()

        shot("51-update-available") { Header(UpdateOffer(UpdateOffer.State.AVAILABLE) { taps++ }) }
        compose.onNodeWithContentDescription("Update available").performClick()
        assertEquals(1, taps)

        shot("52-update-restart") { Header(UpdateOffer(UpdateOffer.State.READY) { taps++ }) }
        compose.onNodeWithText("Restart").performClick()
        assertEquals(2, taps)
    }

    @Test
    fun a_forced_update_fills_the_screen() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        shot("53-update-forced") { ForceUpdateScreen(false, true, {}, {}, {}) }
        shot("54-update-forced-downloaded") { ForceUpdateScreen(true, true, {}, {}, {}) }
    }
}
