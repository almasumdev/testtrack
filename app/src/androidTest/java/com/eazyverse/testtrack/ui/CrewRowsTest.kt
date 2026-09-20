package com.eazyverse.testtrack.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The crew rows, as a member meets them.
 *
 * [CrewWireTest] covers what the app works out from the server's answer. This covers what is then
 * drawn, which is a different question: a row that computes the right thing and offers the wrong
 * button is still a bug, and the last time these screens went untested the review stepper shipped
 * with every step ticked.
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
        greens: Int = 0,
        reds: Int = 0,
        mine: Boolean = false,
        judged: Boolean = false,
    ) = CrewSeat(
        id = "s1",
        appName = "Nikosh Budget",
        owner = "Rafiul",
        crew = "Crew 07",
        greens = greens,
        reds = reds,
        mine = mine,
        judged = judged,
    )

    // ------------------------------------------------------------------ tonight's row

    @Test
    fun an_app_owed_tonight_offers_both_answers() {
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

        compose.onNodeWithText("It will not install").assertIsDisplayed().performClick()
        assertEquals(1, reported)
    }

    @Test
    fun an_app_already_opened_stops_asking() {
        // Offering the button again is how somebody ends up opening the same app twice and
        // thinking they have done two.
        compose.setContent {
            TestTrackTheme { CrewAppRow(app(done = true), onOpen = {}, onReport = {}) }
        }
        compose.onNodeWithText("Open").assertDoesNotExist()
        compose.onNodeWithText("It will not install").assertDoesNotExist()
    }

    @Test
    fun an_app_you_reported_is_shown_as_settled_rather_than_owed() {
        compose.setContent {
            TestTrackTheme { CrewAppRow(app(reported = true), onOpen = {}, onReport = {}) }
        }
        compose.onNodeWithText("Open").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ seats

    @Test
    fun a_seat_shows_the_app_and_who_put_it_in() {
        compose.setContent {
            TestTrackTheme { CrewSeatRow(seat()) }
        }
        compose.onNodeWithText("Nikosh Budget").assertIsDisplayed()
        compose.onNodeWithText("Rafiul", substring = true).assertIsDisplayed()
    }

    @Test
    fun a_seat_carries_whatever_control_the_list_around_it_needs() {
        // The trailing slot is how one row serves the lobby, where it is answered on, and a
        // crew's own page, where it only reports its state.
        var answered = 0
        compose.setContent {
            TestTrackTheme {
                CrewSeatRow(
                    seat(),
                    trailing = {
                        ActionChip(
                            "Try it",
                            MaterialTheme.colorScheme.onPrimaryContainer,
                            MaterialTheme.colorScheme.primaryContainer,
                        ) { answered++ }
                    },
                )
            }
        }
        compose.onNodeWithText("Try it").performClick()
        assertEquals(1, answered)
    }

    // ------------------------------------------------------------------ the verdict dialog

    @Test
    fun the_reject_button_is_held_back_until_you_have_one_working() {
        // Play answers "not found" to any tester whose own setup is unfinished, so a brand new
        // member's first evening would otherwise be fourteen reds that mean nothing.
        var answer: Boolean? = null
        compose.setContent {
            TestTrackTheme {
                CrewVerdictDialog(
                    seat = seat(),
                    mayReject = false,
                    onVerdict = { answer = it },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Play could not find it").assertIsNotEnabled()
        assertNull(answer)

        compose.onNodeWithText("It installed").performClick()
        assertEquals(true, answer)
    }

    @Test
    fun once_you_have_one_working_a_report_is_allowed() {
        var answer: Boolean? = null
        compose.setContent {
            TestTrackTheme {
                CrewVerdictDialog(
                    seat = seat(),
                    mayReject = true,
                    onVerdict = { answer = it },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("Play could not find it").performClick()
        assertEquals(false, answer)
    }

    // ------------------------------------------------------------------ taking a seat

    @Test
    fun a_seat_cannot_be_taken_without_a_package_name() {
        // Fourteen people are about to try to install this exact string. A seat without one is a
        // seat nobody can verify, so the button stays down rather than failing at the server.
        var taken: Triple<String, String, String>? = null
        compose.setContent {
            TestTrackTheme {
                CrewSeatDialog(
                    crew = "Crew 07",
                    nightly = CrewRules.NIGHTLY,
                    onTake = { name, pkg, notes -> taken = Triple(name, pkg, notes) },
                    onDismiss = {},
                )
            }
        }

        compose.onNodeWithText("Take it").assertIsNotEnabled()
        assertNull(taken)
        compose.onNodeWithText("Package name").assertIsDisplayed()
    }

    @Test
    fun the_dialog_says_what_the_fortnight_will_cost_before_anybody_agrees_to_it() {
        // The product puts no limit on how many crews somebody joins, so saying the figure out
        // loud beforehand is the whole of its defence against people taking on too much.
        compose.setContent {
            TestTrackTheme {
                CrewSeatDialog(
                    crew = "Crew 07",
                    nightly = 28,
                    onTake = { _, _, _ -> },
                    onDismiss = {},
                )
            }
        }
        compose.onNodeWithText("28 apps a night", substring = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ the counts

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
                CrewMemberRow(
                    CrewMember(name = "Rafiul", appName = "Nikosh Budget", days = 6, mine = true),
                )
            }
        }
        compose.onNodeWithText("You").assertIsDisplayed()
        compose.onNodeWithText("Rafiul").assertDoesNotExist()
    }

    @Test
    fun a_members_row_says_how_their_fortnight_is_going() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(
                    CrewMember(name = "Rafiul", appName = "Nikosh Budget", days = 6, missed = 1),
                )
            }
        }
        compose.onNodeWithText("Rafiul").assertIsDisplayed()
        compose.onNodeWithText("6 days · 1 missed").assertIsDisplayed()
    }

    @Test
    fun day_one_is_written_out_rather_than_having_an_s_glued_on() {
        // The day this line is read most, and "1 days" on it is the first thing anybody notices.
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(CrewMember(name = "Nadia", appName = "Chala", days = 1))
            }
        }
        compose.onNodeWithText("1 day, no gaps").assertIsDisplayed()
    }

    @Test
    fun somebody_who_left_is_kept_on_the_list_and_said_so() {
        compose.setContent {
            TestTrackTheme {
                CrewMemberRow(CrewMember(name = "Imran", appName = "Fare Calc", days = 4, out = true))
            }
        }
        compose.onNodeWithText("Left on day 4").assertIsDisplayed()
    }
}

