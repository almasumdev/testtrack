package com.eazyverse.testtrack.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.eazyverse.testtrack.net.ApiMyApp
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
        crewsFinished = finished,
        joinedAt = "2026-09-01T00:00:00+00:00",
    )

    private fun myApp(
        state: String,
        kind: String = "crew",
        where: String = "Crew 07",
        day: Int = 0,
        testers: Int = 0,
        name: String = "Nikosh Budget",
        id: String = "a1",
    ) = ApiMyApp(
        id = id,
        kind = kind,
        appName = name,
        packageName = "com.example.nikosh",
        state = state,
        where = where,
        day = day,
        runDays = 14,
        testers = testers,
        reviewsBought = 3,
        createdAt = "2026-09-20T00:00:00+00:00",
    )

    // ------------------------------------------------------------------ ranks

    @Test
    fun the_board_says_what_it_is_counting_before_it_counts_anybody() {
        // A rank nobody can explain is a rank nobody trusts, so the weighting is on the screen.
        compose.setContent {
            TestTrackTheme {
                RanksBody(rows = emptyList(), mine = null, openPoints = 1, reviewPoints = 5)
            }
        }
        compose.onNodeWithText("How it counts").assertIsDisplayed()
        compose.onNodeWithText("worth 5", substring = true).assertIsDisplayed()
    }

    @Test
    fun an_empty_board_invites_rather_than_apologises() {
        compose.setContent {
            TestTrackTheme {
                RanksBody(rows = emptyList(), mine = null, openPoints = 1, reviewPoints = 5)
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
                    openPoints = 1,
                    reviewPoints = 5,
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
                RanksBody(rows = listOf(me), mine = me, openPoints = 1, reviewPoints = 5)
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
                    openPoints = 1,
                    reviewPoints = 5,
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
                    openPoints = 1,
                    reviewPoints = 5,
                )
            }
        }
        compose.onNodeWithText("1 app opened").assertIsDisplayed()
    }

    @Test
    fun signed_out_the_board_says_why_it_is_empty() {
        compose.setContent {
            TestTrackTheme {
                RanksBody(
                    rows = emptyList(),
                    mine = null,
                    openPoints = 1,
                    reviewPoints = 5,
                    offline = true,
                )
            }
        }
        compose.onNodeWithText("Sign in to see the board").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ my apps

    @Test
    fun an_app_list_says_what_is_happening_to_each_one() {
        compose.setContent {
            TestTrackTheme {
                AppsBody(apps = listOf(myApp("testing", day = 6, testers = 14)))
            }
        }
        compose.onNodeWithText("Nikosh Budget").assertIsDisplayed()
        compose.onNodeWithText("Testing").assertIsDisplayed()
        compose.onNodeWithText("day 6 of 14", substring = true).assertIsDisplayed()
    }

    @Test
    fun what_is_still_going_is_kept_apart_from_what_is_over() {
        compose.setContent {
            TestTrackTheme {
                AppsBody(
                    apps =
                        listOf(
                            myApp("testing", id = "live", name = "Running One", testers = 14),
                            myApp("finished", id = "old", name = "Old One", testers = 13),
                        ),
                )
            }
        }
        compose.onNodeWithText("GOING ON").assertIsDisplayed()
        compose.onNodeWithText("FINISHED").assertIsDisplayed()
        compose.onNodeWithText("Running One").assertIsDisplayed()
        compose.onNodeWithText("Old One").assertIsDisplayed()
    }

    @Test
    fun a_rejected_app_is_the_one_row_that_asks_for_something() {
        compose.setContent { TestTrackTheme { AppsBody(apps = listOf(myApp("rejected"))) } }
        compose.onNodeWithText("Fix it").assertIsDisplayed()
        compose.onNodeWithText("Fix the listing", substring = true).assertIsDisplayed()
    }

    @Test
    fun somebody_with_nothing_up_is_told_how_to_start() {
        compose.setContent { TestTrackTheme { AppsBody(apps = emptyList()) } }
        compose.onNodeWithText("Nothing up yet").assertIsDisplayed()
        compose.onNodeWithText("Put an app into a crew", substring = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ profile

    @Test
    fun a_brand_new_account_is_told_what_to_do_rather_than_given_a_place() {
        // The first thing everybody sees. A rank of nought dressed up as a position is worse
        // than saying plainly that nothing has been counted yet.
        compose.setContent { TestTrackTheme { MeBody(profile = profile()) } }

        compose.onNodeWithText("Your record").assertIsDisplayed()
        compose.onNodeWithText("Open somebody's app", substring = true).assertIsDisplayed()
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
        compose.onNodeWithText("11th on the board").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun the_profile_shows_who_you_are_signed_in_as() {
        compose.setContent { TestTrackTheme { MeBody(profile = profile(tix = 240)) } }
        compose.onNodeWithText("Rafiul").assertIsDisplayed()
        compose.onNodeWithText("rafiul@example.com").assertIsDisplayed()
        compose.onNodeWithText("240 tix").assertIsDisplayed()
    }

    @Test
    fun the_profile_says_what_tonight_will_cost() {
        // Two runs is twenty-eight apps in one evening, and nobody is told that anywhere else.
        compose.setContent { TestTrackTheme { MeBody(profile = profile(running = 2)) } }
        compose.onNodeWithText("${CrewRules.nightly(2)}").assertIsDisplayed()
        compose.onNodeWithText("apps a night").assertIsDisplayed()
    }

    @Test
    fun signing_out_is_offered_and_asks_first() {
        var signedOut = 0
        compose.setContent {
            TestTrackTheme { MeBody(profile = profile(), onSignOut = { signedOut++ }) }
        }
        compose.onNodeWithText("Sign out").performClick()
        assertEquals(1, signedOut)
    }

    @Test
    fun signed_out_the_profile_says_what_comes_back() {
        compose.setContent { TestTrackTheme { MeBody(profile = null, offline = true) } }
        compose.onNodeWithText("Signed out").assertIsDisplayed()
    }
}
