package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A crew page's rows, its sheet and its dialogs, as a member meets them.
 *
 * [CrewWireTest] covers what the app works out from the server's answer. This covers what is then
 * drawn, which is a different question: a row that computes the right thing and offers the wrong
 * button is still a bug. The rows take plain values and the actions, so no account or network.
 */
@RunWith(AndroidJUnit4::class)
class CrewRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private var opened = 0
    private var installed = 0
    private var reported = 0
    private var vote: Boolean? = null

    private fun actions(onPhone: Boolean) =
        CrewActions(
            onOpen = { opened++ },
            onOpenAll = {},
            onInstall = { installed++ },
            onReport = { reported++ },
            onLeave = {},
            onTakeSeat = {},
            onVote = { _, yes -> vote = yes },
            isInstalled = { onPhone },
        )

    private fun app(
        done: Boolean = false,
        reported: Boolean = false,
    ) = CrewApp(
        id = "s1",
        label = "Nikosh Budget",
        packageName = "com.example.nikosh",
        owner = "Rafiul",
        crew = "Crew 07",
        done = done,
        reported = reported,
    )

    private fun seat(
        reported: Boolean = false,
        notes: String = "",
        state: SeatState = SeatState.CONFIRMED,
        myVote: Boolean? = null,
    ) = CrewSeat(
        id = "s1",
        appName = "Nikosh Budget",
        owner = "Rafiul",
        packageName = "com.example.nikosh",
        notes = notes,
        crew = "Crew 07",
        state = state,
        reported = reported,
        yes = 1,
        myVote = myVote,
    )

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent { TestTrackTheme { content() } }

    // ------------------------------------------------------------------ tonight

    @Test
    fun an_app_on_the_phone_offers_open() {
        show { TonightRow(app(), seat(), TilePlace.ONLY, actions(onPhone = true)) {} }
        compose.onNodeWithText("Open").assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun an_app_not_on_the_phone_offers_install_first() {
        show { TonightRow(app(), seat(), TilePlace.ONLY, actions(onPhone = false)) {} }
        compose.onNodeWithText("by Rafiul, not on your phone").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertEquals(1, installed)
        compose.onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun an_app_done_tonight_says_opened_or_reported() {
        show { DoneRow(app(done = true), seat(), TilePlace.FIRST) {} }
        compose.onNodeWithText("Opened").assertIsDisplayed()
        compose.onNodeWithText("Open").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ the lobby

    @Test
    fun a_lobby_app_offers_install_and_says_when_it_has_a_sign_in() {
        show { InstallRow(seat(notes = signInNotes("tester@example.com", "hunter2", "")), TilePlace.ONLY, actions(onPhone = false)) {} }
        compose.onNodeWithText("by Rafiul, has a sign-in").assertIsDisplayed()
        compose.onNodeWithText("Install").performClick()
        assertEquals(1, installed)
    }

    @Test
    fun a_lobby_app_already_installed_or_reported_says_so() {
        show { InstallRow(seat(reported = true), TilePlace.ONLY, actions(onPhone = true)) {} }
        compose.onNodeWithText("Reported").assertIsDisplayed()
        compose.onNodeWithText("Install").assertDoesNotExist()
    }

    @Test
    fun an_asked_app_goes_install_then_it_installed() {
        show { AskedRow(seat(state = SeatState.CHECKING), TilePlace.ONLY, actions(onPhone = true)) {} }
        compose.onNodeWithText("It installed").performClick()
        assertEquals(true, vote)
    }

    @Test
    fun an_asked_app_not_installed_offers_install() {
        show { AskedRow(seat(state = SeatState.CHECKING), TilePlace.ONLY, actions(onPhone = false)) {} }
        compose.onNodeWithText("Install").assertIsDisplayed()
        compose.onNodeWithText("It installed").assertDoesNotExist()
    }

    @Test
    fun once_answered_it_says_what_you_said() {
        show { AskedRow(seat(state = SeatState.CHECKING, myVote = false), TilePlace.ONLY, actions(onPhone = true)) {} }
        compose.onNodeWithText("You said No").assertIsDisplayed()
        compose.onNodeWithText("It installed").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ a row's sheet

    @Test
    fun the_sheet_has_the_sign_in_to_copy_and_report() {
        show {
            CrewRowSheet(
                RowSheet(seat(notes = signInNotes("tester@example.com", "hunter2", "")), SheetKind.INSTALL),
                "Crew 07",
                actions(onPhone = false),
            ) {
            }
        }
        compose.onNodeWithText("tester@example.com").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy Username").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy Password").assertIsDisplayed()
        compose.onNodeWithText("Install on Play").assertIsDisplayed()
        compose.onNodeWithText("Report a problem").assertIsDisplayed()
    }

    @Test
    fun the_sheet_says_when_no_sign_in_is_needed_and_when_you_reported() {
        show { CrewRowSheet(RowSheet(seat(reported = true), SheetKind.INSTALL), "Crew 07", actions(onPhone = true)) {} }
        compose.onNodeWithText("No sign-in needed. It opens straight in.").assertIsDisplayed()
        compose.onNodeWithText("You reported it").assertIsDisplayed()
        compose.onNodeWithText("Report a problem").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ the report dialog

    @Test
    fun a_report_needs_a_reason_the_owner_can_act_on() {
        var sent: String? = null
        show { CrewReportDialog(seat(), onReport = { sent = it }, onDismiss = {}) }
        compose.onNodeWithText("Report").assertIsNotEnabled()
        compose.onNodeWithText("Play says the item was not found").performTextInput("broken")
        compose.onNodeWithText("Report").assertIsNotEnabled()
        assertNull(sent)
    }

    @Test
    fun a_quick_reason_is_one_tap() {
        var sent: String? = null
        show { CrewReportDialog(seat(), onReport = { sent = it }, onDismiss = {}) }
        compose.onNodeWithText("It crashes when it opens").performClick()
        compose.onNodeWithText("Report").assertIsEnabled().performClick()
        assertEquals("It crashes when it opens", sent)
    }

    // ------------------------------------------------------------------ members

    @Test
    fun members_say_who_opened_your_app_tonight() {
        val vm = CrewViewModel()
        val crew =
            Crew(
                id = "c1",
                name = "Crew 07",
                phase = CrewPhase.RUN,
                day = 6,
                members =
                    listOf(
                        CrewMember("me", "You", "Time Lapse", day = 6, mine = true),
                        CrewMember("a", "Rafiul", "Nikosh", day = 6, openedMineToday = true, openedMineDays = 6),
                        CrewMember("b", "Nadia", "Habit Garden", day = 6, openedMineDays = 5),
                        CrewMember("c", "Kamal", "Mood Log", day = 6, out = true, openedMineDays = 3),
                    ),
            )
        vm.preview(listOf(crew))
        show { CrewMembersPage(vm, "c1", onBack = {}) }
        compose.onNodeWithText("1 of 2 opened your app tonight").assertIsDisplayed()
        compose.onNodeWithText("Opened").assertIsDisplayed()
        compose.onNodeWithText("Not yet").assertIsDisplayed()
        compose.onNodeWithText("Out").assertIsDisplayed()
    }
}

/**
 * Your own app in the lobby: leaving while it fills, and putting it in again once sent back.
 *
 * The rule that matters is when leaving disappears: once a crew is full, fourteen people are
 * relying on that seat and the server refuses it.
 */
@RunWith(AndroidJUnit4::class)
class CrewLeaveTest {
    @get:Rule
    val compose = createComposeRule()

    private var left = 0
    private var again = 0

    private val actions =
        CrewActions(
            onOpen = {},
            onOpenAll = {},
            onInstall = {},
            onReport = {},
            onLeave = { left++ },
            onTakeSeat = { again++ },
        )

    private fun crewWith(
        filling: Boolean,
        state: SeatState = SeatState.CONFIRMED,
        outReason: String = "",
    ): Crew {
        val mine =
            CrewSeat(
                id = "mine",
                appName = "Nikosh Budget",
                owner = "You",
                crew = "Crew 07",
                state = state,
                mine = true,
                outReason = outReason,
            )
        return Crew(id = "c1", name = "Crew 07", phase = CrewPhase.LOBBY, filling = filling, seats = listOf(mine))
    }

    @Test
    fun a_seat_can_be_taken_back_out_while_the_crew_fills() {
        compose.setContent {
            TestTrackTheme { CrewLobbyPhase(crewWith(filling = true), actions) }
        }
        compose.onNodeWithText("Take it back out").assertIsDisplayed().performClick()
        assertEquals(1, left)
    }

    @Test
    fun once_the_crew_is_full_it_is_not_offered() {
        compose.setContent {
            TestTrackTheme { CrewLobbyPhase(crewWith(filling = false), actions) }
        }
        compose.onNodeWithText("Take it back out").assertDoesNotExist()
    }

    @Test
    fun an_app_sent_back_says_why_and_offers_to_go_in_again() {
        compose.setContent {
            TestTrackTheme {
                CrewLobbyPhase(
                    crewWith(filling = true, state = SeatState.REJECTED, outReason = "Two members could not install it."),
                    actions,
                )
            }
        }
        compose.onNodeWithText("Two members could not install it.").assertIsDisplayed()
        compose.onNodeWithText("Fix it, then put it in again").performClick()
        assertEquals(1, again)
        compose.onNodeWithText("Take it back out").assertDoesNotExist()
    }
}