/**
 * Giving a seat back.
 *
 * The API has supported it since crews shipped and nothing in the app called it, so somebody who
 * put the wrong app in was stuck with it until the crew filled. The rule that matters is when the
 * offer disappears: once a crew is running, fourteen people are relying on that seat.
 */
@RunWith(AndroidJUnit4::class)
class CrewLeaveTest {
    @get:Rule
    val compose = createComposeRule()

    private fun crewWith(phase: CrewPhase): Crew {
        val mine =
            CrewSeat(
                id = "mine",
                appName = "Nikosh Budget",
                owner = "You",
                crew = "Crew 07",
                greens = 1,
                mine = true,
            )
        return Crew(id = "c1", name = "Crew 07", phase = phase, seats = listOf(mine))
    }

    @Test
    fun a_seat_can_be_taken_back_out_before_the_crew_starts() {
        var left = 0
        compose.setContent {
            TestTrackTheme {
                CrewLobbyPhase(crewWith(CrewPhase.LOBBY), onJudge = {}, onLeave = { left++ })
            }
        }
        compose.onNodeWithText("Take it back out").assertIsDisplayed().performClick()
        assertEquals(1, left)
    }

    @Test
    fun once_the_run_has_started_it_is_not_offered() {
        // The server refuses it then, and a button that is going to be refused is worse than no
        // button: it reads as something the product allows and then does not.
        compose.setContent {
            TestTrackTheme { CrewLobbyPhase(crewWith(CrewPhase.RUN), onJudge = {}) }
        }
        compose.onNodeWithText("Take it back out").assertDoesNotExist()
    }

    @Test
    fun a_finished_crew_is_a_record_and_nothing_can_be_withdrawn_from_it() {
        compose.setContent {
            TestTrackTheme { CrewLobbyPhase(crewWith(CrewPhase.RECORD), onJudge = {}) }
        }
        compose.onNodeWithText("Take it back out").assertDoesNotExist()
    }
}
