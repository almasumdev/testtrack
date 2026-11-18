package com.eazyverse.testtrack.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconContourTest {
    @Test
    fun empty_and_transparent_images_have_no_edge() {
        assertNull(iconContour(0, 0) { _, _ -> error("No pixels") })
        assertNull(iconContour(10, 10) { _, _ -> 0 })
    }

    @Test
    fun opaque_image_reaches_all_four_corners() {
        val points = iconContour(8, 8) { _, _ -> 255 }!!.points
        assertTrue(points.containsAll(listOf(Offset.Zero, Offset(0f, 1f), Offset(1f, 0f), Offset(1f, 1f))))
        assertTrue(points.all { it.x in 0f..1f && it.y in 0f..1f })
    }

    @Test
    fun round_artwork_keeps_a_round_edge_instead_of_a_square_border() {
        val points =
            iconContour(100, 100) { x, y ->
                if ((x - 49.5f) * (x - 49.5f) + (y - 49.5f) * (y - 49.5f) <= 2500f) 255 else 0
            }!!.points
        assertFalse(points.contains(Offset.Zero))
        assertTrue(points.filter { it.y == 0f }.all { it.x in 0.4f..0.6f })
        assertTrue(points.contains(Offset(0f, 0.5f)))
        assertTrue(points.contains(Offset(1f, 0.5f)))
        assertTrue(IconContour(points).isRoundIcon())
    }

    @Test
    fun an_irregular_transparent_logo_does_not_get_traced_as_a_circle() {
        val shape = iconContour(32, 32) { x, y -> if (x in 8..23 && y in 4..27) 255 else 0 }!!
        assertFalse(shape.isRoundIcon())
        assertFalse(iconContour(32, 32) { _, _ -> 255 }!!.isRoundIcon())
    }

    @Test
    fun transparent_margins_and_faint_shadows_are_excluded() {
        val points =
            iconContour(10, 10) { x, y ->
                if (x in 2..5 && y in 3..7) 255 else 64
            }!!.points
        assertEquals(0.2f, points.minOf { it.x }, 0.001f)
        assertEquals(0.6f, points.maxOf { it.x }, 0.001f)
        assertEquals(0.3f, points.minOf { it.y }, 0.001f)
        assertEquals(0.8f, points.maxOf { it.y }, 0.001f)
    }
}
