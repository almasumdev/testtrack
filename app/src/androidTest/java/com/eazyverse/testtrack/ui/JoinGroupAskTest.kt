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

    private fun open(
        checking: Boolean = false,
        outside: Boolean = false,
        failure: String? = null,
    ) = compose.setContent {
        TestTrackTheme {
            JoinGroupAsk(
                email = "rafiul@gmail.com",
                checking = checking,
                outside = outside,
                failure = failure,
                onCheck = { checks++ },
                onOpenGroup = { opens++ },
                onClose = { closes++ },
            )
        }
    }

    @Test
    fun while_checking_it_says_so_and_offers_nothing_to_press_but_cancel() {
        open(checking = true)
        compose.onNodeWithText("Checking the testers group").assertIsDisplayed()
        compose.onNodeWithText("Checking…").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun outside_the_group_it_names_the_account_and_leads_to_the_group() {
        open(outside = true)
        compose.onNodeWithText("Join the testers group").assertIsDisplayed()
        compose.onNodeWithText("rafiul@gmail.com", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Open the group").performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("I've joined, check again").performClick()
        assertEquals(1, checks)
    }

    @Test
    fun a_failed_check_is_never_told_as_not_being_a_member() {
        open(failure = "Couldn't reach the membership service.")
        compose.onNodeWithText("Couldn't check the testers group").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Join the testers group").fetchSemanticsNodes().size)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, checks)
    }
}
