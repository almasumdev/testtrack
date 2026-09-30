package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.domain.Case
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The two decisions an account can owe.
 *
 * Neither had a way in before this screen: every review approved itself after two days because
 * nothing could call approve, and every case sat open because nothing could vote. So the tests
 * that matter are that both buttons exist, reach the right call, and that refusing says what it
 * costs before it costs it.
 */
@RunWith(AndroidJUnit4::class)
class WaitingBodyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun review(
        id: String = "r1",
        written: String = "",
        proof: String = "",
    ) = Waiting(id = id, appName = "Nikosh Budget", written = written, proofRef = proof)

    private fun dispute(
        id: String = "d1",
        cast: Int = 1,
    ) = Case(
        id = id,
        appName = "Chalo Transit",
        reason = "The screenshot is of a different app.",
        written = "The search found nothing at all.",
        stake = 15,
        votesCast = cast,
        votesNeeded = 3,
    )

    @Test
    fun an_account_with_nothing_owed_is_told_what_would_land_here() {
        compose.setContent {
            TestTrackTheme { WaitingBody(reviews = emptyList(), disputes = emptyList()) }
        }
        compose.onNodeWithText("Nothing waiting on you").assertIsDisplayed()
    }

    @Test
    fun a_review_of_your_app_offers_both_answers() {
        var approved: Waiting? = null
        var refused: Waiting? = null
        compose.setContent {
            TestTrackTheme {
                WaitingBody(
                    reviews = listOf(review(written = "The total is readable.")),
                    disputes = emptyList(),
                    onApprove = { approved = it },
                    onReject = { refused = it },
                )
            }
        }

        compose.onNodeWithText("Nikosh Budget").assertIsDisplayed()
        compose.onNodeWithText("The total is readable.").assertIsDisplayed()

        compose.onNodeWithText("Accept").performClick()
        assertEquals("r1", approved?.id)
        assertNull(refused)

        compose.onNodeWithText("Refuse").performClick()
        assertEquals("r1", refused?.id)
    }

    @Test
    fun a_review_says_it_accepts_itself_if_nobody_answers() {
        // The owner's real choice is between deciding and letting the clock decide, so the clock
        // is on the card rather than left to be discovered.
        compose.setContent {
            TestTrackTheme { WaitingBody(reviews = listOf(review()), disputes = emptyList()) }
        }
        compose.onNodeWithText("accepts itself", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_case_offers_a_side_and_says_what_it_costs_whoever_loses() {
        var voted: Boolean? = null
        compose.setContent {
            TestTrackTheme {
                WaitingBody(
                    reviews = emptyList(),
                    disputes = listOf(dispute()),
                    onVote = { _, forReviewer -> voted = forReviewer },
                )
            }
        }

        compose.onNodeWithText("Chalo Transit").assertIsDisplayed()
        compose.onNodeWithText("15 tix", substring = true).assertIsDisplayed()
        compose.onNodeWithText("1 of 3").assertIsDisplayed()

        compose.onNodeWithText("The review stands").performClick()
        assertEquals(true, voted)

        compose.onNodeWithText("The developer is right").performClick()
        assertEquals(false, voted)
    }

    @Test
    fun a_case_shows_what_the_developer_actually_said() {
        // Three strangers decide it on this sentence, so hiding it would be asking them to guess.
        compose.setContent {
            TestTrackTheme { WaitingBody(reviews = emptyList(), disputes = listOf(dispute())) }
        }
        compose
            .onNodeWithText("The screenshot is of a different app.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun both_kinds_of_work_appear_under_their_own_heading() {
        compose.setContent {
            TestTrackTheme {
                WaitingBody(reviews = listOf(review()), disputes = listOf(dispute()))
            }
        }
        compose.onNodeWithText("Your apps").assertIsDisplayed()
        compose.onNodeWithText("Help decide").assertIsDisplayed()
    }

    @Test
    fun nothing_can_be_pressed_twice_while_the_first_press_is_in_flight() {
        compose.setContent {
            TestTrackTheme {
                WaitingBody(reviews = listOf(review()), disputes = emptyList(), busy = true)
            }
        }
        compose.onNodeWithText("Accept").assertIsNotEnabled()
        compose.onNodeWithText("Refuse").assertIsNotEnabled()
    }

    // ------------------------------------------------------------------ refusing

    @Test
    fun a_refusal_needs_a_reason_and_says_what_it_costs() {
        var reason: String? = null
        compose.setContent {
            TestTrackTheme {
                FeedRefuseDialog(
                    appName = "Nikosh Budget",
                    onRefuse = { reason = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Refuse it").assertIsNotEnabled()
        assertNull(reason)
        compose
            .onNodeWithText("whoever it goes against pays them", substring = true)
            .assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextInput("Too short")
        compose.onNodeWithText("Refuse it").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput(" and then some more detail")
        compose.onNodeWithText("Refuse it").performClick()
        assertEquals("Too short and then some more detail", reason)
    }
}
