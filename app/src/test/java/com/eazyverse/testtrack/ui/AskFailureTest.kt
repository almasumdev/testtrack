package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AskFailureTest {
    @Test
    fun invalidLinkAndMissingListingMarkTheField() {
        for (code in listOf("package_unreadable", "listing_not_found")) {
            assertTrue(lookupFailure(ApiError(404, code, "server message")).invalidLink)
        }
    }

    @Test
    fun closedTrackAccessDoesNotBlameTheLink() {
        val failure = lookupFailure(ApiError(404, "listing_not_visible", "server message"))
        assertFalse(failure.invalidLink)
        assertTrue(failure.detail.contains("testers group"))
    }

    @Test
    fun connectionFailureHasRecoveryCopy() {
        val failure = lookupFailure(IOException("internal connection detail"))
        assertEquals("Couldn't connect", failure.title)
        assertFalse(failure.invalidLink)
        assertFalse(failure.detail.contains("internal connection detail"))
    }

    @Test
    fun serviceFailurePreservesTheActualReason() {
        val detail = "Looking up closed testing apps is not set up yet."
        val failure = lookupFailure(ApiError(503, "listing_unavailable", detail))
        assertFalse(failure.invalidLink)
        assertEquals(detail, failure.detail)
    }
}
