package com.eazyverse.testtrack.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A night's picture of an app is shrunk on the phone to what the server keeps: 720 wide at most. */
class ProofImagesTest {
    @Test
    fun `a phone screenshot is sent 720 wide, the same shape`() {
        assertEquals(720 to 1600, ProofImages.visitSize(1080, 2400))
        assertEquals(720 to 1560, ProofImages.visitSize(1440, 3120))
    }

    @Test
    fun `a picture already narrow enough is left as it is`() {
        assertEquals(720 to 1600, ProofImages.visitSize(720, 1600))
        assertEquals(480 to 800, ProofImages.visitSize(480, 800))
    }

    @Test
    fun `no picture goes wider than 720, whatever its shape`() {
        listOf(721 to 1, 4000 to 3000, 1600 to 720, 12_000 to 50, 1081 to 2400, 5 to 9000).forEach { (width, height) ->
            val (w, h) = ProofImages.visitSize(width, height)
            assertTrue("$width x $height gave $w wide", w <= ProofImages.VISIT_WIDTH)
            assertTrue("$width x $height gave $h high", h >= 1)
        }
    }
}
