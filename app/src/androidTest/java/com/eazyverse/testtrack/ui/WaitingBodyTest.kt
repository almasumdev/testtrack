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
import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
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
    fun a_request_page_shows_each_review_and_offers_to_close_with_the_refund() {
        var closed = 0
        compose.setContent {
            TestTrackTheme {
                RequestCheck(
                    Request(
                        id = "p1",
                        appName = "Porua",
                        bought = 3,
                        done = 1,
                        open = 1,
                        reward = 40,
                        refundable = 40,
                        reviews =
                            listOf(
                                RequestReview(id = "r1", state = "approved", reviewerName = "Tanvir"),
                                RequestReview(id = "r2", state = "rejected", reviewerName = "Mitu", reason = "Wrong app."),
                            ),
                    ),
                    onClose = { closed++ },
                )
            }
        }
        compose.onNodeWithText("Tanvir").assertIsDisplayed()
        compose.onNodeWithText("Accepted").assertIsDisplayed()
        compose.onNodeWithText("Refused and upheld").assertIsDisplayed()
        compose.onNodeWithText("Close and get 40 tix back").performClick()
        compose.onNodeWithText("Close it").performClick()
        assertEquals(1, closed)
    }

    @Test
    fun an_account_with_nothing_owed_is_told_what_would_land_here() {
        compose.setContent {
            TestTrackTheme { WaitingBody(reviews = emptyList(), disputes = emptyList()) }
        }
        compose.onNodeWithText("Nothing to check").assertIsDisplayed()
    }

    @Test
    fun a_review_of_your_app_offers_both_answers() {
        var approved: Waiting? = null
        var refused: Waiting? = null
        compose.setContent {
            TestTrackTheme {
                val shown = review(written = "The total is readable.")
                ReviewCheck(shown, onApprove = { approved = shown }, onReject = { refused = shown })
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
            TestTrackTheme { ReviewCheck(review()) }
        }
        compose.onNodeWithText("Accepts itself", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_case_votes_only_when_a_side_is_picked_and_sent() {
        var voted: Boolean? = null
        compose.setContent {
            TestTrackTheme {
                CaseCheck(dispute(), onVote = { voted = it })
            }
        }

        compose.onNodeWithText("Chalo Transit").assertIsDisplayed()
        compose.onNodeWithText("1 of 3 votes in").assertIsDisplayed()
        compose.onNodeWithText("Send my vote").assertIsNotEnabled()

        // A tap picks a side; nothing is counted until the vote is sent.
        compose.onNodeWithText("The review stands").performClick()
        assertNull(voted)
        compose.onNodeWithText("The developer is right").performClick()
        compose.onNodeWithText("Send my vote").performClick()
        assertEquals(false, voted)
    }

    @Test
    fun a_counted_vote_stays_on_the_page_and_says_what_it_waits_for() {
        compose.setContent {
            TestTrackTheme { CaseCheck(dispute(cast = 2), voted = true) }
        }
        compose.onNodeWithText("2 of 3 votes in").assertIsDisplayed()
        compose.onNodeWithText("Waiting for one more vote", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Send my vote").assertDoesNotExist()
    }

    @Test
    fun a_case_shows_what_the_developer_actually_said() {
        // Three strangers decide it on this sentence, so hiding it would be asking them to guess.
        compose.setContent {
            TestTrackTheme { CaseCheck(dispute()) }
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
        compose.onNodeWithText("Reviews of your apps").assertIsDisplayed()
        compose.onNodeWithText("Cases to decide").assertIsDisplayed()
    }

    @Test
    fun the_pager_says_where_this_review_is_in_the_run() {
        compose.setContent { TestTrackTheme { ReviewCheck(review(), pager = "1 of 2") } }
        compose.onNodeWithText("1 of 2").assertIsDisplayed()
    }

    @Test
    fun a_row_opens_its_own_page_rather_than_deciding_in_the_list() {
        var opened: String? = null
        compose.setContent {
            TestTrackTheme {
                WaitingBody(
                    reviews = listOf(review().copy(reviewerName = "Tanvir Hasan", decideBy = System.currentTimeMillis() + 30 * 3_600_000L)),
                    disputes = emptyList(),
                    onOpenReview = { opened = it.id },
                )
            }
        }
        // Who sent it and how long is left, on the row; no buttons until it is opened.
        compose.onNodeWithText("Tanvir Hasan, live app").assertIsDisplayed()
        compose.onNodeWithText("1d 5h").assertIsDisplayed()
        compose.onNodeWithText("Accept").assertDoesNotExist()
        compose.onNodeWithText("Nikosh Budget").performClick()
        assertEquals("r1", opened)
    }

    @Test
    fun nothing_can_be_pressed_twice_while_the_first_press_is_in_flight() {
        compose.setContent {
            TestTrackTheme {
                ReviewCheck(review(), busy = true)
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
                FeedRefuseSheet(
                    onRefuse = { text, _ -> reason = text },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Refuse and hold 15 tix").assertIsNotEnabled()
        assertNull(reason)
        // What refusing holds, said before it is held.
        compose
            .onNodeWithText("15 tix are held from you", substring = true)
            .assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextInput("Too short")
        compose.onNodeWithText("Refuse and hold 15 tix").assertIsNotEnabled()

        compose.onNode(hasSetTextAction()).performTextInput(" and then some more detail")
        compose.onNodeWithText("Refuse and hold 15 tix").performClick()
        assertEquals("Too short and then some more detail", reason)
    }
}
