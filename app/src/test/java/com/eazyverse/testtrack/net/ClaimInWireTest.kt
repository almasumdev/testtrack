package com.eazyverse.testtrack.net

import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimInWireTest {
    @Test fun knowsFeedbackIsAlwaysOnTheWire() {
        val body = Api.json.encodeToString(ClaimIn("fp", knowsFeedback = true))
        assertTrue(body, body.contains("\"knows_feedback\":true"))
        assertTrue(body, body.contains("\"device_fp\":\"fp\""))
    }
}
