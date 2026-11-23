package com.eazyverse.testtrack.viewmodel

import com.eazyverse.testtrack.net.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlreadyVolunteeringTest {
    @Test fun oldServerRefusalOfASecondCrewIsKnown() {
        val refusal = ApiError(409, "conflict", "You are already volunteering in Crew 4.", mapOf("reason" to "already_volunteering"))
        assertTrue(refusal.isAlreadyVolunteering)
    }

    @Test fun otherConflictsAreNot() {
        assertFalse(ApiError(409, "conflict", "No room.", mapOf("reason" to "no_room")).isAlreadyVolunteering)
        assertFalse(ApiError(403, "same_phone", "This phone is in it.").isAlreadyVolunteering)
    }
}
