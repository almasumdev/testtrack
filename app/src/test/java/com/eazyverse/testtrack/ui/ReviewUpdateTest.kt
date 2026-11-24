package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.ApiReview
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reviewer's one swapped screenshot: what an older server leaves out, and when it may be sent. */
class ReviewUpdateTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun review(
        state: String,
        canUpdate: Boolean = false,
    ) = ApiReview(
        id = "r1",
        postId = "p1",
        state = state,
        usedMs = 30_000,
        neededMs = 30_000,
        proofRef = "3f1c9a52-7d0e-4c1b-9a6f-2b8e5d4c7a10",
        written = "",
        reason = "",
        createdAt = "2026-11-24T00:00:00+00:00",
        canUpdate = canUpdate,
    )

    private fun post() =
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
            createdAt = "2026-11-24T00:00:00+00:00",
        )

    @Test
    fun `a review from an older server has no update`() {
        val old =
            """{"id":"r1","post_id":"p1","state":"submitted","used_ms":1,"needed_ms":1,"proof_ref":"a","written":"","reason":"","created_at":"2026-11-24T00:00:00+00:00"}"""
        val review = json.decodeFromString<ApiReview>(old)
        assertFalse(review.canUpdate)
        assertEquals("", review.firstProofRef)
        assertEquals("", review.updateNote)
        assertNull(review.proofUpdatedAt)
    }

    @Test
    fun `the new keys are read`() {
        val sent =
            """{"id":"r1","post_id":"p1","state":"submitted","used_ms":1,"needed_ms":1,"proof_ref":"b","written":"","reason":"","created_at":"2026-11-24T00:00:00+00:00","first_proof_ref":"a","update_note":"Here it is","proof_updated_at":"2026-11-24T01:00:00+00:00","can_update":true}"""
        val review = json.decodeFromString<ApiReview>(sent)
        assertTrue(review.canUpdate)
        assertEquals("a", review.firstProofRef)
        assertEquals("Here it is", review.updateNote)
        assertEquals("2026-11-24T01:00:00+00:00", review.proofUpdatedAt)
    }

    @Test
    fun `it can be updated only while submitted`() {
        assertTrue(ReviewJob(post(), review("submitted", canUpdate = true)).canUpdate)
        assertFalse(ReviewJob(post(), review("submitted")).canUpdate)
        for (state in listOf("used", "checking", "disputed", "approved", "rejected")) {
            assertFalse(state, ReviewJob(post(), review(state, canUpdate = true)).canUpdate)
        }
    }

    @Test
    fun `sending a new screenshot asks for no text`() {
        assertEquals(100, ReviewJob(post(), review("submitted"), noBox = true).needsChars)
        assertEquals(0, ReviewJob(post(), review("submitted"), noBox = true, updating = true).needsChars)
    }
}
