package com.eazyverse.testtrack.domain

/*
 * Where the testing control sits on screen: stuck to one edge, somewhere along it.
 *
 * Pure arithmetic on pixel sizes, so it is tested without a phone. The window that draws the
 * control asks two things of it: which edge a drop lands on, and where the control's corner goes
 * for a given edge, spot along it and size. The size changes (flat or upright, open or closed), so
 * the edge and the spot are what is kept, and the corner is worked out again every time.
 */

/** The edge the control is stuck to. On the top or bottom it lies flat; on a side it stands. */
enum class DockEdge {
    TOP,
    BOTTOM,
    LEFT,
    RIGHT,
    ;

    val upright: Boolean get() = this == LEFT || this == RIGHT
}

/** The usable screen: its size, and the bars at the top and bottom the control stays clear of. */
data class DockScreen(
    val width: Float,
    val height: Float,
    val top: Float,
    val bottom: Float,
    val margin: Float,
)

/** An edge and how far along it, from 0 (top or left) to 1 (bottom or right). */
data class DockSpot(
    val edge: DockEdge,
    val along: Float,
)

/** The edge nearest a dropped control's centre, and its place along that edge. */
fun stickTo(
    centreX: Float,
    centreY: Float,
    screen: DockScreen,
): DockSpot {
    val distances =
        mapOf(
            DockEdge.LEFT to centreX,
            DockEdge.RIGHT to screen.width - centreX,
            DockEdge.TOP to centreY - screen.top,
            DockEdge.BOTTOM to screen.height - screen.bottom - centreY,
        )
    val edge = distances.minBy { it.value }.key
    val along =
        if (edge.upright) {
            (centreY - screen.top) / (screen.height - screen.top - screen.bottom)
        } else {
            centreX / screen.width
        }
    return DockSpot(edge, along.coerceIn(0f, 1f))
}

/** The control's top-left corner for [spot], given its [width] and [height], kept on screen. */
fun dockCorner(
    spot: DockSpot,
    width: Float,
    height: Float,
    screen: DockScreen,
): Pair<Float, Float> {
    val m = screen.margin
    val freeX = (screen.width - 2 * m - width).coerceAtLeast(0f)
    val freeY = (screen.height - screen.top - screen.bottom - 2 * m - height).coerceAtLeast(0f)
    val x =
        when (spot.edge) {
            DockEdge.LEFT -> m
            DockEdge.RIGHT -> screen.width - m - width
            else -> m + spot.along * freeX
        }
    val y =
        when (spot.edge) {
            DockEdge.TOP -> screen.top + m
            DockEdge.BOTTOM -> screen.height - screen.bottom - m - height
            else -> screen.top + m + spot.along * freeY
        }
    return x to y
}
