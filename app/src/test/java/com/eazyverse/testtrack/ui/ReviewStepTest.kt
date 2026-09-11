package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the stepper thinks somebody is.
 *
 * Pure arithmetic over what the server said, which is why it can be tested without a device. The
 * rule it is really guarding: the step comes from the server's state every time, so a phone that
 * was closed halfway through opens on the step it actually left off at rather than the first one.
 */
class ReviewStepTest {
    private fun review(
        state: String,
        used: Long = 0,
        needed: Long = 30_000,
    ) = ApiReview(
        id = "r1",
        postId = "p1",
        state = state,
        usedMs = used,
        neededMs = needed,
        proofRef = "",
        written = "",
        reason = "",
        createdAt = "2026-09-12T00:00:00+00:00",
    )

    private val post =
        ApiPost(
            id = "p1",
            appName = "Nikosh Budget",
            packageName = "com.example.nikosh",
            ask = "Is the total readable?",
            isLive = true,
            reward = 40,
            reviewsBought = 3,
            reviewsDone = 0,
            ownerName = "Rafiul",
            mine = false,
            createdAt = "2026-09-12T00:00:00+00:00",
        )

    @Test
    fun each_server_state_lands_on_its_own_step() {
        assertEquals(ReviewStep.INSTALL, ReviewStep.of(review("claimed")))
        assertEquals(ReviewStep.USE, ReviewStep.of(review("installed")))
        assertEquals(ReviewStep.LEAVE, ReviewStep.of(review("used")))
        assertEquals(ReviewStep.WAIT, ReviewStep.of(review("submitted")))
        assertEquals(ReviewStep.WAIT, ReviewStep.of(review("approved")))
    }

    @Test
    fun an_unknown_state_waits_rather_than_starting_again() {
        // A state this build has never heard of means the server is ahead of the app. Showing the
        // first step again would invite somebody to redo work they have already done.
        assertEquals(ReviewStep.WAIT, ReviewStep.of(review("something_new")))
    }

    @Test
    fun the_usage_bar_is_a_fraction_of_what_is_needed() {
        assertEquals(0f, ReviewJob(post, review("installed", used = 0)).usedFraction, 0.001f)
        assertEquals(0.5f, ReviewJob(post, review("installed", used = 15_000)).usedFraction, 0.001f)
        assertEquals(1f, ReviewJob(post, review("used", used = 30_000)).usedFraction, 0.001f)
    }

    @Test
    fun the_bar_never_overflows_when_somebody_stays_longer() {
        val long = ReviewJob(post, review("used", used = 600_000))
        assertEquals(1f, long.usedFraction, 0.001f)
        assertEquals(0, long.secondsLeft)
    }

    @Test
    fun seconds_left_counts_down_and_stops_at_zero() {
        assertEquals(30, ReviewJob(post, review("installed", used = 0)).secondsLeft)
        assertEquals(10, ReviewJob(post, review("installed", used = 20_000)).secondsLeft)
        assertEquals(0, ReviewJob(post, review("used", used = 30_000)).secondsLeft)
    }

    @Test
    fun a_job_is_settled_once_it_is_out_of_the_reviewers_hands() {
        assertFalse(ReviewJob(post, review("claimed")).settled)
        assertFalse(ReviewJob(post, review("used")).settled)
        for (state in listOf("submitted", "approved", "rejected", "disputed")) {
            assertTrue(state, ReviewJob(post, review(state)).settled)
        }
    }

    @Test
    fun every_settled_state_has_something_to_say() {
        for (state in listOf("submitted", "approved", "rejected", "disputed")) {
            assertTrue(state, reviewOutcome(state).isNotBlank())
        }
    }
}
