package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The sheet, including the half no emulator can otherwise reach.
 *
 * Signing in needs a real Google account, so the state after it succeeds cannot be arrived at by
 * driving the app: the only way to look at it is to hand the body that state directly. That is
 * why the body takes its state rather than holding it.
 *
 * The transition is checked by stopping the clock and stepping it. An animation that plays at
 * full speed on a device is a thing you can miss; an animation stepped frame by frame either has
 * intermediate frames or it does not.
 */
@RunWith(AndroidJUnit4::class)
class SignInSheetBodyTest {
    @get:Rule
    val compose = createComposeRule()

    private val reason =
        SignInReason(
            title = "Sign in to see your apps",
            why = "This is the list of what you have put up, so it needs to know who you are.",
        )

    /** Written where the run can pull them out afterwards, so the frames can be looked at. */
    private fun keep(
        name: String,
        bitmap: Bitmap,
    ) {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shots")
        dir.mkdirs()
        File(dir, "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun the_ask_shows_the_reason_it_came_up() {
        compose.setContent { TestTrackTheme { SignInSheetBody(reason = reason) } }

        compose.onNodeWithText(reason.why).assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
    }

    @Test
    fun signing_in_holds_the_button_rather_than_the_sheet() {
        compose.setContent { TestTrackTheme { SignInSheetBody(reason = reason, busy = true) } }

        compose.onNodeWithText("Waiting for Google").assertIsDisplayed()
        // The reason stays put. A sheet that empties itself while it works leaves somebody
        // looking at a spinner with nothing saying what it is for.
        compose.onNodeWithText(reason.why).assertIsDisplayed()
    }

    @Test
    fun the_account_is_named_once_it_has_worked() {
        compose.setContent {
            TestTrackTheme {
                SignInSheetBody(reason = reason, signedInAs = "tester@example.com")
            }
        }

        compose.onNodeWithText("Signed in").assertIsDisplayed()
        compose.onNodeWithText("tester@example.com").assertIsDisplayed()
    }

    @Test
    fun a_second_account_on_the_phone_is_told_why_it_starts_at_0_and_closes_on_ok() {
        var closed = false
        compose.setContent {
            TestTrackTheme {
                SignInSheetBody(
                    reason = reason,
                    signedInAs = "second@example.com",
                    phoneUsedStarter = true,
                    onOk = { closed = true },
                )
            }
        }

        compose.onNodeWithText("This phone already got its 120 starter tix", substring = true).assertIsDisplayed()
        // The usual line promises 120 tix, which is not true for this account.
        compose.onAllNodesWithText("A new account gets", substring = true).assertCountEquals(0)
        compose.onNodeWithText("OK").performClick()
        assertTrue(closed)
    }

    /**
     * The one this file was written for.
     *
     * The halves used to cut: on the frame the answer arrived the form was gone and the panel was
     * there, with nothing in between. Stepping the clock in small slices is what tells the
     * difference between a crossfade and a swap, because a swap has no middle.
     */
    @Test
    fun the_two_halves_cross_rather_than_cut() {
        var account by mutableStateOf<String?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TestTrackTheme { SignInSheetBody(reason = reason, signedInAs = account) }
        }
        compose.mainClock.advanceTimeBy(64)

        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        keep("00-before", before)

        account = "tester@example.com"
        compose.mainClock.advanceTimeBy(16)

        val middle = mutableListOf<Bitmap>()
        repeat(6) { step ->
            compose.mainClock.advanceTimeBy(50)
            val frame = compose.onRoot().captureToImage().asAndroidBitmap()
            keep("%02d-mid".format(step + 1), frame)
            middle += frame
        }

        compose.mainClock.advanceTimeBy(600)
        val after = compose.onRoot().captureToImage().asAndroidBitmap()
        keep("99-after", after)

        // A cut gives two distinct frames and nothing else, so every sample would equal one end
        // or the other. A crossfade puts frames between them that match neither.
        val between =
            middle.count { frame ->
                !frame.sameAs(before) && !frame.sameAs(after)
            }
        assertTrue("no frame between the two halves: this is a cut, not a crossfade", between > 0)
    }
}
