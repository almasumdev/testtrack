package com.eazyverse.testtrack.ui

import com.eazyverse.testtrack.net.ApiError
import com.eazyverse.testtrack.viewmodel.lookupFailure
import com.eazyverse.testtrack.viewmodel.packageFrom
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

    @Test
    fun aClosedLookupThatCannotRunOffersTypingTheDetailsInAmber() {
        val failure = lookupFailure(ApiError(503, "listing_unavailable", "x"), closed = true)
        assertTrue(failure.byHand)
        assertTrue(failure.ours)
        assertFalse(lookupFailure(ApiError(503, "listing_unavailable", "x"), closed = false).byHand)
    }

    @Test
    fun aHiddenClosedListingOffersTypingTheDetailsButStaysRed() {
        val failure = lookupFailure(ApiError(404, "listing_not_visible", "x"), closed = true)
        assertTrue(failure.byHand)
        assertFalse(failure.ours)
        assertFalse(lookupFailure(ApiError(404, "listing_not_found", "x"), closed = true).byHand)
    }

    @Test
    fun onAddAnAppTheWayOnIsThePhoneNotTyping() {
        for (code in listOf("listing_not_visible", "listing_unavailable")) {
            val failure = lookupFailure(ApiError(503, code, "x"), closed = true, fromPhone = true)
            assertTrue(failure.byHand)
            assertTrue(failure.detail.contains("pick it from this phone"))
            assertFalse(failure.detail.contains("details yourself"))
        }
    }

    @Test
    fun thePackageIsReadFromALinkAsTheServerReadsIt() {
        assertEquals("com.example.app", packageFrom(" com.example.app "))
        assertEquals("com.example.app", packageFrom("https://play.google.com/store/apps/details?id=com.example.app&hl=en"))
        assertEquals("com.example.app", packageFrom("play.google.com/store/apps/details?hl=en&id=com.example.app"))
        assertEquals("com.example.app", packageFrom("market://details?id=com.example.app"))
        assertEquals(null, packageFrom("https://example.com/?id=com.example.app"))
        assertEquals(null, packageFrom("not a package"))
        assertEquals(null, packageFrom("com"))
    }
}
