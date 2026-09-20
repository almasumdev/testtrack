package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The six pages a signed-out reader lands on.
 *
 * Every one of them is a page nobody signed in ever sees, which is exactly why they went wrong
 * unnoticed for so long: four of them claimed to have checked and found nothing. They are drawn
 * here one at a time and written out, because a mark built from six solid shapes is a thing to
 * look at rather than a thing to assert about.
 */
@RunWith(AndroidJUnit4::class)
class GatedTest {
    @get:Rule
    val compose = createComposeRule()

    private fun keep(
        name: String,
        bitmap: Bitmap,
    ) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "gated")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun shoot(
        name: String,
        heading: String,
        body: String,
        mark: @Composable () -> Unit,
    ) {
        compose.setContent {
            TestTrackTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Gated(heading = heading, body = body, onSignIn = {}, mark = mark)
                }
            }
        }
        compose.onNodeWithText(heading).assertIsDisplayed()
        compose.onNodeWithText("Get started").assertIsDisplayed()
        keep(name, compose.onRoot().captureToImage().asAndroidBitmap())
    }

    @Test
    fun my_apps() {
        shoot(
            "1-apps",
            "Nothing up yet",
            "Your apps, and how each one is getting on, belong to your account.",
        ) { AppsMark() }
    }

    @Test
    fun profile() {
        shoot(
            "2-profile",
            "Level one, waiting",
            "Every night you open somebody's app moves this ring. Sign in and it starts counting.",
        ) { LevelMark() }
    }

    @Test
    fun crew_lobby() {
        shoot(
            "3-lobby",
            "No seat held",
            "A crew you have joined waits here until all fifteen seats are confirmed.",
        ) { SeatsMark() }
    }

    @Test
    fun crew_running() {
        shoot(
            "4-running",
            "No apps to open tonight",
            "Hold a seat and fourteen nights of testing start here, one night at a time.",
        ) { NightsMark() }
    }

    @Test
    fun crew_record() {
        shoot(
            "5-record",
            "Nothing to look back on",
            "A crew lands here the day its fourteen nights are up, and stays for good.",
        ) { RecordMark() }
    }

    @Test
    fun circle_waiting() {
        shoot(
            "6-waiting",
            "Nothing addressed to you",
            "Reviews of your apps, and cases you are asked to decide, arrive here.",
        ) { NoteMark() }
    }

    /** The pill is the only thing on any of these pages that does anything. */
    @Test
    fun the_pill_is_the_way_out() {
        var asked = 0
        compose.setContent {
            TestTrackTheme {
                Gated(heading = "Nothing up yet", body = "A line.", onSignIn = { asked++ }) {
                    AppsMark()
                }
            }
        }

        compose.onNodeWithText("Get started").performClick()

        assertEquals(1, asked)
    }
}
