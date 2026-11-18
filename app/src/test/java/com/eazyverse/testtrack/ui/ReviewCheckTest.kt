package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.Evidence
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
        proofRef = "3f1c9a52-7d0e-4c1b-9a6f-2b8e5d4c7a10",
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

    @Test
    fun `closed and live usage requirements keep their own finish windows`() {
        assertEquals(120_000L, previewOf(post().copy(isLive = false)).neededMs)
        assertEquals(180_000L, previewOf(post().copy(isLive = true)).neededMs)
        assertEquals(24, FeedRules.CLAIM_HOURS)
        assertEquals(72, FeedRules.LIVE_CLAIM_HOURS)
    }

    @Test
    fun `the server's half reward choice survives restoring a review`() {
        val restored = ReviewJob(post(), review("submitted").copy(noBox = true))
        assertTrue(restored.noBox)
        assertTrue(restored.settled)
        assertEquals(20, restored.reward)
    }

    @Test
    fun `written feedback still requires text when optional counts are absent`() {
        val written = ReviewJob(post().copy(feedback = "written", needsChars = 0), review("used", 0))
        assertEquals(100, written.needsChars)
        assertFalse(written.textEnough("a".repeat(99)))
        assertTrue(written.textEnough("a".repeat(100)))
    }

    @Test
    fun `a refused review may be answered once, and the members see both sides`() {
        assertTrue(ReviewJob(post(), review("disputed")).canReply)
        assertFalse(ReviewJob(post(), review("disputed").copy(repliedAt = "2026-11-07T10:00:00+00:00")).canReply)
        assertFalse(ReviewJob(post(), review("submitted")).canReply)

        assertTrue(Evidence().isEmpty)
        assertFalse(Evidence(ownerProofRef = "8b2d6e41-5a3c-4f7e-b1d9-0c6a4e2f9b35").isEmpty)
        assertFalse(Evidence(reply = "It is there.").isEmpty)
    }
}
