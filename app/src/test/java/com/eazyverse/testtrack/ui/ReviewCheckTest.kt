package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.Request
import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.Waiting
import com.eazyverse.testtrack.domain.pays
import com.eazyverse.testtrack.domain.requestReviewStatus
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 7 November 2026: a live review sent here because Play showed no review box (half pay, at least
 * 100 characters), and the new screenshot asked for when the owner can't find a review on Play.
 */
class ReviewCheckTest {
    private fun review(
        state: String,
        needs: Int = 0,
    ) = ApiReview(
        id = "r1",
        postId = "p1",
        state = state,
        usedMs = 30_000,
        neededMs = 30_000,
        proofRef = "drive:FirstPicture123",
        written = "",
        reason = "",
        createdAt = "2026-11-07T00:00:00+00:00",
        needsChars = needs,
    )

    private fun post(needs: Int = 0) =
        ApiPost(
            id = "p1",
            appName = "Nikosh Budget",
            packageName = "com.example.nikosh",
            ask = "",
            isLive = true,
            reward = 41,
            reviewsBought = 3,
            reviewsDone = 0,
            ownerName = "Rafiul",
            mine = false,
            createdAt = "2026-11-07T00:00:00+00:00",
            needsChars = needs,
        )

    @Test
    fun `sent without Play it asks at least 100 characters, or the request's own if more`() {
        assertEquals(100, ReviewJob(post(), review("used"), noBox = true).needsChars)
        assertEquals(250, ReviewJob(post(250), review("used", 250), noBox = true).needsChars)
        assertEquals(0, ReviewJob(post(), review("used")).needsChars)
    }

    @Test
    fun `a review the owner can't find goes back to the proof step, for a screenshot only`() {
        val job = ReviewJob(post(250), review("checking", 250))
        assertTrue(job.rechecking)
        assertEquals(ReviewStep.PROVE, job.step)
        assertEquals(0, job.needsChars)
        // It is the reviewer's to do, so it sits with the reviews in progress and under way.
        assertTrue("checking" in InProgressStates)
        assertEquals(1, Request(id = "p1", appName = "Steps", reviews = listOf(RequestReview("r1", "checking"))).underWay)
    }

    @Test
    fun `one sent without Play pays half, rounded down`() {
        assertEquals(20, Waiting(id = "r1", appName = "Steps", reward = 41, noBox = true).pays)
        assertEquals(41, Waiting(id = "r1", appName = "Steps", reward = 41).pays)
    }

    @Test
    fun `the owner sees where a check stands`() {
        assertEquals("Waiting for a new screenshot", requestReviewStatus(RequestReview("r1", "checking", checked = true)))
        assertEquals("No new screenshot came", requestReviewStatus(RequestReview("r1", "rejected", checked = true)))
        assertFalse(requestReviewStatus(RequestReview("r1", "rejected")) == "No new screenshot came")
    }
}
