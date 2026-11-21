package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrewPicturesRefusalTest {
    private fun error(
        status: Int,
        code: String,
    ) = ApiError(status, code, "message")

    @Test
    fun aRefusalIsTheServerSayingNoNotABadConnection() {
        assertTrue(isRefusal(error(403, "not_allowed")))
        assertTrue(isRefusal(error(404, "not_found")))
    }

    @Test
    fun aServerFailureOrClearedCrewIsNotARefusal() {
        assertFalse(isRefusal(error(500, "internal")))
        assertFalse(isRefusal(error(410, "details_gone")))
        assertFalse(isRefusal(error(401, "unauthenticated")))
    }
}
