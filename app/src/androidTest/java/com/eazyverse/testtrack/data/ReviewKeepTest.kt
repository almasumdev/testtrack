package com.eazyverse.testtrack.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eazyverse.testtrack.net.ApiReview
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** The review page opens on the last answer the server gave, offline or after a restart. */
@RunWith(AndroidJUnit4::class)
class ReviewKeepTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun clean() = ReviewKeep.clear(context)

    private fun review(state: String) =
        ApiReview(
            id = "r1",
            postId = "p1",
            state = state,
            usedMs = 12_000,
            neededMs = 30_000,
            proofRef = "",
            written = "",
            reason = "",
            createdAt = "2026-09-12T00:00:00+00:00",
        )

    @Test
    fun the_last_answer_comes_back_whole() {
        ReviewKeep.put(context, "p1", review("installed"))
        assertEquals(review("installed"), ReviewKeep.get(context, "p1"))
    }

    @Test
    fun a_newer_answer_replaces_the_older() {
        ReviewKeep.put(context, "p1", review("installed"))
        ReviewKeep.put(context, "p1", review("submitted"))
        assertEquals("submitted", ReviewKeep.get(context, "p1")!!.state)
    }

    @Test
    fun signing_out_forgets_them() {
        ReviewKeep.put(context, "p1", review("used"))
        ReviewKeep.clear(context)
        assertNull(ReviewKeep.get(context, "p1"))
    }
}
