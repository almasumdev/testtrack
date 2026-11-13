package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import com.eazyverse.testtrack.viewmodel.GroupUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The testers group sheet, in each of its faces.
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

    private fun open(ui: GroupUi) =
        compose.setContent {
            TestTrackTheme {
                GroupSheetBody(
                    ui = ui,
                    email = "rafiul@example.com",
                    onOpenGroup = { opens++ },
                    onCheck = { checks++ },
                    onClose = { closes++ },
                )
            }
        }

    @Test
    fun checking_names_the_account_and_offers_only_not_now() {
        open(GroupUi.Checking)
        compose.onNodeWithText("rafiul@example.com").assertIsDisplayed()
        compose.onNodeWithText("Checking now").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Open the group").fetchSemanticsNodes().size)
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun join_leads_to_the_group_and_can_be_checked_by_hand() {
        open(GroupUi.Join(opened = false))
        compose.onNodeWithText("Not in the group yet").assertIsDisplayed()
        compose.onNodeWithText("Open the group with this account").assertIsDisplayed()
        compose.onNodeWithText("Open the group").performClick()
        assertEquals(1, opens)
        compose.onNodeWithText("I already joined").performClick()
        assertEquals(1, checks)
    }

    @Test
    fun rechecking_keeps_the_steps_and_only_the_busy_button_shows() {
        open(GroupUi.Rechecking)
        compose.onNodeWithText("Checking").assertIsNotEnabled()
        compose.onNodeWithText("I already joined").assertIsNotEnabled()
        compose.onNodeWithText("Checking now").assertIsDisplayed()
    }

    @Test
    fun joined_says_so_and_has_nothing_to_press() {
        open(GroupUi.Joined)
        compose.onNodeWithText("In the testers group").assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Open the group").fetchSemanticsNodes().size)
    }

    @Test
    fun still_out_says_what_to_check_and_offers_another_look() {
        open(GroupUi.StillOut)
        assertEquals(0, compose.onAllNodesWithText("Join anyway", substring = true).fetchSemanticsNodes().size)
        compose.onNodeWithText("Showed a different account?").assertIsDisplayed()
        compose.onNodeWithText("Check again").performClick()
        assertEquals(1, checks)
        compose.onNodeWithText("Open the group again").performClick()
        assertEquals(1, opens)
    }

    @Test
    fun a_failed_check_is_never_told_as_not_being_a_member() {
        open(GroupUi.Failed("Couldn't check the testers group. Check your connection and try again."))
        compose.onNodeWithText("Couldn't check").assertIsDisplayed()
        compose.onNodeWithText("Couldn't check the testers group", substring = true).assertIsDisplayed()
        compose.onNodeWithText("you're still in", substring = true).assertIsDisplayed()
        assertEquals(0, compose.onAllNodesWithText("Not in the group yet").fetchSemanticsNodes().size)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, checks)
        compose.onNodeWithText("Not now").performClick()
        assertEquals(1, closes)
    }

    @Test
    fun titles_follow_the_state() {
        assertEquals("Checking the testers group", groupTitle(GroupUi.Checking))
        assertEquals("Join the testers group", groupTitle(GroupUi.Join(opened = true)))
        assertEquals("Join the testers group", groupTitle(GroupUi.Rechecking))
        assertEquals("You're in the testers group", groupTitle(GroupUi.Joined))
        assertEquals("Not in the group yet", groupTitle(GroupUi.StillOut))
        assertEquals("Couldn't check the group", groupTitle(GroupUi.Failed("x")))
    }
}
