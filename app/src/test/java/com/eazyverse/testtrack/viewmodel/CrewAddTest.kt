package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewAddTest {
    @Test
    fun ourCheckerDownLetsTheDetailsBeTyped() {
        assertTrue(crewLookupFailure(ApiError(503, "listing_unavailable", "x")).byHand)
    }

    @Test
    fun aListingPlayHidesHasNoWayPastForACrew() {
        val failure = crewLookupFailure(ApiError(404, "listing_not_visible", "x"))
        assertFalse(failure.byHand)
        assertTrue(failure.detail.contains("testers group"))
    }
}
