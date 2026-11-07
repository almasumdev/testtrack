package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.domain.RequestReview
import com.eazyverse.testtrack.domain.requestReviewStatus
import com.eazyverse.testtrack.domain.textUseLabel
import com.eazyverse.testtrack.net.ApiPost
import com.eazyverse.testtrack.net.PostIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Written and detailed feedback, 7 November 2026: the same numbers as the server's rules.FEEDBACK,
 * a request from before reading as stars, and accepting a refused review while it is still open.
 */
class FeedbackAskTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `each kind pays its least and asks its characters, as the server does`() {
        assertEquals(20 to 0, FeedRules.Feedback.STARS.least to FeedRules.Feedback.STARS.chars)
        assertEquals(35 to 100, FeedRules.Feedback.WRITTEN.least to FeedRules.Feedback.WRITTEN.chars)
        assertEquals(50 to 250, FeedRules.Feedback.DETAILED.least to FeedRules.Feedback.DETAILED.chars)
        assertEquals(FeedRules.Feedback.STARS, FeedRules.Feedback.of("anything else"))
    }

    @Test
    fun `characters are counted as the server counts them`() {
        assertEquals(11, plainLength("  hello \n\n  world  "))
        assertEquals(0, plainLength("   "))
    }

    @Test
    fun `a request from an older server reads as stars with no text`() {
        val post =
            json.decodeFromString<ApiPost>(
                """{"id":"p1","app_name":"Steps","package_name":"com.example.steps","ask":"","is_live":true,"reward":20,
                |"reviews_bought":3,"reviews_done":0,"owner_name":"","mine":false,"created_at":"2026-11-01T00:00:00+00:00"}
                """.trimMargin(),
            )
        assertEquals("stars", post.feedback)
        assertEquals(0, post.needsChars)
    }

    @Test
    fun `a stars request is sent as it always was`() {
        val sent = Json.encodeToString(PostIn(appName = "Steps", packageName = "com.example.steps", ask = "", reward = 20, isLive = true))
        assertTrue(sent, "ready_texts" !in sent || "\"ready_texts\":[]" in sent)
    }

    @Test
    fun `a refused review can be accepted only while the members are deciding`() {
        assertTrue(RequestReview("r1", "disputed", outcome = "open").canAcceptLater)
        assertFalse(RequestReview("r1", "rejected", outcome = "upheld").canAcceptLater)
        assertFalse(RequestReview("r1", "approved", outcome = "accepted").canAcceptLater)
        assertEquals("Accepted after all", requestReviewStatus(RequestReview("r1", "approved", outcome = "accepted")))
    }

    @Test
    fun `how a reviewer used the ready review is said in words`() {
        assertEquals("Used your ready review", textUseLabel("used"))
        assertEquals("In their own words", textUseLabel("own"))
        assertNull(textUseLabel(null))
    }
}
