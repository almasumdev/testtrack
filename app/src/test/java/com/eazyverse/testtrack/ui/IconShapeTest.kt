package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which icons get our rounded corner: only the ones that fill their whole square. */
class IconShapeTest {
    @Test
    fun a_fully_opaque_icon_is_a_full_square() {
        assertTrue(isFullSquare(100, 100) { _, _ -> 255 })
    }

    @Test
    fun a_round_icon_with_clear_corners_is_not() {
        val cx = 49.5
        val alpha = { x: Int, y: Int -> if ((x - cx) * (x - cx) + (y - cx) * (y - cx) <= 50 * 50) 255 else 0 }
        assertFalse(isFullSquare(100, 100, alpha))
    }

    @Test
    fun one_clear_corner_is_enough_to_call_it_shaped() {
        assertFalse(isFullSquare(100, 100) { x, y -> if (x < 10 && y < 10) 0 else 255 })
    }

    @Test
    fun the_corners_are_read_just_inside_the_edge() {
        val seen = mutableSetOf<Pair<Int, Int>>()
        isFullSquare(200, 100) { x, y ->
            seen += x to y
            255
        }
        assertEquals(setOf(6 to 3, 193 to 3, 6 to 96, 193 to 96), seen)
    }

    @Test
    fun a_tiny_or_empty_image_does_not_crash() {
        assertTrue(isFullSquare(1, 1) { _, _ -> 255 })
        assertTrue(isFullSquare(0, 0) { _, _ -> 0 })
    }

    @Test
    fun a_decision_is_remembered_per_address() {
        assertNull(IconShapes.known("https://example.com/none.png"))
        IconShapes.remember("https://example.com/round.png", false)
        assertEquals(false, IconShapes.known("https://example.com/round.png"))
    }
}
