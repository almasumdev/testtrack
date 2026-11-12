package com.eazyverse.testtrack.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

    @Test
    fun the_whole_fit_is_remembered_per_address() {
        val fit = IconFit(false, CropBox(0.2f, 0.2f, 0.8f, 0.8f))
        IconShapes.remember("https://example.com/margin.png", fit)
        assertEquals(fit, IconShapes.fit("https://example.com/margin.png"))
        assertEquals(false, IconShapes.known("https://example.com/margin.png"))
    }

    private fun inside(
        x0: Int,
        x1: Int,
        y0: Int,
        y1: Int,
    ) = { x: Int, y: Int -> if (x in x0..x1 && y in y0..y1) 255 else 0 }

    private fun assertBox(
        expected: CropBox,
        actual: CropBox?,
    ) {
        assertNotNull(actual)
        assertEquals(expected.left, actual!!.left, 0.001f)
        assertEquals(expected.top, actual.top, 0.001f)
        assertEquals(expected.right, actual.right, 0.001f)
        assertEquals(expected.bottom, actual.bottom, 0.001f)
    }

    @Test
    fun a_full_bleed_icon_is_not_cropped() {
        assertNull(cropBox(100, 100) { _, _ -> 255 })
    }

    @Test
    fun centred_content_is_cropped_to_itself() {
        assertBox(CropBox(0.2f, 0.2f, 0.8f, 0.8f), cropBox(100, 100, inside(20, 79, 20, 79)))
    }

    @Test
    fun off_centre_content_is_cropped_where_it_sits() {
        assertBox(CropBox(0.1f, 0.5f, 0.5f, 0.9f), cropBox(100, 100, inside(10, 49, 50, 89)))
    }

    @Test
    fun a_wide_content_box_is_made_square_around_its_centre() {
        // 60 wide, 20 tall, centred: the box takes the larger side and keeps the content's centre.
        assertBox(CropBox(0.2f, 0.2f, 0.8f, 0.8f), cropBox(100, 100, inside(20, 79, 40, 59)))
    }

    @Test
    fun the_box_stays_inside_the_image() {
        // Content hugs the left edge and is tall; the square must not slide out of the bitmap.
        val box = cropBox(100, 100, inside(0, 19, 10, 89))
        assertNotNull(box)
        assertTrue(box!!.left >= 0f && box.right <= 1f && box.top >= 0f && box.bottom <= 1f)
    }

    @Test
    fun a_fully_clear_image_is_not_cropped() {
        assertNull(cropBox(100, 100) { _, _ -> 0 })
    }

    @Test
    fun a_margin_under_the_threshold_is_not_cropped() {
        assertNull(cropBox(100, 100, inside(3, 96, 3, 96)))
    }

    @Test
    fun faint_fringe_does_not_count_as_artwork() {
        val alpha = { x: Int, y: Int ->
            when {
                x in 20..79 && y in 20..79 -> 255
                x < 5 -> 20
                else -> 0
            }
        }
        assertBox(CropBox(0.2f, 0.2f, 0.8f, 0.8f), cropBox(100, 100, alpha))
    }

    @Test
    fun an_empty_image_does_not_crash() {
        assertNull(cropBox(0, 0) { _, _ -> 255 })
    }
}
