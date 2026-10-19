package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The testers group dialog, in each of its three faces.
 *
 * The one that matters most is the failure: a check that could not be made must never read as
 * "you are not in the group", which is the answer most likely to make somebody who has joined
 * give up.
 */
@RunWith(AndroidJUnit4::class)
class JoinGroupAskTest {
    @get:Rule
    val compose = createComposeRule()

    private var checks = 0
    private var opens = 0
    private var closes = 0
    private var continues = 0

    private fun open(
        checking: Boolean = false,
        outside: Boolean = false,
        failure: String? = null,
        confirmed: Boolean = false,
        stillOut: Boolean = false,
    ) = compose.setContent {
        TestTrackTheme {
            JoinGroupAsk(
                email = "rafiul@example.com",
                checking = checking,
                outside = outside,
                failure = failure,
                onCheck = { checks++ },
                onOpenGroup = { opens++ },
                onClose = { closes++ },
                confirmed = confirmed,
                stillOut = stillOut,
                onContinue = { continues++ },
            )
        }
    }

    @Test
    fun while_checking_it_lists_what_is_checked_and_offers_only_cancel() {
        open(checking = true)
        compose.onNodeWithText("Checking your tester access").assertIsDisplayed()
        compose.onNodeWithText("Looking you up in the group").assertIsDisplayed()
        compose.onNodeWithText("rafiul@example.com").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun outside_the_group_it_names_the_account_and_leads_to_the_group() {
        open(outside = true)
        compose.onNodeWithText("Join the testers group").assertIsDisplayed()
        compose.onNodeWithText("rafiul@example.com").assertIsDisplayed()
        compose.onNodeWithText("Not joined").assertIsDisplayed()
        compose.onNodeWithText("Open group").performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("Already joined? Check now").performClick()
        assertEquals(1, checks)
    }

    @Test
    fun checking_again_keeps_the_page_and_only_lets_it_be_cancelled() {
        open(outside = true, checking = true)
        compose.onNodeWithText("Checking").assertIsDisplayed()
        compose.onNodeWithText("Open group").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun still_out_after_checking_again_says_why_and_offers_another_look() {
        open(outside = true, stillOut = true)
        compose.onNodeWithText("Still not in the group", substring = true).performClick()
        assertEquals(1, checks)
    }

    @Test
    fun a_yes_waits_for_continue_rather_than_closing_by_itself() {
        open(confirmed = true)
        compose.onNodeWithText("You're all set").assertIsDisplayed()
        assertEquals(0, continues)
        compose.onNodeWithText("Continue").performClick()
        assertEquals(1, continues)
    }

    @Test
    fun a_failed_check_is_never_told_as_not_being_a_member() {
        open(failure = "Couldn't check the testers group. Check your connection and try again.")
        compose.onNodeWithText("Couldn't check the group").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Join the testers group").fetchSemanticsNodes().size)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, checks)
    }
}
