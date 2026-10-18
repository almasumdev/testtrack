package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.net.ApiProfile
import com.eazyverse.testtrack.net.BoardRow
import com.eazyverse.testtrack.ui.theme.TestTrackTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ranks, My apps and Profile, drawn.
 *
 * Each body takes plain values rather than a view model, which is what lets every state it can be
 * in be put on a device: empty, loading, signed out, and the awkward ones in between.
 *
 * The case worth having on each is the one a person notices first. On the board it is finding
 * yourself when you are nowhere near the top. On the apps list it is a row saying what is
 * happening rather than only that something is. On the profile it is what a brand new account
 * sees, because that is the first thing everybody sees.
 */
@RunWith(AndroidJUnit4::class)
class StandingScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private fun row(
        id: String,
        name: String,
        place: Int,
        opens: Int = 0,
        reviews: Int = 0,
    ) = BoardRow(
        id = id,
        name = name,
        place = place,
        opens = opens,
        reviews = reviews,
        points = opens + reviews * 5,
    )

    private fun profile(
        opens: Int = 0,
        reviews: Int = 0,
        place: Int = 0,
        tix: Int = 0,
        running: Int = 0,
        filled: Int = running,
        finished: Int = 0,
        name: String = "Rafiul",
    ) = ApiProfile(
        id = "u1",
        email = "rafiul@example.com",
        displayName = name,
        timezone = "Asia/Dhaka",
        tix = tix,
        opens = opens,
        reviews = reviews,
        points = opens + reviews * 5,
        place = place,
        crewsRunning = running,
        crewsFilled = filled,
        crewsFinished = finished,
        joinedAt = "2026-09-01T00:00:00+00:00",
    )

    private fun myApp(
        crew: AppCrewStatus? = null,
        feedback: AppFeedbackStatus? = null,
        name: String = "Nikosh Budget",
        id: String = "a1",
        verifiedBy: String? = "play",
    ) = AppEntry(
        id = id,
        pkg = "com.example.nikosh",
        name = name,
        crew = crew,
        feedback = feedback,
        verifiedBy = verifiedBy,
    )

    // ------------------------------------------------------------------ ranks

    @Test
    fun an_empty_board_invites_rather_than_apologises() {
        // The robot moves for as long as it is on screen, so the clock is held: left to run, the
        // test would wait for an animation that never ends.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TestTrackTheme {
                RanksBody(rows = emptyList(), mine = null)
            }
        }
        compose.onNodeWithText("Nobody has started yet").assertIsDisplayed()
    }

    @Test
    fun a_reader_far_down_the_list_can_still_find_themselves() {
        // The whole reason to show a board. Being fortieth and able to see it is useful; being
        // fortieth and looking at three strangers is not.
        // The numbers are deliberately unlike each other. Reusing one would have this pass on a
        // screen that drew the reader's place where somebody else's score belongs.
        val top = listOf(row("a", "Tanvir", 1, opens = 37), row("b", "Habib", 2, opens = 21))
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows = top,
                    mine = row("me", "Rafiul", 40, opens = 2),
                )
            }
        }
        compose.onNodeWithText("Tanvir").assertIsDisplayed()
        compose.onNodeWithText("You").assertIsDisplayed()
        // Their place, pinned at the bottom, and the work behind it.
        compose.onNodeWithText("40").assertIsDisplayed()
        compose.onNodeWithText("2 apps opened").assertIsDisplayed()
    }

    @Test
    fun a_reader_already_in_the_list_is_not_shown_twice() {
        val me = row("me", "Rafiul", 1, opens = 40)
        compose.setContent {
            TestTrackTheme {
                RanksBody(rows = listOf(me), mine = me)
            }
        }
        assertEquals(
            1,
            compose.onAllNodes(hasText("You")).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun a_row_says_the_work_and_not_only_the_score() {
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows = listOf(row("a", "Tanvir", 1, opens = 12, reviews = 2)),
                    mine = null,
                )
            }
        }
        compose.onNodeWithText("12 apps opened · 2 reviews").assertIsDisplayed()
    }

    @Test
    fun one_open_is_written_out_rather_than_having_an_s_glued_on() {
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows = listOf(row("a", "Tanvir", 1, opens = 1)),
                    mine = null,
                )
            }
        }
        compose.onNodeWithText("1 app opened").assertIsDisplayed()
    }

    @Test
    fun a_board_that_could_not_be_reached_blames_the_network_and_not_the_reader() {
        // The robot moves for as long as it is on screen, so the clock is held: left to run, the
        // test would wait for an animation that never ends.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows = emptyList(),
                    mine = null,
                    offline = true,
                )
            }
        }
        compose.onNodeWithText("The board did not load").assertIsDisplayed()
    }

    @Test
    fun the_three_at_the_top_stand_on_a_podium() {
        // Second, first, third. The order on screen is the shape of a podium and not the order of
        // the list, so a test that only checked the names would pass on a plain list of three.
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows =
                        listOf(
                            row("a", "Tanvir", 1, opens = 37),
                            row("b", "Habib", 2, opens = 21),
                            row("c", "Nadia", 3, opens = 15),
                        ),
                    mine = null,
                )
            }
        }

        compose.onNodeWithText("Tanvir").assertIsDisplayed()
        compose.onNodeWithText("Habib").assertIsDisplayed()
        compose.onNodeWithText("Nadia").assertIsDisplayed()
        // The blocks carry the place, so all three numbers are on screen without a list of rows.
        compose.onNodeWithText("1").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun the_rest_of_the_board_is_headed_standings() {
        val board = (1..6).map { row("id$it", "Name $it", it, opens = 10 - it) }
        compose.setContent { TestTrackTheme { RanksBody(rows = board, mine = null) } }

        // The list under the podium is named for what it is, and counts the whole board.
        compose.onNodeWithText("Standings").assertIsDisplayed()
        compose.onNodeWithText("Top 6").assertIsDisplayed()
    }

    @Test
    fun a_reader_on_the_podium_is_not_also_pinned_underneath_it() {
        val me = row("me", "Rafiul", 1, opens = 40)
        val board = listOf(me, row("b", "Habib", 2, opens = 21), row("c", "Nadia", 3, opens = 15))
        compose.setContent { TestTrackTheme { RanksBody(rows = board, mine = me) } }
        compose.waitForIdle()

        // The top three live inside one podium item rather than three rows, so the bar has to
        // watch for the podium and not for an id. Getting that wrong pins the winner to the
        // bottom of their own board.
        assertEquals(1, compose.onAllNodes(hasText("You")).fetchSemanticsNodes().size)
    }

    // ------------------------------------------------------------------ my apps

    @Test
    fun an_app_says_what_is_happening_to_it_in_badges() {
        compose.setContent {
            TestTrackTheme {
                AppsBody(
                    apps =
                        listOf(
                            myApp(
                                crew = AppCrewStatus("testing", "c1", "Crew 07", day = 6, runDays = 14),
                                feedback = AppFeedbackStatus("collecting", "p1", done = 2, bought = 3),
                            ),
                        ),
                )
            }
        }
        compose.onNodeWithText("Nikosh Budget").assertIsDisplayed()
        compose.onNodeWithContentDescription("Closed test").assertIsDisplayed()
        compose.onNodeWithText("Day 6 of 14 · Crew 07").assertIsDisplayed()
        compose.onNodeWithText("Feedback 2 of 3").assertIsDisplayed()
    }

    @Test
    fun every_app_is_in_one_list_whatever_it_is_doing() {
        compose.setContent {
            TestTrackTheme {
                AppsBody(
                    apps =
                        listOf(
                            myApp(crew = AppCrewStatus("testing", "c1", "Crew 07", day = 3), id = "live", name = "Running One"),
                            myApp(crew = AppCrewStatus("finished", "c2", "Crew 02"), id = "old", name = "Old One"),
                            myApp(id = "new", name = "New One", verifiedBy = null),
                        ),
                )
            }
        }
        compose.onNodeWithText("Your apps").assertIsDisplayed()
        compose.onNodeWithText("Running One").assertIsDisplayed()
        compose.onNodeWithText("Old One").assertIsDisplayed()
        compose.onNodeWithText("Crew test done").assertIsDisplayed()
        compose.onNodeWithText("Not verified").assertIsDisplayed()
    }

    @Test
    fun an_app_sent_back_says_to_fix_it() {
        compose.setContent {
            TestTrackTheme { AppsBody(apps = listOf(myApp(crew = AppCrewStatus("rejected", "c1", "Crew 07")))) }
        }
        compose.onNodeWithText("Sent back · fix it").assertIsDisplayed()
    }

    @Test
    fun somebody_with_nothing_added_is_told_how_to_start() {
        // The robot moves for as long as it is on screen, so the clock is held: left to run, the
        // test would wait for an animation that never ends.
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTrackTheme { AppsBody(apps = emptyList(), onAdd = {}) } }
        compose.onNodeWithText("Nothing added yet").assertIsDisplayed()
        compose.onNodeWithText("Add an app").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ profile

    @Test
    fun a_brand_new_account_is_told_what_to_do_rather_than_given_a_place() {
        // The first thing everybody sees. A rank of nought dressed up as a position is worse
        // than saying plainly that nothing has been counted yet.
        compose.setContent { TestTrackTheme { MeBody(profile = profile()) } }

        compose.onNodeWithText("Your record").assertIsDisplayed()
        compose.onNodeWithText("Open the apps in your crew", substring = true).assertIsDisplayed()
        assertTrue(
            "An unranked account must not be given a place",
            compose
                .onAllNodes(hasText("on the board", substring = true))
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun an_account_that_has_done_something_is_shown_where_it_sits() {
        compose.setContent {
            TestTrackTheme { MeBody(profile = profile(opens = 12, reviews = 1, place = 11)) }
        }
        compose.onNodeWithText("#11").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun the_profile_shows_who_you_are_signed_in_as() {
        compose.setContent { TestTrackTheme { MeBody(profile = profile(tix = 240)) } }
        compose.onNodeWithText("Rafiul").assertIsDisplayed()
        compose.onNodeWithText("rafiul@example.com").assertIsDisplayed()
        // The balance is the header's, over every tab, and is not repeated here.
        compose.onNodeWithText("Since Sep 2026").assertIsDisplayed()
    }

    @Test
    fun the_profile_says_what_tonight_will_cost() {
        // Two runs is twenty-eight apps in one evening, and nobody is told that anywhere else.
        compose.setContent { TestTrackTheme { MeBody(profile = profile(running = 2)) } }
        compose.onNodeWithText("${CrewRules.nightly(2)}").assertIsDisplayed()
        compose.onNodeWithText("Apps a night").assertIsDisplayed()
    }

    @Test
    fun a_crew_still_filling_costs_nothing_a_night_yet() {
        compose.setContent { TestTrackTheme { MeBody(profile = profile(running = 2, filled = 1)) } }
        compose.onNodeWithText("${CrewRules.nightly(1)}").assertIsDisplayed()
    }

    @Test
    fun signing_out_is_offered_and_asks_first() {
        var signedOut = 0
        compose.setContent {
            TestTrackTheme { MeBody(profile = profile(), onSignOut = { signedOut++ }) }
        }
        // At the foot of the page, under the settings, so it is scrolled to before it is pressed.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Sign out"))
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(1, signedOut)
    }

    /*
     * Two tests where there was one, because these are two different facts.
     *
     * The old one passed `offline = true` and asserted the signed-out words, which was harmless
     * only while both branches said the same thing. They do not any more: one of them is the app
     * admitting it does not know who you are, and the other is the app admitting it could not
     * reach the server, and telling somebody to sign in when their connection dropped is the
     * fault this pair exists to stop coming back.
     */

    @Test
    fun signed_out_the_profile_shows_where_everybody_starts() {
        compose.setContent { TestTrackTheme { MeBody(profile = null, signedOut = true) } }
        compose.onNodeWithText("Sign in to see your profile").assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
    }

    @Test
    fun a_profile_that_could_not_be_reached_blames_the_network() {
        // The robot moves for as long as it is on screen, so the clock is held: left to run, the
        // test would wait for an animation that never ends.
        compose.mainClock.autoAdvance = false
        compose.setContent { TestTrackTheme { MeBody(profile = null, offline = true) } }
        compose.onNodeWithText("Your record did not load").assertIsDisplayed()
    }
}

/**
 * Changing how you appear.
 *
 * Removed along with the old Firestore profile and gone for a while, so an account was stuck with
 * whatever Google last called it. These cover the two things the dialog has to get right: a name
 * is required, and the picture is not.
 */
@RunWith(AndroidJUnit4::class)
class MeEditDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun the_profile_offers_a_way_to_change_itself() {
        var edited = 0
        compose.setContent {
            TestTrackTheme {
                MeBody(
                    profile =
                        ApiProfile(
                            id = "u1",
                            email = "rafiul@example.com",
                            displayName = "Rafiul",
                            timezone = "Asia/Dhaka",
                            tix = 0,
                            opens = 0,
                            reviews = 0,
                            points = 0,
                            place = 0,
                            crewsRunning = 0,
                            crewsFinished = 0,
                            joinedAt = "2026-09-01T00:00:00+00:00",
                        ),
                    onEdit = { edited++ },
                )
            }
        }
        // It is in the Account group, under the fold on a phone.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Edit profile"))
        compose.onNodeWithText("Edit profile").performClick()
        assertEquals(1, edited)
    }

    @Test
    fun a_name_is_required_and_spaces_are_not_one() {
        var saved: String? = null
        compose.setContent {
            TestTrackTheme {
                MeEditDialog(name = "Rafiul", onSave = { n, _ -> saved = n }, onDismiss = {})
            }
        }

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNodeWithText("Save").assertIsNotEnabled()

        // Three spaces clear a length check and arrive as nothing, which is what the server had
        // to be taught too.
        compose.onNode(hasSetTextAction()).performTextInput("   ")
        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("That is only spaces.").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("Nadia")
        compose.onNodeWithText("Save").performClick()
        assertEquals("Nadia", saved)
    }

    @Test
    fun a_picture_is_optional_and_says_so() {
        compose.setContent {
            TestTrackTheme {
                MeEditDialog(name = "Rafiul", onSave = { _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Change photo").assertIsDisplayed()
        // And it saves without one, since the name is the only required half.
        compose.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun nothing_can_be_saved_twice_while_the_first_save_is_in_flight() {
        compose.setContent {
            TestTrackTheme {
                MeEditDialog(name = "Rafiul", busy = true, onSave = { _, _ -> }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Saving").assertIsNotEnabled()
    }
}
