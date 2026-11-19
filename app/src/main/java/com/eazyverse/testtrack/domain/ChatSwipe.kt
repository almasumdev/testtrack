package com.eazyverse.testtrack.domain

import kotlin.math.abs
import kotlin.math.exp

/**
 * The swipe-to-reply gesture's arithmetic, Messenger's way: a message is dragged toward the middle
 * of the screen, follows the finger with growing resistance, and replies when let go past
 * [THRESHOLD]. Distances are in dp, and "toward" is the way the message moves: right for someone
 * else's (on the left), left for your own (on the right).
 */
object ChatSwipe {
    /** How far the finger has to go for the reply to count. */
    const val THRESHOLD = 64f

    /** The message never travels more than this, however far the finger goes. */
    const val TRAVEL = 100f

    /** Sideways has to beat up and down by this much, so reading the thread never starts a swipe. */
    const val DOMINANCE = 2f

    enum class Start {
        /** Not enough movement yet to tell. */
        WAIT,

        /** Clearly sideways and toward the middle: it is a swipe. */
        SWIPE,

        /** Up and down, or away from the middle: the gesture is not ours. */
        SKIP,
    }

    /** The finger's distance toward the middle, from its sideways move [dx]: positive when the right way. */
    fun toward(
        dx: Float,
        mine: Boolean,
    ): Float = if (mine) -dx else dx

    /** Whether a touch that has moved [dx] across and [dy] down, past [slop], is a swipe, not ours yet or never. */
    fun start(
        dx: Float,
        dy: Float,
        slop: Float,
        mine: Boolean,
    ): Start {
        val toward = toward(dx, mine)
        val up = abs(dy)
        return when {
            toward > slop && toward >= DOMINANCE * up -> Start.SWIPE
            up > slop && toward < DOMINANCE * up -> Start.SKIP
            abs(dx) > slop && toward <= 0f -> Start.SKIP
            else -> Start.WAIT
        }
    }

    /** How far the message has moved for a finger [drag] toward the middle: one to one at first, then more and more reluctant. */
    fun travel(drag: Float): Float {
        val d = drag.coerceAtLeast(0f)
        return TRAVEL * (1f - exp(-d / TRAVEL))
    }

    /** The reply mark's fade and scale, 0 to 1 as the finger reaches the threshold. */
    fun progress(drag: Float): Float = (drag / THRESHOLD).coerceIn(0f, 1f)

    /** Letting go here replies. */
    fun passed(drag: Float): Boolean = drag >= THRESHOLD
}
