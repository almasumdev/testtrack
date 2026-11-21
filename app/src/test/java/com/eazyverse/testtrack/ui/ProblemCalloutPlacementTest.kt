package com.eazyverse.testtrack.ui

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Where a problem callout lands: 16dp from both sides, under the mark or above it near the dock. */
class ProblemCalloutPlacementTest {
    private val density = 2.75f
    private val dock = (96 * density).toInt()

    private fun px(dp: Int) = (dp * density).toInt()

    // The mark's 32dp target sits 6dp into the tile's 12dp padding, 16dp in from the screen.
    private fun markAt(
        widthDp: Int,
        topDp: Int,
    ): IntRect {
        val right = px(widthDp - 16 - 12 + 6)
        return IntRect(right - px(32), px(topDp), right, px(topDp + 32))
    }

    private fun place(
        widthDp: Int,
        topDp: Int,
        heightDp: Int = 100,
        windowHeightDp: Int = 800,
    ): Pair<CalloutPlacement, Int> {
        val window = IntSize(px(widthDp), px(windowHeightDp))
        val popup = IntSize(px(minOf(260, widthDp - 32)), px(heightDp))
        return placeCallout(markAt(widthDp, topDp), window, popup, density, dock) to popup.width
    }

    @Test
    fun stays_sixteen_dp_inside_both_edges_on_narrow_and_wide_phones() {
        for (widthDp in listOf(300, 360, 392)) {
            val (placed, width) = place(widthDp, topDp = 200)
            assertTrue("left at $widthDp", placed.x >= px(16))
            assertTrue("right at $widthDp", placed.x + width <= px(widthDp) - px(16) + 1)
        }
    }

    @Test
    fun opens_below_the_mark_with_the_caret_up() {
        val mark = markAt(360, 200)
        val (placed, _) = place(360, topDp = 200)
        assertTrue(placed.caretUp)
        assertEquals((mark.bottom - px(6) + px(4)).toFloat(), placed.y.toFloat(), 1f)
    }

    @Test
    fun flips_above_the_mark_when_it_would_run_into_the_dock() {
        val mark = markAt(360, 560)
        val (placed, _) = place(360, topDp = 560, heightDp = 120)
        assertFalse(placed.caretUp)
        assertEquals((mark.top + px(6) - px(4) - px(120)).toFloat(), placed.y.toFloat(), 1f)
    }

    @Test
    fun the_caret_points_at_the_centre_of_the_mark() {
        for (widthDp in listOf(360, 392)) {
            val mark = markAt(widthDp, 200)
            val (placed, width) = place(widthDp, topDp = 200)
            val drawn = clampCaret(placed.caretX, width.toFloat(), density)
            val markCentre = (mark.left + mark.right) / 2f
            assertEquals(markCentre, placed.x + drawn, density)
        }
    }

    @Test
    fun the_caret_keeps_off_the_corners() {
        assertEquals(px(22).toFloat(), clampCaret(0f, px(260).toFloat(), density), 0.5f)
        assertEquals(px(260 - 22).toFloat(), clampCaret(px(260).toFloat(), px(260).toFloat(), density), 0.5f)
    }
}
