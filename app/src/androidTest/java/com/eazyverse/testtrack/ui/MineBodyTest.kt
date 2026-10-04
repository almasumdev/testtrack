package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Mine tab: where a review goes once it has left the Feedback list. */
@RunWith(AndroidJUnit4::class)
class MineBodyTest {
    @get:Rule
    val compose = createComposeRule()

    private fun review(
        id: String,
        state: String,
        name: String = "Porua",
        reason: String = "",
    ) = MyReview(id = id, postId = "p-$id", appName = name, state = state, reward = 30, reason = reason)

    private fun open(
        reviews: List<MyReview>,
        signedOut: Boolean = false,
        onOpen: (MyReview) -> Unit = {},
    ) = compose.setContent {
        TestTrackTheme {
            MineBody(reviews = reviews, loading = false, signedOut = signedOut, onSignIn = {}, onOpen = onOpen)
        }
    }

    @Test
    fun reviews_are_grouped_by_where_they_have_got_to() {
        open(
            listOf(
                review("a", "used", "Bluefy"),
                review("b", "submitted", "Porua"),
                review("c", "approved", "Rickshaw Fare"),
            ),
        )
        compose.onNodeWithText("In progress").assertIsDisplayed()
        compose.onNodeWithText("Waiting for the developer").assertIsDisplayed()
        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Waiting").assertIsDisplayed()
        compose.onNodeWithText("+30 tix").assertIsDisplayed()
    }

    @Test
    fun a_review_still_in_progress_can_be_picked_up_again() {
        var opened: String? = null
        open(listOf(review("a", "used", "Bluefy")), onOpen = { opened = it.id })
        compose.onNodeWithText("Bluefy").performClick()
        assertEquals("a", opened)
    }

    @Test
    fun a_sent_review_opens_too_to_see_where_it_stands() {
        var opened: String? = null
        open(listOf(review("b", "submitted", "Porua")), onOpen = { opened = it.id })
        compose.onNodeWithText("Porua").performClick()
        assertEquals("b", opened)
    }

    @Test
    fun a_refused_review_says_why() {
        open(listOf(review("c", "rejected", reason = "The screenshot is of another app.")))
        compose.onNodeWithText("The screenshot is of another app.").assertIsDisplayed()
        compose.onNodeWithText("Not paid").assertIsDisplayed()
    }

    @Test
    fun nothing_yet_says_how_to_start() {
        open(emptyList())
        compose.onNodeWithText("No reviews yet").assertIsDisplayed()
    }

    @Test
    fun signed_out_asks_to_sign_in() {
        open(emptyList(), signedOut = true)
        compose.onNodeWithText("Sign in").assertIsDisplayed()
    }
}
