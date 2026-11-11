package com.eazyverse.testtrack.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.viewmodel.SignInUi
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

    /** The frame's title row and the body under it, as the sheet puts them together. */
    @Composable
    private fun Sheet(
        ui: SignInUi,
        onPress: () -> Unit = {},
        onAddAccount: () -> Unit = {},
        onClose: () -> Unit = {},
        onOk: () -> Unit = {},
    ) {
        TestTrackTheme {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SignInLead(ui)
                    Spacer(Modifier.width(12.dp))
                    Text(signInTitle(reason, ui))
                }
                SignInSheetBody(reason, ui, onPress, onAddAccount, onClose, onOk)
            }
        }
    }

    private fun show(
        ui: SignInUi,
        title: String,
        body: String,
    ) {
        compose.setContent { Sheet(ui) }
        compose.onNodeWithText(title).assertIsDisplayed()
        compose.onNodeWithText(body).assertIsDisplayed()
    }

    @Test
    fun the_ask_shows_the_reason_it_came_up() {
        compose.setContent { Sheet(SignInUi.Idle) }

        compose.onNodeWithText(reason.title).assertIsDisplayed()
        compose.onNodeWithText(reason.why).assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
        compose.onNodeWithText("Google Play links testers by their Google account.").assertIsDisplayed()
    }

    @Test
    fun signing_in_says_so_and_has_no_button() {
        show(SignInUi.Working, "Signing you in", "This takes a few seconds.")

        // Nothing to press while Google is being waited on.
        compose.onAllNodesWithText("Continue with Google").assertCountEquals(0)
    }

    @Test
    fun cancelling_can_try_again_or_keep_looking_around() {
        var tried = false
        var closed = false
        compose.setContent { Sheet(SignInUi.Cancelled, onPress = { tried = true }, onClose = { closed = true }) }

        compose.onNodeWithText("Sign-in was cancelled").assertIsDisplayed()
        compose.onNodeWithText("You can try again or keep looking around.").assertIsDisplayed()
        compose.onNodeWithText("Continue with Google").performClick()
        assertTrue(tried)
        compose.onNodeWithText("Keep looking around").performClick()
        assertTrue(closed)
    }

    @Test
    fun no_internet_says_what_to_check() {
        show(
            SignInUi.Offline,
            "No internet connection",
            "Sign-in needs the internet. Check your Wi-Fi or mobile data, then try again.",
        )
        compose.onNodeWithText("Continue with Google").assertIsDisplayed()
    }

    @Test
    fun no_account_offers_to_add_one_or_try_again() {
        var added = false
        var tried = false
        compose.setContent {
            Sheet(SignInUi.NoAccount, onPress = { tried = true }, onAddAccount = { added = true })
        }

        compose.onNodeWithText("No Google account found").assertIsDisplayed()
        compose.onNodeWithText("Add a Google account to this phone, then come back and try again.").assertIsDisplayed()
        compose.onNodeWithText("Add a Google account").performClick()
        assertTrue(added)
        compose.onNodeWithText("Try again").performClick()
        assertTrue(tried)
    }

    @Test
    fun a_failure_shows_the_servers_words_when_it_has_them() {
        show(SignInUi.Failed("This account was deleted."), "Sign-in didn't work", "This account was deleted.")
    }

    @Test
    fun a_failure_with_nothing_to_say_shows_the_default_text() {
        show(SignInUi.Failed(null), "Sign-in didn't work", DEFAULT_FAILURE)
    }

    @Test
    fun the_account_is_named_once_it_has_worked() {
        compose.setContent {
            Sheet(SignInUi.SignedIn("tester@example.com", "Test Er", null, starterUsed = false))
        }

        compose.onNodeWithText("You're signed in").assertIsDisplayed()
        compose.onNodeWithText("Test Er").assertIsDisplayed()
        compose.onNodeWithText("tester@example.com").assertIsDisplayed()
        // It closes itself, so there is nothing to press.
        compose.onAllNodesWithText("OK").assertCountEquals(0)
    }

    @Test
    fun a_second_account_on_the_phone_is_told_why_it_starts_at_0_and_closes_on_ok() {
        var closed = false
        compose.setContent {
            Sheet(
                SignInUi.SignedIn("second@example.com", "Second", null, starterUsed = true),
                onOk = { closed = true },
            )
        }

        compose.onNodeWithText("Starter tix already used").assertIsDisplayed()
        compose.onNodeWithText("Each phone gets 120 starter tix once", substring = true).assertIsDisplayed()
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
        var ui by mutableStateOf<SignInUi>(SignInUi.Idle)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TestTrackTheme { SignInSheetBody(reason = reason, ui = ui) }
        }
        compose.mainClock.advanceTimeBy(64)

        val before = compose.onRoot().captureToImage().asAndroidBitmap()
        keep("00-before", before)

        ui = SignInUi.SignedIn("tester@example.com", "Test Er", null, starterUsed = false)
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
