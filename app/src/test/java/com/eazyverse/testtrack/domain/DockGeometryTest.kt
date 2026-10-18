package com.eazyverse.testtrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where the testing control sticks when it is let go, and where it sits for each edge. */
class DockGeometryTest {
    private val screen = DockScreen(width = 1080f, height = 2400f, top = 80f, bottom = 120f, margin = 20f)

    @Test
    fun a_drop_sticks_to_the_nearest_edge() {
        assertEquals(DockEdge.RIGHT, stickTo(1000f, 1200f, screen).edge)
        assertEquals(DockEdge.LEFT, stickTo(60f, 1200f, screen).edge)
        assertEquals(DockEdge.TOP, stickTo(540f, 150f, screen).edge)
        assertEquals(DockEdge.BOTTOM, stickTo(540f, 2230f, screen).edge)
    }

    @Test
    fun the_sides_stand_and_the_top_and_bottom_lie_flat() {
        assertTrue(DockEdge.LEFT.upright)
        assertTrue(DockEdge.RIGHT.upright)
        assertFalse(DockEdge.TOP.upright)
        assertFalse(DockEdge.BOTTOM.upright)
    }

    @Test
    fun where_along_the_edge_is_kept_and_clamped() {
        assertEquals(0.5f, stickTo(1050f, 1180f, screen).along, 0.01f)
        // Dropped past a corner: the nearest edge wins and the spot along it is held on screen.
        assertEquals(DockSpot(DockEdge.RIGHT, 0f), stickTo(1200f, 50f, screen))
        assertEquals(DockSpot(DockEdge.BOTTOM, 0f), stickTo(-50f, 2390f, screen))
    }

    @Test
    fun the_corner_keeps_the_control_on_its_edge_and_on_screen() {
        val (rx, _) = dockCorner(DockSpot(DockEdge.RIGHT, 0.5f), 120f, 300f, screen)
        assertEquals(1080f - 20f - 120f, rx, 0.01f)
        val (_, ty) = dockCorner(DockSpot(DockEdge.TOP, 0.5f), 600f, 100f, screen)
        assertEquals(80f + 20f, ty, 0.01f)
        val (_, by) = dockCorner(DockSpot(DockEdge.BOTTOM, 1f), 600f, 100f, screen)
        assertEquals(2400f - 120f - 20f - 100f, by, 0.01f)
        val (bx, _) = dockCorner(DockSpot(DockEdge.BOTTOM, 1f), 600f, 100f, screen)
        assertEquals(1080f - 20f - 600f, bx, 0.01f)
    }
}
