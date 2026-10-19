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
import androidx.compose.ui.test.onAllNodesWithText
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
        ProvideUpdateOffer(update) {
            Column(Modifier.fillMaxSize().exchangeBackground().statusBarsPadding()) {
                TopHud(level = 3, intoLevel = 25, forNext = 100, tix = 340, update = LocalUpdateOffer.current)
                SupportGroup(onAbout = {})
            }
        }
    }

    @Test
    fun an_optional_update_is_an_icon_and_a_row_that_never_open_anything_by_themselves() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        var downloads = 0
        var restarts = 0
        shot("50-update-none") { Header(null) }
        compose.onNodeWithContentDescription("Update available").assertDoesNotExist()
        compose.onNodeWithText("Ready to download").assertDoesNotExist()

        shot("51-update-available") { Header(UpdateOffer(UpdateOffer.State.AVAILABLE, "1.6.2") { downloads++ }) }
        compose.onNodeWithText("Version 1.6.2").assertExists()
        compose.onNodeWithContentDescription("Update available").performClick()
        assertEquals(1, downloads)

        shot("52-update-downloading") { Header(UpdateOffer(UpdateOffer.State.DOWNLOADING, "1.6.2", progress = .62f)) }
        compose.onNodeWithText("62%").assertExists()

        // Ready from an earlier session: Restart shows, and no dialog opens on its own.
        shot("53-update-ready") { Header(UpdateOffer(UpdateOffer.State.READY, "1.6.2") { restarts++ }) }
        compose.onNodeWithText("Restart to update?").assertDoesNotExist()
        // The header's Restart; the row has its own, and both ask the same question.
        compose.onAllNodesWithText("Restart")[0].performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("Restart to update?").assertExists()
        assertEquals(0, restarts)
    }

    @Test
    fun a_download_started_here_asks_to_restart_when_it_lands() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        shot("54-update-restart-dialog") { Header(UpdateOffer(UpdateOffer.State.READY, "1.6.2", autoAsk = true)) }
        compose.onNodeWithText("Restart to update?").assertExists()
    }

    @Test
    fun a_forced_update_fills_the_screen() {
        compose.mainClock.autoAdvance = false
        compose.setContent { screen?.invoke() }
        shot("55-update-forced") { ForceUpdateScreen(false, true, {}, {}, {}) }
        shot("56-update-forced-downloaded") { ForceUpdateScreen(true, true, {}, {}, {}) }
    }
}
