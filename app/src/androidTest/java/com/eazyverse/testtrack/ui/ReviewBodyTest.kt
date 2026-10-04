package com.eazyverse.testtrack.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The review screen as a reviewer meets it.
 *
 * [ReviewStepTest] covers what the app works out from the server's answer. This covers what is
 * then drawn, which is a different question and the one that was wrong: the stepper shipped
 * showing Install, Use and Leave already ticked on a review that had only just been claimed,
 * because the index of the current step was floored to the last step instead of the first. Every
 * arithmetic test passed throughout. Only opening the screen finds that.
 *
 * So the assertions here are about one thing: on any given state, exactly one step offers a
 * button, and it is the right one. A stepper that offers the last step first is not a cosmetic
 * fault. It invites somebody to send proof of a review they have not left, and then to be
 * rejected for it.
 *
 * No account, no network and no Play: [ReviewBody] is handed the state directly, which is why it
 * was split out of [ReviewScreen].
 */
@RunWith(AndroidJUnit4::class)
class ReviewBodyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun post(live: Boolean = true) =
        ApiPost(
            id = "p1",
            appName = "Nikosh Budget",
            packageName = "com.example.nikosh",
            ask = "Is the monthly total readable at a glance?",
            isLive = live,
            reward = 40,
            reviewsBought = 3,
            reviewsDone = 0,
            ownerName = "Rafiul",
            mine = false,
            createdAt = "2026-09-12T00:00:00+00:00",
        )

    private fun review(
        state: String,
        used: Long = 0,
    ) = ApiReview(
        id = "r1",
        postId = "p1",
        state = state,
        usedMs = used,
        neededMs = 30_000,
        proofRef = "",
        written = "",
        reason = "",
        createdAt = "2026-09-12T00:00:00+00:00",
    )

    /** The screen, at a given state, with the taps recorded rather than acted on. */
    private fun open(
        state: String,
        used: Long = 0,
        live: Boolean = true,
        leftReview: Boolean = false,
        sideloaded: Boolean = false,
        onLeave: (ReviewStep) -> Unit = {},
        onSend: (android.net.Uri, String) -> Unit = { _, _ -> },
        proof: android.net.Uri? = null,
        onCapture: () -> Unit = {},
    ) {
        compose.setContent {
            TestTrackTheme {
                ReviewBody(
                    post = post(live),
                    job = ReviewJob(post(live), review(state, used), leftReview),
                    sideloaded = sideloaded,
                    snackbars = remembered,
                    onClose = {},
                    onLeave = onLeave,
                    onSend = onSend,
                    proof = proof,
                    onCapture = onCapture,
                )
            }
        }
    }

    private val remembered = SnackbarHostState()

    // ------------------------------------------------------------------ one step at a time

    @Test
    fun a_fresh_review_offers_the_first_step_and_only_the_first() {
        open("claimed")

        compose.onNodeWithText("Get it on Play").assertIsDisplayed()
        // The three that regressed. Their buttons appearing here is the bug this file exists for.
        compose.onNodeWithText("Open the app").assertDoesNotExist()
        compose.onNodeWithText("Open Play to review").assertDoesNotExist()
        compose.onNodeWithText("Send the proof").assertDoesNotExist()
    }

    @Test
    fun once_it_is_installed_the_second_step_is_the_live_one() {
        open("installed")

        compose.onNodeWithText("Open the app").assertIsDisplayed()
        compose.onNodeWithText("Get it on Play").assertDoesNotExist()
        compose.onNodeWithText("Send the proof").assertDoesNotExist()
    }

    @Test
    fun once_it_has_been_used_the_review_itself_is_next() {
        open("used")

        compose.onNodeWithText("Open Play to review").assertIsDisplayed()
        compose.onNodeWithText("Open the app").assertDoesNotExist()
    }

    @Test
    fun every_step_is_listed_all_along_so_the_job_is_never_a_surprise() {
        // Steps ahead are visible but not actionable. Somebody deciding whether to take a post on
        // should be able to see the whole of what they are agreeing to.
        // They are the short names under the dial.
        open("claimed")
        for (label in listOf("Install", "Use", "Review", "Prove")) {
            compose.onAllNodesWithText(label).onFirst().assertIsDisplayed()
        }
    }

    // ------------------------------------------------------------------ what the buttons do

    @Test
    fun the_install_button_hands_over_rather_than_deciding_anything_itself() {
        var left: ReviewStep? = null
        open("claimed", onLeave = { left = it })

        compose.onNodeWithText("Get it on Play").performClick()
        assertEquals(ReviewStep.INSTALL, left)
    }

    @Test
    fun a_closed_test_is_reviewed_on_play_too() {
        // Closed-test feedback is left on Play as private tester feedback, so the button hands the
        // reviewer to Play exactly as it does for a live app, and nothing is written in here.
        var left: ReviewStep? = null
        open("used", live = false, onLeave = { left = it })

        compose.onNodeWithText("Open Play to review").performClick()
        assertEquals(ReviewStep.LEAVE, left)
    }

    @Test
    fun a_closed_test_can_be_finished_with_a_screenshot_once_play_has_been_opened() {
        open("used", live = false, leftReview = true)
        compose.onNodeWithText("Send the proof").assertIsDisplayed()
    }

    /*
     * The two halves of the one journey with no server state behind it.
     *
     * Nothing outside Google can see that a Play review was left, so "used" is the last word the
     * server ever has on a live post. Unless the app remembers having sent somebody to the review
     * page, the proof step is never reached and the post can be worked on forever without ever
     * being submitted. That is how it shipped. One assertion each way, because the rule only sets
     * its content once per test.
     */

    @Test
    fun a_live_post_offers_no_way_to_send_proof_before_play_has_been_opened() {
        open("used", leftReview = false)
        compose.onNodeWithText("Open Play to review").assertIsDisplayed()
        compose.onNodeWithText("Send the proof").assertDoesNotExist()
    }

    @Test
    fun a_live_post_can_actually_be_finished_once_play_has_been_opened() {
        open("used", leftReview = true)
        compose.onNodeWithText("Send the proof").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ the countdown

    @Test
    fun the_countdown_says_what_is_left_and_then_says_it_is_done() {
        open("installed", used = 20_000)
        compose.onNodeWithText("10 more seconds in the app").assertIsDisplayed()
    }

    @Test
    fun staying_longer_than_asked_never_reads_as_a_negative_countdown() {
        open("installed", used = 600_000)
        compose.onNodeWithText("Long enough. Leave your review next.").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ settled, and warnings

    @Test
    fun a_sent_review_stops_offering_anything_to_press() {
        open("submitted")

        compose.onNodeWithText("Sent").assertIsDisplayed()
        compose
            .onNodeWithText("With the developer. They have two days, then it approves itself.")
            .assertIsDisplayed()
        for (action in listOf("Get it on Play", "Open the app", "Send the proof")) {
            compose.onNodeWithText(action).assertDoesNotExist()
        }
    }

    @Test
    fun a_state_this_build_does_not_know_falls_back_to_the_start_not_the_end() {
        // A server ahead of the app. Showing the last step would let somebody send proof for a
        // review they never left, so the safe fallback is the first step.
        open("some_state_from_a_later_release")
        compose.onNodeWithText("Send the proof").assertDoesNotExist()
    }

    @Test
    fun a_sideloaded_copy_is_called_out_where_it_will_be_read() {
        open("claimed", sideloaded = true)
        compose
            .onNodeWithText("did not come from Play", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun without_a_screenshot_step_four_offers_both_ways_and_will_not_send() {
        var sent = 0
        open("used", leftReview = true, onSend = { _, _ -> sent++ })

        // Both ways to get one are on the page itself, not behind a dialog.
        compose.onNodeWithText("Capture on Play").assertIsDisplayed()
        compose.onNodeWithText("Choose from gallery").assertIsDisplayed()

        // Nothing to send yet. A proof step that accepts an empty proof sends the developer a
        // rejection they cannot judge.
        compose.onNodeWithText("Send the proof").assertIsNotEnabled()
        assertEquals(0, sent)
    }

    @Test
    fun with_a_screenshot_both_ways_to_replace_it_stay_and_it_sends() {
        val shot = android.net.Uri.parse("file:///nowhere/proof.jpg")
        var sent: android.net.Uri? = null
        var captures = 0
        open("used", leftReview = true, proof = shot, onSend = { uri, _ -> sent = uri }, onCapture = { captures++ })

        compose.onNodeWithText("Screenshot added").assertIsDisplayed()
        // Replacing it is not only a screen capture: the gallery is right beside it.
        compose.onNodeWithText("Gallery").assertIsDisplayed()
        compose.onNodeWithText("Capture").performClick()
        assertEquals(1, captures)

        compose.onNodeWithText("Send the proof").performClick()
        assertEquals(shot, sent)
    }

    @Test
    fun a_sent_review_ends_on_a_done_button_so_the_page_never_resizes() {
        open("submitted")
        compose.onNodeWithText("Done").assertIsDisplayed()
    }
}
