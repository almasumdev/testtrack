package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
 * The crew rows and dialogs, as a member meets them.
 *
 * [CrewWireTest] covers what the app works out from the server's answer. This covers what is then
 * drawn, which is a different question: a row that computes the right thing and offers the wrong
 * button is still a bug.
 *
 * The rows take plain values rather than a view model, which is what makes this possible without
 * an account or a network.
 */
@RunWith(AndroidJUnit4::class)
class CrewRowsTest {
    @get:Rule
    val compose = createComposeRule()

    private fun app(
        done: Boolean = false,
        reported: Boolean = false,
    ) = CrewApp(
        id = "a1",
        label = "Nikosh Budget",
        owner = "Rafiul",
        crew = "Crew 07",
        done = done,
        reported = reported,
    )

    private fun seat(
        mine: Boolean = false,
        reported: Boolean = false,
        notes: String = "",
    ) = CrewSeat(
        id = "s1",
        appName = "Nikosh Budget",
        owner = "Rafiul",
        packageName = "com.example.nikosh",
        notes = notes,
        crew = "Crew 07",
        mine = mine,
        reported = reported,
    )

    // ------------------------------------------------------------------ tonight's row

    @Test
    fun an_app_owed_tonight_offers_open_and_report() {
        var opened = 0
        var reported = 0
        compose.setContent {
            TestTrackTheme {
                CrewAppRow(app(), onOpen = { opened++ }, onReport = { reported++ })
            }
        }

        compose.onNodeWithText("Nikosh Budget").assertIsDisplayed()
        compose.onNodeWithText("Open").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Report").assertIsDisplayed().performClick()
        assertEquals(1, reported)
    }

    @Test
    fun an_app_already_opened_stops_asking() {
        compose.setContent {
            TestTrackTheme { CrewAppRow(app(done = true), onOpen = {}, onReport = {}) }
        }
        compose.onNodeWithText("Open").assertDoesNotExist()
        compose.onNodeWithText("Report").assertDoesNotExist()
    }

    @Test
    fun an_app_you_reported_is_shown_as_excused() {
        compose.setContent {
            TestTrackTheme { CrewAppRow(app(reported = true), onOpen = {}, onReport = {}) }
        }
        compose.onNodeWithText("Open").assertDoesNotExist()
        compose.onNodeWithText("no longer counts against you", substring = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ seats in the lobby

    @Test
    fun a_seat_in_the_lobby_offers_play_and_a_report_and_shows_the_notes() {
        var installed = 0
        var reported = 0
        compose.setContent {
            TestTrackTheme {
                CrewSeatRow(
                    seat(notes = "Test account: demo / demo"),
                    onInstall = { installed++ },
                    onReport = { reported++ },
                )
            }
        }
        compose.onNodeWithText("Rafiul", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Test account: demo / demo").assertIsDisplayed()
        compose.onNodeWithText("Get it on Play").performClick()
        compose.onNodeWithText("Report").performClick()
        assertEquals(1, installed)
        assertEquals(1, reported)
    }

    @Test
    fun a_seat_you_reported_says_so_and_does_not_ask_again() {
        compose.setContent {
            TestTrackTheme { CrewSeatRow(seat(reported = true), onInstall = {}, onReport = {}) }
        }
        compose.onNodeWithText("Reported").assertIsDisplayed()
        compose.onNodeWithText("Report").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ an app waiting on members

    @Test
    fun a_waiting_app_asks_a_member_who_may_answer() {
        var answer: Boolean? = null
        compose.setContent {
            TestTrackTheme {
                CrewSeatRow(
                    seat().copy(state = SeatState.CHECKING, yes = 1),
                    onInstall = {},
                    onReport = {},
                    onVote = { answer = it },
                )
            }
        }
        compose.onNodeWithText("Members decide: 1 Yes, 0 No so far", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Report").assertDoesNotExist()
        compose.onNodeWithText("It installed").performClick()
        assertEquals(true, answer)
    }

    @Test
    fun once_answered_it_says_what_you_said_and_asks_no_more() {
        compose.setContent {
            TestTrackTheme {
                CrewSeatRow(seat().copy(state = SeatState.CHECKING, myVote = false, no = 1), onVote = {})
            }
        }
        compose.onNodeWithText("You: No").assertIsDisplayed()
        compose.onNodeWithText("It installed").assertDoesNotExist()
    }

    @Test
    fun without_your_own_app_in_you_see_the_count_but_cannot_answer() {
        compose.setContent {
            TestTrackTheme { CrewSeatRow(seat().copy(state = SeatState.CHECKING), onInstall = {}, onVote = null) }
        }
        compose.onNodeWithText("Members decide", substring = true).assertIsDisplayed()
        compose.onNodeWithText("It installed").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ the report dialog

    @Test
    fun a_report_needs_a_reason_the_owner_can_act_on() {
        var sent: String? = null
        compose.setContent {
            TestTrackTheme { CrewReportDialog(seat(), onReport = { sent = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Report").assertIsNotEnabled()
        compose.onNodeWithText("Play says the item was not found").performTextInput("broken")
        compose.onNodeWithText("Report").assertIsNotEnabled()
        assertNull(sent)
    }

    @Test
    fun a_proper_reason_can_be_sent() {
        var sent: String? = null
        compose.setContent {
            TestTrackTheme { CrewReportDialog(seat(), onReport = { sent = it }, onDismiss = {}) }
        }
        compose.onNodeWithText("Play says the item was not found").performTextInput("Install fails on Android 14")
        compose.onNodeWithText("Report").assertIsEnabled().performClick()
        assertEquals("Install fails on Android 14", sent)
    }

    // ------------------------------------------------------------------ taking a seat

    @Test
    fun the_tallies_say_what_tonight_costs_and_how_much_is_left() {
        compose.setContent {
            TestTrackTheme { CrewTallies(nightly = 28, nightsLeft = 9, crews = 2) }
        }
        compose.onNodeWithText("28").assertIsDisplayed()
        compose.onNodeWithText("9").assertIsDisplayed()
        compose.onNodeWithText("2").assertIsDisplayed()
    }

    @Test
    fun your_own_row_says_you_rather_than_your_name() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(CrewMember(id = "m", name = "Rafiul", appName = "Nikosh", day = 6, mine = true))
            }
        }
        compose.onNodeWithText("You").assertIsDisplayed()
        compose.onNodeWithText("Rafiul").assertDoesNotExist()
    }

    @Test
    fun a_members_row_is_their_own_day_and_progress() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(
                    CrewMember(id = "k", name = "Kenji", appName = "Chala", day = 6, doneToday = 3, owedToday = 14, missed = 1),
                )
            }
        }
        compose.onNodeWithText("Day 6 · 3 of 14 so far · 1 missed").assertIsDisplayed()
    }

    @Test
    fun a_member_who_finished_today_says_so() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(CrewMember(id = "n", name = "Nadia", appName = "Chala", day = 2, nightDone = true))
            }
        }
        compose.onNodeWithText("Day 2 · done today").assertIsDisplayed()
    }

    @Test
    fun somebody_taken_out_is_kept_on_the_list_and_said_so() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(CrewMember(id = "i", name = "Imran", appName = "Fare Calc", day = 4, out = true))
            }
        }
        compose.onNodeWithText("Out of the run").assertIsDisplayed()
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
        compose.onNodeWithText("Put it in again").performClick()
        assertEquals(1, again)
        compose.onNodeWithText("Take it back out").assertDoesNotExist()
    }
}
