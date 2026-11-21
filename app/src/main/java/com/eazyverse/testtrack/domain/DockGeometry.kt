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

/**
 * The window's top-left corner for [spot], given its [width] and [height], kept on screen.
 *
 * [room] is transparent space all round inside the window, kept only so the control's shadow is
 * not cut off by the window's edge. The edge gap and the snapping are the control's own, not the
 * window's: the control sits exactly where it would in a window with no [room], and the window
 * reaches [room] further out on every side (it may lie partly off screen on the edge side).
 */
fun dockCorner(
    spot: DockSpot,
    width: Float,
    height: Float,
    screen: DockScreen,
    room: Float = 0f,
): Pair<Float, Float> {
    val (x, y) = controlCorner(spot, width - 2 * room, height - 2 * room, screen)
    return (x - room) to (y - room)
}

/**
 * Where on a control of any size the dock holds it still, as a share of its width and of its height
 * (0 is left or top, 1 is right or bottom): the docked edge itself, and [DockSpot.along] on the
 * other axis. [dockCorner] puts a control of every size at exactly that point of its own box, so a
 * control that is growing can be drawn inside a window of its largest size at this point and sit
 * where [dockCorner] would put it at each size on the way.
 */
fun dockAnchor(spot: DockSpot): Pair<Float, Float> =
    when (spot.edge) {
        DockEdge.TOP -> spot.along to 0f
        DockEdge.BOTTOM -> spot.along to 1f
        DockEdge.LEFT -> 0f to spot.along
        DockEdge.RIGHT -> 1f to spot.along
    }

/** The control's own top-left corner, with no room around it. */
private fun controlCorner(
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
