package com.eazyverse.testtrack.data

import com.eazyverse.testtrack.net.ApiError
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a taken picture id is worth a new id. Every other 409 is final and must reach the queue as it is. */
class ServerUploadTest {
    @Test
    fun `a taken id is retried under a new one`() {
        assertTrue(ServerUpload.taken(ApiError(409, "upload_taken", "taken")))
    }

    @Test
    fun `any other 409 is not an id problem`() {
        assertFalse(ServerUpload.taken(ApiError(409, "conflict", "crew gone")))
        assertFalse(ServerUpload.taken(ApiError(409, "chat_closed", "closed")))
    }

    @Test
    fun `the code alone is not enough without the status`() {
        assertFalse(ServerUpload.taken(ApiError(400, "upload_taken", "odd")))
    }
}
